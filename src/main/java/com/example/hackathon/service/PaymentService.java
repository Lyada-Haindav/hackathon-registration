package com.example.hackathon.service;

import com.example.hackathon.dto.PaymentOrderResponse;
import com.example.hackathon.dto.PaymentVerificationResponse;
import com.example.hackathon.dto.UpiConfigResponse;
import com.example.hackathon.dto.UpiPaymentConfirmRequest;
import com.example.hackathon.exception.BadRequestException;
import com.example.hackathon.model.HackathonEvent;
import com.example.hackathon.model.Payment;
import com.example.hackathon.model.PaymentRecordStatus;
import com.example.hackathon.model.PaymentStatus;
import com.example.hackathon.model.Team;
import com.example.hackathon.model.User;
import com.example.hackathon.repository.PaymentRepository;
import com.example.hackathon.repository.TeamRepository;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.Locale;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final String PHONEPE_PAY_PATH = "/pg/v1/pay";
    private static final String PHONEPE_STATUS_PATH = "/pg/v1/status";

    private final PaymentRepository paymentRepository;
    private final TeamService teamService;
    private final TeamRepository teamRepository;
    private final EventService eventService;
    private final UserService userService;
    private final EmailService emailService;
    private final HttpClient httpClient;

    @Value("${app.payment.mock-enabled:false}")
    private boolean paymentMockEnabled;

    @Value("${app.payment.fee-split-members:4}")
    private int feeSplitMembers;

    @Value("${app.payment.upi-id:}")
    private String upiId;

    @Value("${app.payment.upi-payee-name:Payment}")
    private String upiPayeeName;

    @Value("${app.phonepe.enabled:true}")
    private boolean phonePeEnabled;

    @Value("${app.phonepe.merchant-id:}")
    private String phonePeMerchantId;

    @Value("${app.phonepe.salt-key:}")
    private String phonePeSaltKey;

    @Value("${app.phonepe.salt-index:1}")
    private int phonePeSaltIndex;

    @Value("${app.phonepe.base-url:https://api-preprod.phonepe.com/apis/pg-sandbox}")
    private String phonePeBaseUrl;

    @Value("${app.web.base-url:http://localhost:8080}")
    private String appBaseUrl;

    public PaymentService(PaymentRepository paymentRepository,
                          TeamService teamService,
                          TeamRepository teamRepository,
                          EventService eventService,
                          UserService userService,
                          EmailService emailService) {
        this.paymentRepository = paymentRepository;
        this.teamService = teamService;
        this.teamRepository = teamRepository;
        this.eventService = eventService;
        this.userService = userService;
        this.emailService = emailService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public UpiConfigResponse getUpiPaymentConfig() {
        validateUpiConfig();
        return new UpiConfigResponse(upiId.trim(), sanitizeUpiPayeeName(upiPayeeName));
    }

    public PaymentVerificationResponse confirmUpiPayment(String userEmail, String teamId, UpiPaymentConfirmRequest request) {
        Team team = teamService.getTeamEntityForUser(teamId, userEmail);
        HackathonEvent event = eventService.getEventEntity(team.getEventId());
        validateUpiConfig();

        if (team.getPaymentStatus() == PaymentStatus.SUCCESS) {
            return new PaymentVerificationResponse(team.getId(), team.getPaymentStatus(), "Payment already confirmed");
        }

        BigDecimal fee = calculatePayableFee(event, team.getTeamSize());
        String transactionId = normalizeMerchantTransactionId(request == null ? null : request.transactionId());
        if (transactionId == null) {
            transactionId = generateSyntheticUpiTxnId();
        }

        Payment payment = latestPaymentForTeam(team.getId());
        if (payment.getId() == null) {
            payment.setTeamId(team.getId());
            payment.setEventId(event.getId());
        }
        payment.setAmount(fee);
        payment.setCurrency("INR");
        payment.setRazorpayOrderId("UPI_INTENT_" + team.getId());
        payment.setRazorpayPaymentId(transactionId);
        payment.setRazorpaySignature("UPI_DEEP_LINK_CONFIRM");
        payment.setStatus(PaymentRecordStatus.CAPTURED);
        payment.setVerifiedAt(Instant.now());
        Payment savedPayment = paymentRepository.save(payment);

        team.setPaymentRecordId(savedPayment.getId());
        team.setRazorpayOrderId(savedPayment.getRazorpayOrderId());
        team.setRazorpayPaymentId(transactionId);
        team.setPaymentStatus(PaymentStatus.SUCCESS);
        teamRepository.save(team);

        boolean emailSent = sendPaymentConfirmationSafely(userEmail, team, event, savedPayment);
        String message = emailSent
                ? "UPI payment confirmed successfully. Confirmation email sent."
                : "UPI payment confirmed successfully. Confirmation email could not be sent.";
        return new PaymentVerificationResponse(team.getId(), team.getPaymentStatus(), message);
    }

    public PaymentOrderResponse createOrder(String userEmail, String teamId) {
        Team team = teamService.getTeamEntityForUser(teamId, userEmail);
        HackathonEvent event = eventService.getEventEntity(team.getEventId());

        if (team.getPaymentStatus() == PaymentStatus.SUCCESS) {
            throw new BadRequestException("Payment already completed for this team");
        }

        BigDecimal fee = calculatePayableFee(event, team.getTeamSize());
        if (fee.compareTo(BigDecimal.ZERO) <= 0) {
            markFreeRegistration(team, event);
            return new PaymentOrderResponse(team.getId(), "FREE_REGISTRATION", "FREE_REGISTRATION", BigDecimal.ZERO, "INR", null);
        }

        if (paymentMockEnabled) {
            throw new BadRequestException("Mock payments are enabled. Set PAYMENT_MOCK_ENABLED=false for live PhonePe payment.");
        }

        validatePhonePeConfig();

        String merchantTransactionId = generateMerchantTransactionId(team.getId());
        String redirectUrl = buildRedirectUrl(team.getId(), merchantTransactionId);
        String checkoutUrl = createPhonePePayPageUrl(merchantTransactionId, team.getUserId(), fee, redirectUrl);

        Payment payment = latestPaymentForTeam(team.getId());
        if (payment.getId() == null) {
            payment.setTeamId(team.getId());
            payment.setEventId(event.getId());
        }
        payment.setAmount(fee);
        payment.setCurrency("INR");
        payment.setRazorpayOrderId(merchantTransactionId);
        payment.setRazorpayPaymentId(null);
        payment.setRazorpaySignature("PHONEPE_PAY_PAGE");
        payment.setStatus(PaymentRecordStatus.ORDER_CREATED);
        payment.setVerifiedAt(null);
        Payment savedPayment = paymentRepository.save(payment);

        team.setPaymentRecordId(savedPayment.getId());
        team.setRazorpayOrderId(merchantTransactionId);
        team.setRazorpayPaymentId(null);
        team.setPaymentStatus(PaymentStatus.PENDING);
        teamRepository.save(team);

        return new PaymentOrderResponse(team.getId(), merchantTransactionId, "PHONEPE", fee, "INR", checkoutUrl);
    }

    public PaymentVerificationResponse verifyPhonePePayment(String userEmail, String teamId, String merchantTransactionId) {
        Team team = teamService.getTeamEntityForUser(teamId, userEmail);
        HackathonEvent event = eventService.getEventEntity(team.getEventId());

        if (team.getPaymentStatus() == PaymentStatus.SUCCESS) {
            return new PaymentVerificationResponse(team.getId(), team.getPaymentStatus(), "Payment already verified");
        }

        validatePhonePeConfig();

        String expectedTransactionId = normalizeMerchantTransactionId(merchantTransactionId);
        if (expectedTransactionId == null) {
            expectedTransactionId = normalizeMerchantTransactionId(team.getRazorpayOrderId());
        }
        if (expectedTransactionId == null) {
            throw new BadRequestException("No PhonePe transaction found for this team. Start payment again.");
        }

        if (team.getRazorpayOrderId() != null && !team.getRazorpayOrderId().isBlank()
                && !team.getRazorpayOrderId().equals(expectedTransactionId)) {
            throw new BadRequestException("Transaction does not match this team registration.");
        }

        BigDecimal expectedAmount = calculatePayableFee(event, team.getTeamSize());
        PhonePeStatusSnapshot snapshot = fetchPhonePeStatus(expectedTransactionId, expectedAmount);

        Payment payment = paymentRepository.findByRazorpayOrderId(expectedTransactionId)
                .orElseGet(() -> latestPaymentForTeam(team.getId()));
        if (payment.getId() == null) {
            payment.setTeamId(team.getId());
            payment.setEventId(event.getId());
        }
        payment.setAmount(expectedAmount);
        payment.setCurrency("INR");
        payment.setRazorpayOrderId(expectedTransactionId);
        payment.setRazorpayPaymentId(snapshot.gatewayTransactionId());
        payment.setRazorpaySignature(snapshot.responseCode());

        PhonePePaymentState state = normalizePhonePeState(snapshot);
        if (state == PhonePePaymentState.SUCCESS) {
            payment.setStatus(PaymentRecordStatus.CAPTURED);
            payment.setVerifiedAt(Instant.now());
            Payment savedPayment = paymentRepository.save(payment);

            team.setPaymentRecordId(savedPayment.getId());
            team.setRazorpayOrderId(expectedTransactionId);
            team.setRazorpayPaymentId(snapshot.gatewayTransactionId());
            team.setPaymentStatus(PaymentStatus.SUCCESS);
            teamRepository.save(team);

            boolean emailSent = sendPaymentConfirmationSafely(userEmail, team, event, savedPayment);
            String message = emailSent
                    ? "Payment verified successfully through PhonePe. Confirmation email sent."
                    : "Payment verified successfully through PhonePe. Confirmation email could not be sent.";
            return new PaymentVerificationResponse(team.getId(), team.getPaymentStatus(), message);
        }

        if (state == PhonePePaymentState.FAILED) {
            payment.setStatus(PaymentRecordStatus.FAILED);
            payment.setVerifiedAt(Instant.now());
            paymentRepository.save(payment);

            team.setPaymentRecordId(payment.getId());
            team.setRazorpayOrderId(expectedTransactionId);
            team.setRazorpayPaymentId(snapshot.gatewayTransactionId());
            team.setPaymentStatus(PaymentStatus.FAILED);
            teamRepository.save(team);

            String failureMessage = snapshot.message() == null || snapshot.message().isBlank()
                    ? "PhonePe payment failed."
                    : snapshot.message();
            return new PaymentVerificationResponse(team.getId(), team.getPaymentStatus(), failureMessage);
        }

        payment.setStatus(PaymentRecordStatus.ORDER_CREATED);
        payment.setVerifiedAt(null);
        Payment savedPayment = paymentRepository.save(payment);

        team.setPaymentRecordId(savedPayment.getId());
        team.setRazorpayOrderId(expectedTransactionId);
        team.setRazorpayPaymentId(snapshot.gatewayTransactionId());
        team.setPaymentStatus(PaymentStatus.PENDING);
        teamRepository.save(team);

        String pendingMessage = snapshot.message() == null || snapshot.message().isBlank()
                ? "Payment is pending in PhonePe. Please wait a few seconds and check again."
                : snapshot.message();
        return new PaymentVerificationResponse(team.getId(), team.getPaymentStatus(), pendingMessage);
    }

    private Payment markFreeRegistration(Team team, HackathonEvent event) {
        Payment payment = new Payment();
        payment.setTeamId(team.getId());
        payment.setEventId(event.getId());
        payment.setAmount(BigDecimal.ZERO);
        payment.setCurrency("INR");
        payment.setRazorpayOrderId("FREE_REGISTRATION");
        payment.setStatus(PaymentRecordStatus.CAPTURED);
        payment.setVerifiedAt(Instant.now());
        Payment saved = paymentRepository.save(payment);

        team.setPaymentStatus(PaymentStatus.SUCCESS);
        team.setPaymentRecordId(saved.getId());
        team.setRazorpayOrderId("FREE_REGISTRATION");
        team.setRazorpayPaymentId("FREE_REGISTRATION");
        teamRepository.save(team);
        return saved;
    }

    private BigDecimal calculatePayableFee(HackathonEvent event, int teamSize) {
        BigDecimal totalEventFee = event.getRegistrationFee();
        if (totalEventFee == null || totalEventFee.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        int splitMembers = feeSplitMembers <= 0 ? 1 : feeSplitMembers;
        int payableMembers = Math.max(teamSize, 1);
        BigDecimal perMemberFee = totalEventFee
                .divide(BigDecimal.valueOf(splitMembers), 2, RoundingMode.HALF_UP);
        return perMemberFee.multiply(BigDecimal.valueOf(payableMembers)).setScale(2, RoundingMode.HALF_UP);
    }

    private Payment latestPaymentForTeam(String teamId) {
        return paymentRepository.findByTeamId(teamId).stream()
                .max(Comparator.comparing(Payment::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseGet(Payment::new);
    }

    private String createPhonePePayPageUrl(String merchantTransactionId,
                                           String merchantUserId,
                                           BigDecimal amount,
                                           String redirectUrl) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("merchantId", phonePeMerchantId.trim());
            payload.put("merchantTransactionId", merchantTransactionId);
            payload.put("merchantUserId", sanitizeMerchantUserId(merchantUserId));
            payload.put("amount", toPaise(amount));
            payload.put("redirectUrl", redirectUrl);
            payload.put("redirectMode", "REDIRECT");
            payload.put("callbackUrl", redirectUrl);
            payload.put("paymentInstrument", new JSONObject().put("type", "PAY_PAGE"));

            String base64Payload = Base64.getEncoder()
                    .encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
            String xVerify = sha256Hex(base64Payload + PHONEPE_PAY_PATH + phonePeSaltKey.trim())
                    + "###" + phonePeSaltIndex;

            JSONObject body = new JSONObject();
            body.put("request", base64Payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl() + PHONEPE_PAY_PATH))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-VERIFY", xVerify)
                    .header("X-MERCHANT-ID", phonePeMerchantId.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BadRequestException("PhonePe order failed with HTTP " + response.statusCode() + ".");
            }

            JSONObject responseJson = new JSONObject(response.body());
            if (!responseJson.optBoolean("success", false)) {
                String message = responseJson.optString("message", "PhonePe rejected payment order.");
                throw new BadRequestException(message);
            }

            JSONObject data = responseJson.optJSONObject("data");
            String redirectPage = extractRedirectUrl(data);
            if (redirectPage == null || redirectPage.isBlank()) {
                throw new BadRequestException("PhonePe did not return a checkout URL.");
            }
            return redirectPage;
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("Unable to initiate PhonePe payment: " + sanitizeMessage(ex.getMessage()));
        }
    }

    private PhonePeStatusSnapshot fetchPhonePeStatus(String merchantTransactionId, BigDecimal expectedAmount) {
        try {
            String merchantId = phonePeMerchantId.trim();
            String statusPath = PHONEPE_STATUS_PATH + "/" + merchantId + "/" + merchantTransactionId;
            String xVerify = sha256Hex(statusPath + phonePeSaltKey.trim()) + "###" + phonePeSaltIndex;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl() + statusPath))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .header("X-VERIFY", xVerify)
                    .header("X-MERCHANT-ID", merchantId)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BadRequestException("PhonePe status API failed with HTTP " + response.statusCode() + ".");
            }

            JSONObject responseJson = new JSONObject(response.body());
            JSONObject data = responseJson.optJSONObject("data");
            String state = data == null ? "" : data.optString("state", "");
            String responseCode = data == null
                    ? responseJson.optString("code", "")
                    : data.optString("responseCode", responseJson.optString("code", ""));
            String gatewayTransactionId = data == null ? "" : data.optString("transactionId", "");
            String message = responseJson.optString("message", "Payment status fetched.");

            long amountPaise = -1;
            if (data != null && data.has("amount")) {
                amountPaise = data.optLong("amount", -1);
            }
            long expectedPaise = toPaise(expectedAmount);
            if (amountPaise > 0 && expectedPaise > 0 && amountPaise != expectedPaise) {
                throw new BadRequestException("Payment amount mismatch from PhonePe. Please contact organizer.");
            }

            if (state == null || state.isBlank()) {
                String code = responseJson.optString("code", "");
                state = code == null || code.isBlank() ? "PENDING" : code;
            }

            return new PhonePeStatusSnapshot(
                    state.trim().toUpperCase(Locale.ROOT),
                    gatewayTransactionId == null ? null : gatewayTransactionId.trim(),
                    responseCode == null ? null : responseCode.trim(),
                    message
            );
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("Unable to verify PhonePe payment: " + sanitizeMessage(ex.getMessage()));
        }
    }

    private String extractRedirectUrl(JSONObject data) {
        if (data == null) {
            return null;
        }

        JSONObject instrumentResponse = data.optJSONObject("instrumentResponse");
        if (instrumentResponse != null) {
            JSONObject redirectInfo = instrumentResponse.optJSONObject("redirectInfo");
            if (redirectInfo != null) {
                String url = redirectInfo.optString("url", "");
                if (url != null && !url.isBlank()) {
                    return url.trim();
                }
            }
        }

        String direct = data.optString("redirectUrl", "");
        return direct == null || direct.isBlank() ? null : direct.trim();
    }

    private String buildRedirectUrl(String teamId, String merchantTransactionId) {
        String base = appBaseUrl == null || appBaseUrl.isBlank()
                ? "http://localhost:8080"
                : appBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        return base + "/user?paymentTeamId=" + urlEncode(teamId)
                + "&phonepeTxnId=" + urlEncode(merchantTransactionId);
    }

    private String generateMerchantTransactionId(String teamId) {
        String safeTeamId = teamId == null ? "TEAM" : teamId.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (safeTeamId.length() > 10) {
            safeTeamId = safeTeamId.substring(safeTeamId.length() - 10);
        }
        String timestamp = Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT);
        String entropy = Long.toString(System.nanoTime(), 36).toUpperCase(Locale.ROOT);
        String transactionId = "KLH" + safeTeamId + timestamp + entropy;
        return transactionId.length() <= 34 ? transactionId : transactionId.substring(0, 34);
    }

    private String sanitizeMerchantUserId(String userId) {
        String raw = userId == null ? "" : userId.replaceAll("[^A-Za-z0-9]", "");
        if (raw.isBlank()) {
            return "KLHUSER";
        }
        return raw.length() <= 35 ? raw : raw.substring(0, 35);
    }

    private String normalizeMerchantTransactionId(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        if (trimmed.length() > 100) {
            return trimmed.substring(0, 100);
        }
        return trimmed;
    }

    private long toPaise(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0L;
        }
        return amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private void validateUpiConfig() {
        if (upiId == null || upiId.isBlank() || !upiId.contains("@")) {
            throw new BadRequestException("UPI is not configured. Set UPI_ID in format name@bank.");
        }
    }

    private String sanitizeUpiPayeeName(String value) {
        String name = value == null ? "" : value.trim();
        return name.isEmpty() ? "Payment" : name;
    }

    private String generateSyntheticUpiTxnId() {
        return "UPI_TXN_" + Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT);
    }

    private void validatePhonePeConfig() {
        if (!phonePeEnabled) {
            throw new BadRequestException("PhonePe payments are disabled. Set PHONEPE_ENABLED=true.");
        }
        if (isInvalidConfig(phonePeMerchantId) || isInvalidConfig(phonePeSaltKey) || phonePeSaltIndex <= 0) {
            throw new BadRequestException(
                    "PhonePe is not configured. Set PHONEPE_MERCHANT_ID, PHONEPE_SALT_KEY, and PHONEPE_SALT_INDEX."
            );
        }
        if (phonePeBaseUrl == null || phonePeBaseUrl.isBlank()) {
            throw new BadRequestException("PHONEPE_BASE_URL is missing.");
        }
    }

    private String normalizeBaseUrl() {
        String base = phonePeBaseUrl == null ? "" : phonePeBaseUrl.trim();
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }

    private PhonePePaymentState normalizePhonePeState(PhonePeStatusSnapshot snapshot) {
        String state = snapshot.state() == null ? "" : snapshot.state().toUpperCase(Locale.ROOT);
        String responseCode = snapshot.responseCode() == null ? "" : snapshot.responseCode().toUpperCase(Locale.ROOT);
        String mix = state + " " + responseCode;

        if (mix.contains("COMPLETED") || mix.contains("SUCCESS") || mix.contains("PAYMENT_SUCCESS")) {
            return PhonePePaymentState.SUCCESS;
        }
        if (mix.contains("FAILED") || mix.contains("FAILURE") || mix.contains("ERROR")
                || mix.contains("CANCELLED") || mix.contains("EXPIRED")) {
            return PhonePePaymentState.FAILED;
        }
        return PhonePePaymentState.PENDING;
    }

    private boolean isInvalidConfig(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("replace") || normalized.contains("your_") || normalized.contains("example");
    }

    private String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown error";
        }
        String oneLine = message.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() > 220 ? oneLine.substring(0, 220) + "..." : oneLine;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute SHA-256 hash", ex);
        }
    }

    private boolean sendPaymentConfirmationSafely(String userEmail, Team team, HackathonEvent event, Payment payment) {
        try {
            User user = userService.findByEmail(userEmail);
            emailService.sendPaymentConfirmationEmail(user, team, event, payment);
            return true;
        } catch (Exception ex) {
            log.warn("Payment confirmed for team '{}' but confirmation email failed: {}", team.getId(), ex.getMessage());
            return false;
        }
    }

    private enum PhonePePaymentState {
        SUCCESS,
        PENDING,
        FAILED
    }

    private record PhonePeStatusSnapshot(
            String state,
            String gatewayTransactionId,
            String responseCode,
            String message
    ) {
    }
}
