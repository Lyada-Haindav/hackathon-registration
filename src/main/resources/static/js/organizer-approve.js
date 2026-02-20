const organizerApproveMessageEl = document.getElementById("organizerApproveMessage");
const organizerApproveParams = new URLSearchParams(window.location.search);
const organizerApproveToken = organizerApproveParams.get("token");

if (!organizerApproveToken) {
    organizerApproveMessageEl.textContent = "Approval token is missing.";
} else {
    approveOrganizer();
}

async function approveOrganizer() {
    try {
        const response = await apiFetch(`/api/auth/organizer/approve?token=${encodeURIComponent(organizerApproveToken)}`, {
            method: "GET"
        }, false);

        organizerApproveMessageEl.textContent = response.message || "Organiser approved successfully.";
    } catch (error) {
        organizerApproveMessageEl.textContent = error.message || "Unable to approve organiser.";
    }
}
