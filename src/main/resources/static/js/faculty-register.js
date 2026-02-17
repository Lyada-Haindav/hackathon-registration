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
        facultyRegisterMessageEl.textContent = "Faculty registration successful";
        window.location.href = "/faculty";
    } catch (error) {
        facultyRegisterMessageEl.textContent = error.message;
    }
});
