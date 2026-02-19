const loginForm = document.getElementById("loginForm");
const messageEl = document.getElementById("message");

loginForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const submitButton = loginForm.querySelector("button[type='submit']");
    submitButton.disabled = true;
    const formData = new FormData(loginForm);
    const body = {
        email: formData.get("email"),
        password: formData.get("password")
    };

    try {
        const response = await apiFetch("/api/auth/login", {
            method: "POST",
            body
        }, false);

        setSession(response);
        messageEl.textContent = "Login successful";
        window.location.href = response.role === "FACULTY" ? "/organizer/dashboard" : "/user";
    } catch (error) {
        messageEl.textContent = error.message;
        if ((error.message || "").toLowerCase().includes("verify your email")) {
            alert("Verify your email before login.");
        }
    } finally {
        submitButton.disabled = false;
    }
});
