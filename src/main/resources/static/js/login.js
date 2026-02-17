const loginForm = document.getElementById("loginForm");
const messageEl = document.getElementById("message");

loginForm.addEventListener("submit", async (event) => {
    event.preventDefault();
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
        window.location.href = response.role === "FACULTY" ? "/faculty" : "/user";
    } catch (error) {
        messageEl.textContent = error.message;
    }
});
