const resetPasswordForm = document.getElementById("resetPasswordForm");
const resetPasswordMessageEl = document.getElementById("message");
const resetTokenInput = document.getElementById("resetToken");

const resetParams = new URLSearchParams(window.location.search);
const resetToken = resetParams.get("token");

if (!resetToken) {
    resetPasswordMessageEl.textContent = "Reset token missing. Request a new password reset link.";
    resetPasswordForm.querySelector("button").disabled = true;
} else {
    resetTokenInput.value = resetToken;
}

resetPasswordForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const formData = new FormData(resetPasswordForm);
    const body = {
        token: formData.get("token"),
        newPassword: formData.get("newPassword")
    };

    try {
        const response = await apiFetch("/api/auth/reset-password", {
            method: "POST",
            body
        }, false);
        resetPasswordMessageEl.textContent = response.message || "Password updated successfully.";
        setTimeout(() => {
            window.location.href = "/login";
        }, 1200);
    } catch (error) {
        resetPasswordMessageEl.textContent = error.message;
    }
});
