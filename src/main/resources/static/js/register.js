const registerForm = document.getElementById("registerForm");
const messageEl = document.getElementById("message");

registerForm.addEventListener("submit", async (event) => {
    event.preventDefault();

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

        setSession(response);
        messageEl.textContent = "Registration successful";
        window.location.href = "/user";
    } catch (error) {
        messageEl.textContent = error.message;
    }
});
