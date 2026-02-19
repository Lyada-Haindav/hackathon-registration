const registerForm = document.getElementById("registerForm");
const messageEl = document.getElementById("message");

registerForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const submitButton = registerForm.querySelector("button[type='submit']");
    submitButton.disabled = true;

    const formData = new FormData(registerForm);
    const body = {
        name: formData.get("name"),
        email: formData.get("email"),
        password: formData.get("password")
    };

    try {
        const response = await apiFetch("/api/auth/register", {
            method: "POST",
            body
        }, false);

        if (response.emailVerificationRequired) {
            setSession(null);
            const verifyMessage = response.message || "Verification email sent. Please verify your email before login.";
            messageEl.textContent = verifyMessage;
            alert("Confirm your mail to continue.\n\n" + verifyMessage);
            registerForm.reset();
            setTimeout(() => {
                window.location.href = "/login";
            }, 1800);
            return;
        }

        setSession(response);
        messageEl.textContent = "Registration successful";
        window.location.href = "/user";
    } catch (error) {
        messageEl.textContent = error.message;
    } finally {
        submitButton.disabled = false;
    }
});
