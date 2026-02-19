const verifyEmailMessageEl = document.getElementById("message");
const verifyParams = new URLSearchParams(window.location.search);
const verifyToken = verifyParams.get("token");

if (!verifyToken) {
    verifyEmailMessageEl.textContent = "Verification token missing. Please request a new verification link.";
} else {
    verifyEmail();
}

async function verifyEmail() {
    try {
        const response = await apiFetch(`/api/auth/verify-email?token=${encodeURIComponent(verifyToken)}`, {
            method: "GET"
        }, false);

        verifyEmailMessageEl.textContent = response.message || "Email verified successfully. Redirecting to login...";
        setTimeout(() => {
            window.location.href = "/login";
        }, 1600);
    } catch (error) {
        verifyEmailMessageEl.textContent = error.message || "Unable to verify email. Please request a new link.";
    }
}
