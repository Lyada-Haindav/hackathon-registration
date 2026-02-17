package com.example.hackathon.service;

import com.example.hackathon.dto.PaymentOrderResponse;
import com.example.hackathon.dto.PaymentVerificationRequest;
import com.example.hackathon.dto.PaymentVerificationResponse;
import com.example.hackathon.exception.BadRequestException;
import com.example.hackathon.exception.ResourceNotFoundException;
import com.example.hackathon.model.HackathonEvent;
import com.example.hackathon.model.Payment;
import com.example.hackathon.model.PaymentRecordStatus;
import com.example.hackathon.model.PaymentStatus;
import com.example.hackathon.model.Team;
import com.example.hackathon.repository.PaymentRepository;
import com.example.hackathon.repository.TeamRepository;
import com.example.hackathon.util.SignatureUtil;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final TeamService teamService;
    private final TeamRepository teamRepository;
    private final EventService eventService;

    @Value("${app.razorpay.key-id}")
    private String razorpayKeyId;

    @Value("${app.razorpay.key-secret}")
    private String razorpayKeySecret;

    @Value("${app.payment.mock-enabled:false}")
    private boolean paymentMockEnabled;

    @Value("${app.payment.fee-split-members:4}")
    private int feeSplitMembers;

    public PaymentService(PaymentRepository paymentRepository,
                          TeamService teamService,
                          TeamRepository teamRepository,
                          EventService eventService) {
        this.paymentRepository = paymentRepository;
        this.teamService = teamService;
        this.teamRepository = teamRepository;
        this.eventService = eventService;
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
            return new PaymentOrderResponse(team.getId(), "FREE_REGISTRATION", razorpayKeyId, BigDecimal.ZERO, "INR");
        }

        if (paymentMockEnabled) {
            return createMockOrder(team, event, fee);
        }

        validateRazorpayConfig();

        String orderId;
        try {
            RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            JSONObject options = new JSONObject();
            options.put("amount", fee.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact());
            options.put("currency", "INR");
            options.put("receipt", "team_" + team.getId() + "_" + System.currentTimeMillis());
            Order order = client.orders.create(options);
            orderId = order.get("id");
        } catch (RazorpayException ex) {
            throw new BadRequestException("Unable to create Razorpay order: " + ex.getMessage());
        }

        Payment payment = new Payment();
        payment.setTeamId(team.getId());
        payment.setEventId(event.getId());
        payment.setAmount(fee);
        payment.setCurrency("INR");
        payment.setRazorpayOrderId(orderId);
        payment.setStatus(PaymentRecordStatus.ORDER_CREATED);
        Payment savedPayment = paymentRepository.save(payment);

        team.setPaymentRecordId(savedPayment.getId());
        team.setRazorpayOrderId(orderId);
        team.setPaymentStatus(PaymentStatus.PENDING);
        teamRepository.save(team);

        return new PaymentOrderResponse(team.getId(), orderId, razorpayKeyId, fee, "INR");
    }

    public PaymentVerificationResponse verifyPayment(String userEmail, String teamId, PaymentVerificationRequest request) {
        Team team = teamService.getTeamEntityForUser(teamId, userEmail);

        if (team.getRazorpayOrderId() == null || !team.getRazorpayOrderId().equals(request.razorpayOrderId())) {
            throw new BadRequestException("Order does not match team registration");
        }

        Payment payment = paymentRepository.findByRazorpayOrderId(request.razorpayOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment record not found for order"));

        if (paymentMockEnabled && isMockOrder(request.razorpayOrderId())) {
            payment.setRazorpayPaymentId(request.razorpayPaymentId());
            payment.setRazorpaySignature(request.razorpaySignature());
            payment.setStatus(PaymentRecordStatus.CAPTURED);
            payment.setVerifiedAt(Instant.now());
            paymentRepository.save(payment);

            team.setRazorpayPaymentId(request.razorpayPaymentId());
            team.setPaymentStatus(PaymentStatus.SUCCESS);
            teamRepository.save(team);

            return new PaymentVerificationResponse(team.getId(), team.getPaymentStatus(), "Mock payment verified successfully");
        }

        String payload = request.razorpayOrderId() + "|" + request.razorpayPaymentId();
        String calculatedSignature = SignatureUtil.hmacSha256(razorpayKeySecret, payload);

        payment.setRazorpayPaymentId(request.razorpayPaymentId());
        payment.setRazorpaySignature(request.razorpaySignature());

        if (calculatedSignature.equals(request.razorpaySignature())) {
            payment.setStatus(PaymentRecordStatus.CAPTURED);
            payment.setVerifiedAt(Instant.now());
            paymentRepository.save(payment);

            team.setRazorpayPaymentId(request.razorpayPaymentId());
            team.setPaymentStatus(PaymentStatus.SUCCESS);
            teamRepository.save(team);

            return new PaymentVerificationResponse(team.getId(), team.getPaymentStatus(), "Payment verified successfully");
        }

        payment.setStatus(PaymentRecordStatus.SIGNATURE_MISMATCH);
        paymentRepository.save(payment);

        team.setPaymentStatus(PaymentStatus.FAILED);
        teamRepository.save(team);

        return new PaymentVerificationResponse(team.getId(), team.getPaymentStatus(), "Payment signature validation failed");
    }

    private void markFreeRegistration(Team team, HackathonEvent event) {
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
    }

    private void validateRazorpayConfig() {
        if (isInvalidConfig(razorpayKeyId) || isInvalidConfig(razorpayKeySecret)) {
            throw new BadRequestException(
                    "Razorpay is not configured. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET, then restart the application."
            );
        }
    }

    private PaymentOrderResponse createMockOrder(Team team, HackathonEvent event, BigDecimal fee) {
        String mockOrderId = "mock_order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        Payment payment = new Payment();
        payment.setTeamId(team.getId());
        payment.setEventId(event.getId());
        payment.setAmount(fee);
        payment.setCurrency("INR");
        payment.setRazorpayOrderId(mockOrderId);
        payment.setStatus(PaymentRecordStatus.ORDER_CREATED);
        Payment savedPayment = paymentRepository.save(payment);

        team.setPaymentRecordId(savedPayment.getId());
        team.setRazorpayOrderId(mockOrderId);
        team.setPaymentStatus(PaymentStatus.PENDING);
        teamRepository.save(team);

        return new PaymentOrderResponse(team.getId(), mockOrderId, "MOCK_MODE", fee, "INR");
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

        return perMemberFee.multiply(BigDecimal.valueOf(payableMembers))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isMockOrder(String orderId) {
        return orderId != null && orderId.startsWith("mock_order_");
    }

    private boolean isInvalidConfig(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }

        String normalized = value.toLowerCase();
        return normalized.contains("replace_me") || normalized.contains("replace");
    }
}
