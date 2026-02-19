const resendVerificationForm = document.getElementById("resendVerificationForm");
const resendVerificationMessageEl = document.getElementById("message");

resendVerificationForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    const formData = new FormData(resendVerificationForm);
    const body = {
        email: formData.get("email")
    };

    try {
        const response = await apiFetch("/api/auth/resend-verification", {
            method: "POST",
            body
        }, false);
        resendVerificationMessageEl.textContent = response.message || "Verification link sent.";
    } catch (error) {
        resendVerificationMessageEl.textContent = error.message;
    }
});
