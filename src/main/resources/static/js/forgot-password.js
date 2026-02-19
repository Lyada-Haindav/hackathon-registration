const forgotPasswordForm = document.getElementById("forgotPasswordForm");
const forgotPasswordMessageEl = document.getElementById("message");

forgotPasswordForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    const formData = new FormData(forgotPasswordForm);
    const body = {
        email: formData.get("email")
    };

    try {
        const response = await apiFetch("/api/auth/forgot-password", {
            method: "POST",
            body
        }, false);
        forgotPasswordMessageEl.textContent = response.message || "If the email is registered, a reset link has been sent.";
    } catch (error) {
        forgotPasswordMessageEl.textContent = error.message;
    }
});
