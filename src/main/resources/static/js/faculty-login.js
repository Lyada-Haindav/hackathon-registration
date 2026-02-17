const facultyLoginForm = document.getElementById("facultyLoginForm");
const facultyMessageEl = document.getElementById("facultyLoginMessage");

facultyLoginForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    const formData = new FormData(facultyLoginForm);
    const body = {
        email: formData.get("email"),
        password: formData.get("password"),
        secretCode: formData.get("secretCode")
    };

    try {
        const response = await apiFetch("/api/auth/faculty/login", {
            method: "POST",
            body
        }, false);

        setSession(response);
        facultyMessageEl.textContent = "Faculty login successful";
        window.location.href = "/faculty";
    } catch (error) {
        facultyMessageEl.textContent = error.message;
    }
});
