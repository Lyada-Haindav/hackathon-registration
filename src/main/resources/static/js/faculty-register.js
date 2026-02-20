const facultyRegisterForm = document.getElementById("facultyRegisterForm");
const facultyRegisterMessageEl = document.getElementById("facultyRegisterMessage");

facultyRegisterForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    const formData = new FormData(facultyRegisterForm);
    const body = {
        name: formData.get("name"),
        email: formData.get("email"),
        password: formData.get("password"),
        secretCode: formData.get("secretCode")
    };

    try {
        const response = await apiFetch("/api/auth/faculty/register", {
            method: "POST",
            body
        }, false);

        setSession(response);
        if (response && response.token) {
            facultyRegisterMessageEl.textContent = "Organiser registration successful";
            window.location.href = "/organizer/dashboard";
            return;
        }

        facultyRegisterMessageEl.textContent = response.message || "Registration submitted. Wait for owner approval email.";
        facultyRegisterForm.reset();
    } catch (error) {
        facultyRegisterMessageEl.textContent = error.message;
    }
});
