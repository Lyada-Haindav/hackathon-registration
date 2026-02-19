if (!ensureRole("FACULTY")) {
    throw new Error("Organiser role required");
}

const SELECTED_EVENT_STORAGE_KEY = "selectedFacultyEventId";

const state = {
    events: [],
    selectedEventId: (() => {
        const saved = String(localStorage.getItem(SELECTED_EVENT_STORAGE_KEY) || "").trim();
        return saved || null;
    })(),
    loadedCriteria: [],
    loadedProblems: [],
    loadedEvaluationTeams: [],
    loadedStatusTeams: []
};

const eventForm = document.getElementById("eventForm");
const dynamicFormBuilder = document.getElementById("dynamicFormBuilder");
const problemForm = document.getElementById("problemForm");
const criterionForm = document.getElementById("criterionForm");
const evaluationForm = document.getElementById("evaluationForm");

function getById(id) {
    return document.getElementById(id);
}

function setFacultyMessage(text, isError = false) {
    const msg = getById("facultyMessage");
    if (!msg) {
        return;
    }

    msg.textContent = text;
    msg.classList.toggle("error", isError);
}

function showFacultyPopup(title, message, isError = false) {
    const overlay = getById("facultyPopup");
    const titleEl = getById("facultyPopupTitle");
    const bodyEl = getById("facultyPopupBody");

    if (!overlay || !titleEl || !bodyEl) {
        return;
    }

    titleEl.textContent = title || "Update";
    bodyEl.textContent = message || "Action completed.";
    overlay.classList.toggle("error", isError);
    overlay.classList.remove("hidden");
}

function closeFacultyPopup() {
    const overlay = getById("facultyPopup");
    if (!overlay) {
        return;
    }

    overlay.classList.add("hidden");
    overlay.classList.remove("error");
}

function getEventById(eventId) {
    const id = String(eventId || "").trim();
    if (!id) {
        return null;
    }
    return state.events.find((event) => event.id === id) || null;
}

function updateGlobalEventHint() {
    const hint = getById("globalEventHint");
    if (!hint) {
        return;
    }

    const selected = getEventById(state.selectedEventId);
    if (!selected) {
        hint.textContent = "No event selected. Create or refresh events to continue.";
        return;
    }

    const registrationState = selected.registrationOpen ? "Registration Open" : "Registration Closed";
    hint.textContent = `Selected: ${selected.title} (${selected.id}) • ${registrationState}`;
}

function clearSelectedEvent() {
    state.selectedEventId = null;
    localStorage.removeItem(SELECTED_EVENT_STORAGE_KEY);

    document.querySelectorAll(".event-id-input").forEach((input) => {
        input.value = "";
    });

    const select = getById("globalEventSelect");
    if (select) {
        select.value = "";
    }

    updateGlobalEventHint();
}

function syncEventInputs(eventId, options = {}) {
    const id = String(eventId || "").trim();
    if (!id) {
        return;
    }

    const announce = options.announce !== false;
    state.selectedEventId = id;
    localStorage.setItem(SELECTED_EVENT_STORAGE_KEY, id);

    document.querySelectorAll(".event-id-input").forEach((input) => {
        input.value = id;
    });

    const select = getById("globalEventSelect");
    if (select) {
        select.value = id;
    }

    updateGlobalEventHint();

    if (announce) {
        const selected = getEventById(id);
        setFacultyMessage(selected ? `Selected event: ${selected.title}` : `Selected event: ${id}`);
    }
}

function renderGlobalEventSelector(events = []) {
    const select = getById("globalEventSelect");
    if (!select) {
        return;
    }

    const allEvents = Array.isArray(events) ? events : [];
    select.innerHTML = "";

    if (!allEvents.length) {
        const emptyOption = document.createElement("option");
        emptyOption.value = "";
        emptyOption.textContent = "No events available";
        select.appendChild(emptyOption);
        clearSelectedEvent();
        return;
    }

    allEvents.forEach((event) => {
        const option = document.createElement("option");
        option.value = event.id;
        option.textContent = `${event.title} (${event.id})`;
        select.appendChild(option);
    });

    const selectedExists = Boolean(state.selectedEventId && allEvents.some((event) => event.id === state.selectedEventId));
    const nextEventId = selectedExists ? state.selectedEventId : allEvents[0].id;
    syncEventInputs(nextEventId, { announce: false });
}

function getPreferredEventId(fallbackSelector = ".event-id-input") {
    if (state.selectedEventId) {
        return state.selectedEventId;
    }

    const input = document.querySelector(fallbackSelector);
    return input ? String(input.value || "").trim() : "";
}

function resolveExportFilename(contentDisposition, fallbackFilename) {
    if (!contentDisposition) {
        return fallbackFilename;
    }

    const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
    if (utf8Match && utf8Match[1]) {
        try {
            return decodeURIComponent(utf8Match[1]);
        } catch (error) {
            return utf8Match[1];
        }
    }

    const plainMatch = contentDisposition.match(/filename=\"?([^\";]+)\"?/i);
    if (plainMatch && plainMatch[1]) {
        return plainMatch[1];
    }

    return fallbackFilename;
}

async function downloadExportReport(dataset, format = "csv", fallbackSelector = ".event-id-input") {
    const explicitInput = document.querySelector(fallbackSelector);
    const explicitEventId = explicitInput ? String(explicitInput.value || "").trim() : "";
    const eventId = explicitEventId || state.selectedEventId;
    if (!eventId) {
        setFacultyMessage("Choose an event first to export reports.", true);
        return;
    }

    const token = getToken();
    if (!token) {
        setFacultyMessage("Session expired. Please login again.", true);
        return;
    }

    const normalizedDataset = String(dataset || "").trim().toLowerCase();
    const normalizedFormat = String(format || "csv").trim().toLowerCase();
    const endpoint = `/api/faculty/exports/${encodeURIComponent(eventId)}/${encodeURIComponent(normalizedDataset)}?format=${encodeURIComponent(normalizedFormat)}`;

    try {
        const response = await fetch(endpoint, {
            method: "GET",
            headers: {
                Authorization: `Bearer ${token}`
            }
        });

        if (!response.ok) {
            const contentType = response.headers.get("content-type") || "";
            let message = "Failed to download export file.";

            if (contentType.includes("application/json")) {
                const payload = await response.json();
                message = payload.message || message;
            } else {
                const text = await response.text();
                if (text) {
                    message = text;
                }
            }

            throw new Error(message);
        }

        const fallbackName = `${normalizedDataset}.${normalizedFormat === "xlsx" ? "xlsx" : "csv"}`;
        const filename = resolveExportFilename(response.headers.get("content-disposition"), fallbackName);
        const blob = await response.blob();
        const downloadUrl = URL.createObjectURL(blob);

        const link = document.createElement("a");
        link.href = downloadUrl;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(downloadUrl);

        const labelMap = {
            teams: "Teams",
            payments: "Payments",
            leaderboard: "Leaderboard",
            evaluations: "Evaluations"
        };
        const datasetLabel = labelMap[normalizedDataset] || normalizedDataset;
        setFacultyMessage(`${datasetLabel} ${normalizedFormat.toUpperCase()} export downloaded.`);
    } catch (error) {
        setFacultyMessage(error.message, true);
    }
}

function activeBadge(active) {
    return `<span class="status-pill ${active ? "active" : "inactive"}">${active ? "ACTIVE" : "INACTIVE"}</span>`;
}

function eventStateBadge(event) {
    if (event.onHold) {
        return `<span class="status-pill hold">ON HOLD</span>`;
    }
    return activeBadge(event.active);
}

function leaderboardBadge(visible) {
    return `<span class="status-pill ${visible ? "active" : "inactive"}">${visible ? "LEADERBOARD LIVE" : "LEADERBOARD HIDDEN"}</span>`;
}

function paymentBadge(status) {
    const normalized = String(status || "PENDING").toLowerCase();
    const badgeClass = normalized === "success" ? "active" : normalized === "failed" ? "inactive" : "pending";
    return `<span class="status-pill ${badgeClass}">${status}</span>`;
}

function registrationBadge(isOpen) {
    return `<span class="status-pill ${isOpen ? "active" : "inactive"}">${isOpen ? "REGISTRATION OPEN" : "REGISTRATION CLOSED"}</span>`;
}

function teamLeaderLabel(team) {
    const members = Array.isArray(team && team.members) ? team.members : [];
    const leader = members.find((member) => Boolean(member.leader));

    if (!leader) {
        return "Leader: Not set";
    }

    return `Leader: ${leader.name || "N/A"} (${leader.phone || "No phone"})`;
}

function eventFeeSummary(event) {
    const totalFee = Number(event.registrationFee || 0);
    const perMemberFee = Number(event.feePerMember || 0);
    const splitMembers = Number(event.feeSplitMembers || 1);
    return `INR ${totalFee.toFixed(2)} total (INR ${perMemberFee.toFixed(2)}/member for ${splitMembers} members)`;
}

function todayIsoDate() {
    return new Date().toISOString().split("T")[0];
}

function shiftIsoDate(isoDate, days) {
    const date = new Date(`${isoDate}T00:00:00`);
    date.setDate(date.getDate() + days);
    return date.toISOString().split("T")[0];
}

function initializeEventFormDefaults() {
    if (!eventForm) {
        return;
    }

    const startInput = eventForm.querySelector("[name='startDate']");
    const endInput = eventForm.querySelector("[name='endDate']");
    const openInput = eventForm.querySelector("[name='registrationOpenDate']");
    const closeInput = eventForm.querySelector("[name='registrationCloseDate']");

    if (!startInput || !endInput || !openInput || !closeInput) {
        return;
    }

    const today = todayIsoDate();
    if (!startInput.value) {
        startInput.value = shiftIsoDate(today, 7);
    }
    if (!endInput.value) {
        endInput.value = shiftIsoDate(startInput.value, 1);
    }
    if (!openInput.value) {
        openInput.value = today;
    }
    if (!closeInput.value) {
        closeInput.value = endInput.value;
    }

    startInput.addEventListener("change", () => {
        if (endInput.value && endInput.value < startInput.value) {
            endInput.value = startInput.value;
        }
        if (closeInput.value && closeInput.value < openInput.value) {
            closeInput.value = endInput.value || openInput.value;
        }
    });

    endInput.addEventListener("change", () => {
        if (!closeInput.value || closeInput.value < openInput.value) {
            closeInput.value = endInput.value || openInput.value;
        }
    });
}

function normalizeText(value) {
    return String(value || "").trim().toLowerCase();
}

function teamMatchesQuery(team, query) {
    if (!query) {
        return true;
    }

    const teamName = normalizeText(team.teamName);
    const teamId = normalizeText(team.teamId);
    return teamName.includes(query) || teamId.includes(query);
}

function getEvaluationTeamSearchQuery() {
    const input = getById("evaluationTeamSearch");
    return normalizeText(input ? input.value : "");
}

function getTeamStatusSearchQuery() {
    const input = getById("teamsSearchInput");
    return normalizeText(input ? input.value : "");
}

function createFieldRowTemplate(field = {}) {
    return `
        <div class="field-builder-row">
            <input class="field-key" placeholder="Field key (teamIdea)" value="${field.key || ""}">
            <input class="field-label" placeholder="Field label (Team Idea)" value="${field.label || ""}">
            <select class="field-type">
                <option value="TEXT" ${field.type === "TEXT" ? "selected" : ""}>Text</option>
                <option value="NUMBER" ${field.type === "NUMBER" ? "selected" : ""}>Number</option>
                <option value="EMAIL" ${field.type === "EMAIL" ? "selected" : ""}>Email</option>
                <option value="PHONE" ${field.type === "PHONE" ? "selected" : ""}>Phone</option>
                <option value="DATE" ${field.type === "DATE" ? "selected" : ""}>Date</option>
                <option value="SELECT" ${field.type === "SELECT" ? "selected" : ""}>Select</option>
                <option value="TEXTAREA" ${field.type === "TEXTAREA" ? "selected" : ""}>Textarea</option>
            </select>
            <label class="inline-check">
                <input class="field-required" type="checkbox" ${field.required !== false ? "checked" : ""}>
                Required
            </label>
            <input class="field-options" placeholder="Options (comma separated for Select)" value="${(field.options || []).join(", ")}">
            <button type="button" class="ghost-btn small remove-field">Remove</button>
        </div>
    `;
}

function bindFieldRemoveButtons() {
    document.querySelectorAll(".remove-field").forEach((button) => {
        button.onclick = () => {
            const rows = document.querySelectorAll("#formFieldRows .field-builder-row");
            if (rows.length <= 1) {
                setFacultyMessage("At least one field is required.", true);
                return;
            }
            button.closest(".field-builder-row")?.remove();
        };
    });
}

function addFieldRow(field = {}) {
    const container = getById("formFieldRows");
    if (!container) {
        return;
    }

    container.insertAdjacentHTML("beforeend", createFieldRowTemplate(field));
    bindFieldRemoveButtons();
}

function collectFormFields() {
    const rows = document.querySelectorAll("#formFieldRows .field-builder-row");
    if (!rows.length) {
        throw new Error("Add at least one form field.");
    }

    const fields = [];

    for (const row of rows) {
        const key = row.querySelector(".field-key")?.value.trim() || "";
        const label = row.querySelector(".field-label")?.value.trim() || "";
        const type = row.querySelector(".field-type")?.value || "TEXT";
        const required = row.querySelector(".field-required")?.checked ?? true;
        const optionsRaw = row.querySelector(".field-options")?.value.trim() || "";

        if (!key || !label) {
            throw new Error("Each form field needs both key and label.");
        }

        const options = optionsRaw ? optionsRaw.split(",").map((item) => item.trim()).filter(Boolean) : [];
        if (type === "SELECT" && options.length === 0) {
            throw new Error(`Select field '${label}' must include options.`);
        }

        fields.push({ key, label, type, required, options });
    }

    return fields;
}

function renderFacultyEvents(events) {
    const container = getById("facultyEventCards");
    if (!container) {
        return;
    }

    container.innerHTML = "";

    if (!events.length) {
        container.innerHTML = "<p class='muted'>No events created yet.</p>";
        return;
    }

    events.forEach((event) => {
        const card = document.createElement("div");
        card.className = "event-card compact-event";
        card.innerHTML = `
            <div class="section-title-row">
                <h3>${event.title}</h3>
                ${eventStateBadge(event)}
            </div>
            <p>${event.description}</p>
            <p class="muted">ID: ${event.id}</p>
            <p class="muted">Registration: ${event.registrationOpenDate} to ${event.registrationCloseDate}</p>
            <div class="row">
                ${registrationBadge(Boolean(event.registrationOpen))}
                ${leaderboardBadge(event.leaderboardVisible)}
            </div>
            <div class="meta-row">
                <span>${event.startDate} to ${event.endDate}</span>
                <strong>${eventFeeSummary(event)}</strong>
            </div>
            <div class="row">
                <button type="button" class="ghost-btn small use-event-btn">Use This Event</button>
                <button type="button" class="ghost-btn small registration-toggle-btn">
                    ${event.registrationOpen ? "Close Registration" : "Open Registration"}
                </button>
                <button type="button" class="ghost-btn small leaderboard-visibility-btn">
                    ${event.leaderboardVisible ? "Hide Leaderboard" : "Publish Leaderboard"}
                </button>
                <button type="button" class="ghost-btn small hold-resume-event-btn">
                    ${event.onHold ? "Resume Event" : "Hold Event"}
                </button>
                <button type="button" class="ghost-btn small danger-outline delete-event-btn">Delete Event</button>
            </div>
        `;

        card.querySelector(".use-event-btn")?.addEventListener("click", () => syncEventInputs(event.id));
        card.querySelector(".registration-toggle-btn")?.addEventListener("click", () =>
            setRegistrationOpen(event.id, !event.registrationOpen)
        );
        card.querySelector(".leaderboard-visibility-btn")?.addEventListener("click", () =>
            setLeaderboardVisibility(event.id, !event.leaderboardVisible)
        );
        card.querySelector(".hold-resume-event-btn")?.addEventListener("click", () =>
            event.onHold ? resumeEvent(event.id) : holdEvent(event.id)
        );
        card.querySelector(".delete-event-btn")?.addEventListener("click", () => deleteEvent(event.id, event.title));

        container.appendChild(card);
    });
}

async function setLeaderboardVisibility(eventId, visible) {
    try {
        const updated = await apiFetch(`/api/faculty/events/${eventId}/leaderboard-visibility`, {
            method: "PUT",
            body: { visible }
        });

        state.events = state.events.map((event) => (event.id === eventId ? updated : event));
        renderFacultyEvents(state.events);
        renderGlobalEventSelector(state.events);
        setFacultyMessage(
            visible
                ? "Leaderboard is now visible to users for this event."
                : "Leaderboard is now hidden from users for this event."
        );
    } catch (error) {
        setFacultyMessage(error.message, true);
    }
}

async function holdEvent(eventId) {
    try {
        const updated = await apiFetch(`/api/faculty/events/${eventId}/hold`, { method: "PUT" });
        state.events = state.events.map((event) => (event.id === eventId ? updated : event));
        renderFacultyEvents(state.events);
        renderGlobalEventSelector(state.events);
        setFacultyMessage("Event is now on hold. Users cannot register for it.");
    } catch (error) {
        setFacultyMessage(error.message, true);
    }
}

async function resumeEvent(eventId) {
    try {
        const updated = await apiFetch(`/api/faculty/events/${eventId}/resume`, { method: "PUT" });
        state.events = state.events.map((event) => (event.id === eventId ? updated : event));
        renderFacultyEvents(state.events);
        renderGlobalEventSelector(state.events);
        setFacultyMessage("Event resumed and is now active.");
    } catch (error) {
        setFacultyMessage(error.message, true);
    }
}

async function setRegistrationOpen(eventId, open) {
    const action = open ? "open-registration" : "close-registration";
    try {
        const updated = await apiFetch(`/api/faculty/events/${eventId}/${action}`, { method: "PUT" });
        state.events = state.events.map((event) => (event.id === eventId ? updated : event));
        renderFacultyEvents(state.events);
        renderGlobalEventSelector(state.events);
        setFacultyMessage(open ? "Registration opened for this event." : "Registration closed for this event.");
    } catch (error) {
        setFacultyMessage(error.message, true);
    }
}

async function deleteEvent(eventId, title) {
    const confirmDelete = window.confirm(`Delete event "${title}"? This removes teams, forms, payments, criteria, and evaluations for this event.`);
    if (!confirmDelete) {
        return;
    }

    try {
        await apiFetch(`/api/faculty/events/${eventId}`, { method: "DELETE" });

        state.events = state.events.filter((event) => event.id !== eventId);
        renderFacultyEvents(state.events);
        renderGlobalEventSelector(state.events);
        if (state.events.length === 0) {
            clearSelectedEvent();
        }
        setFacultyMessage("Event deleted successfully.");
    } catch (error) {
        setFacultyMessage(error.message, true);
    }
}

function renderCriteriaCards(criteria) {
    const container = getById("criteriaCards");
    if (!container) {
        return;
    }

    container.innerHTML = "";
    if (!criteria.length) {
        container.innerHTML = "<p class='muted'>No criteria found for this event.</p>";
        return;
    }

    criteria.forEach((item) => {
        const card = document.createElement("div");
        card.className = "mini-card";
        card.innerHTML = `
            <h4>${item.name}</h4>
            <p>Criterion ID: ${item.id}</p>
            <p>Max Marks: ${Number(item.maxMarks).toFixed(2)}</p>
        `;
        container.appendChild(card);
    });
}

function renderProblemCards(problems) {
    const container = getById("problemListForFaculty");
    if (!container) {
        return;
    }

    container.innerHTML = "";
    if (!problems.length) {
        container.innerHTML = "<p class='muted'>No problem statements for this event.</p>";
        return;
    }

    problems.forEach((problem) => {
        const card = document.createElement("div");
        card.className = "mini-card";
        card.innerHTML = `
            <div class="section-title-row">
                <h4>${problem.title}</h4>
                ${problem.released ? activeBadge(true) : activeBadge(false)}
            </div>
            <p>${problem.description}</p>
            <p class="muted">ID: ${problem.id}</p>
            ${problem.released ? "" : '<button type="button" class="ghost-btn small">Release Now</button>'}
        `;

        const releaseButton = card.querySelector("button");
        if (releaseButton) {
            releaseButton.addEventListener("click", () => releaseProblem(problem.id));
        }

        container.appendChild(card);
    });
}

function renderEvaluationRows(criteria) {
    const container = getById("evaluationCriteriaRows");
    if (!container) {
        return;
    }

    container.innerHTML = "";
    if (!criteria.length) {
        container.innerHTML = "<p class='muted'>No criteria found. Create criteria first.</p>";
        return;
    }

    criteria.forEach((criterion) => {
        const row = document.createElement("div");
        row.className = "score-row";
        row.innerHTML = `
            <div>
                <strong>${criterion.name}</strong>
                <p class="muted">Max: ${Number(criterion.maxMarks).toFixed(2)}</p>
            </div>
            <input type="number" min="0" step="0.01" data-criterion-id="${criterion.id}" data-max="${criterion.maxMarks}" placeholder="Marks Given" required>
        `;
        container.appendChild(row);
    });
}

function renderEvaluationTeams(teams, preferredTeamId = "") {
    if (!evaluationForm) {
        return;
    }

    const select = evaluationForm.querySelector("[name='teamId']");
    if (!select) {
        return;
    }

    const allTeams = Array.isArray(teams) ? teams : [];
    const searchQuery = getEvaluationTeamSearchQuery();
    const filteredTeams = searchQuery ? allTeams.filter((team) => teamMatchesQuery(team, searchQuery)) : allTeams;

    const previousSelection = preferredTeamId || String(select.value || "");
    select.innerHTML = "";

    if (!allTeams.length) {
        select.innerHTML = "<option value=''>No teams available for this event</option>";
        return;
    }

    if (!filteredTeams.length) {
        select.innerHTML = "<option value=''>No teams match the search</option>";
        return;
    }

    const placeholder = document.createElement("option");
    placeholder.value = "";
    placeholder.textContent = "Select team by name";
    select.appendChild(placeholder);

    filteredTeams.forEach((team) => {
        const option = document.createElement("option");
        option.value = team.teamId;
        option.textContent = `${team.teamName} (${team.teamId})`;
        select.appendChild(option);
    });

    if (previousSelection && filteredTeams.some((team) => team.teamId === previousSelection)) {
        select.value = previousSelection;
    }
}

function renderTeams(teams) {
    const container = getById("teamsOutputCards");
    if (!container) {
        return;
    }

    container.innerHTML = "";

    const allTeams = Array.isArray(teams) ? teams : [];
    const searchQuery = getTeamStatusSearchQuery();
    const filteredTeams = searchQuery ? allTeams.filter((team) => teamMatchesQuery(team, searchQuery)) : allTeams;

    if (!allTeams.length) {
        container.innerHTML = "<p class='muted'>No teams registered for this event yet.</p>";
        return;
    }

    if (!filteredTeams.length) {
        container.innerHTML = "<p class='muted'>No teams match the search.</p>";
        return;
    }

    filteredTeams.forEach((team) => {
        const card = document.createElement("div");
        card.className = "mini-card";
        card.innerHTML = `
            <div class="section-title-row">
                <h4>${team.teamName}</h4>
                ${paymentBadge(team.paymentStatus)}
            </div>
            <p>Team ID: ${team.teamId}</p>
            <p>Problem Statement: ${team.selectedProblemStatementTitle || "Not selected"}</p>
            <p>${teamLeaderLabel(team)}</p>
            <p>Size: ${team.teamSize}</p>
            <p>Score: ${Number(team.totalScore || 0).toFixed(2)}</p>
            <button type="button" class="ghost-btn small">Evaluate This Team</button>
        `;

        card.querySelector("button")?.addEventListener("click", () => {
            if (evaluationForm) {
                const evalEventId = evaluationForm.querySelector("[name='eventId']");
                if (evalEventId) {
                    evalEventId.value = team.eventId;
                }
                syncEventInputs(team.eventId);
                setFacultyMessage(`Ready to evaluate team ${team.teamName}`);
                prepareEvaluation(team.teamId);
                return;
            }

            const params = new URLSearchParams({ eventId: team.eventId, teamId: team.teamId });
            window.location.href = `/organizer/evaluation?${params.toString()}`;
        });

        container.appendChild(card);
    });
}

function renderProblemSelectionCounts(teams, problems) {
    const container = getById("problemSelectionCounts");
    if (!container) {
        return;
    }

    container.innerHTML = "";

    const allTeams = Array.isArray(teams) ? teams : [];
    const allProblems = Array.isArray(problems) ? problems : [];

    if (!allProblems.length) {
        container.innerHTML = "<p class='muted'>No problem statements created for this event.</p>";
        return;
    }

    const countsByProblemId = new Map();
    allProblems.forEach((problem) => countsByProblemId.set(problem.id, 0));

    allTeams.forEach((team) => {
        const selectedId = String(team.selectedProblemStatementId || "").trim();
        if (!selectedId) {
            return;
        }
        if (countsByProblemId.has(selectedId)) {
            countsByProblemId.set(selectedId, countsByProblemId.get(selectedId) + 1);
        }
    });

    const notSelectedCount = allTeams.filter((team) => !String(team.selectedProblemStatementId || "").trim()).length;
    const sortedProblems = [...allProblems].sort((a, b) => {
        const aCount = countsByProblemId.get(a.id) || 0;
        const bCount = countsByProblemId.get(b.id) || 0;
        if (bCount !== aCount) {
            return bCount - aCount;
        }
        return String(a.title || "").localeCompare(String(b.title || ""));
    });

    sortedProblems.forEach((problem) => {
        const selectedCount = countsByProblemId.get(problem.id) || 0;
        const card = document.createElement("div");
        card.className = "mini-card";
        card.innerHTML = `
            <div class="section-title-row">
                <h4>${problem.title}</h4>
                <span class="status-pill active">${selectedCount} selected</span>
            </div>
            <p>${problem.description || "No description available."}</p>
        `;
        container.appendChild(card);
    });

    const notSelectedCard = document.createElement("div");
    notSelectedCard.className = "mini-card";
    notSelectedCard.innerHTML = `
        <div class="section-title-row">
            <h4>Not Selected Yet</h4>
            <span class="status-pill pending">${notSelectedCount} teams</span>
        </div>
        <p>Teams that have not chosen any problem statement.</p>
    `;
    container.appendChild(notSelectedCard);
}

function renderDeploymentReadiness(result) {
    const summary = getById("deploymentSummary");
    const container = getById("deploymentChecks");
    if (!summary || !container) {
        return;
    }

    container.innerHTML = "";
    if (!result || !Array.isArray(result.checks) || result.checks.length === 0) {
        summary.textContent = "No readiness data available.";
        return;
    }

    summary.textContent = `${result.summary} Checked at: ${new Date(result.checkedAt).toLocaleString()}`;

    result.checks.forEach((check) => {
        const card = document.createElement("div");
        card.className = "mini-card";
        const statusClass = check.passed ? "active" : "inactive";
        card.innerHTML = `
            <div class="section-title-row">
                <h4>${check.name}</h4>
                <span class="status-pill ${statusClass}">${check.passed ? "PASS" : "FAIL"}</span>
            </div>
            <p>${check.message}</p>
            <p class="muted">Required: ${check.required ? "Yes" : "No"}</p>
        `;
        container.appendChild(card);
    });
}

async function loadDeploymentReadiness() {
    try {
        const result = await apiFetch("/api/faculty/deployment/readiness");
        renderDeploymentReadiness(result);
        setFacultyMessage(result.ready ? "Deployment readiness passed." : "Deployment readiness has blocking issues.", !result.ready);
    } catch (error) {
        setFacultyMessage(error.message, true);
    }
}

async function loadProblemSelectionCounts() {
    const eventId = getPreferredEventId("#teamsEventId");
    if (!eventId) {
        setFacultyMessage("Choose an event first.", true);
        return;
    }

    try {
        const [teams, problems] = await Promise.all([
            apiFetch(`/api/faculty/events/${eventId}/teams`),
            apiFetch(`/api/faculty/problem-statements/${eventId}`)
        ]);

        state.loadedStatusTeams = teams;
        state.loadedEvaluationTeams = teams;
        state.loadedProblems = problems;

        renderProblemSelectionCounts(teams, problems);
    } catch (error) {
        setFacultyMessage(error.message, true);
    }
}

async function loadFacultyEvents() {
    try {
        const events = await apiFetch("/api/faculty/events");
        state.events = Array.isArray(events) ? events : [];
        renderFacultyEvents(state.events);
        renderGlobalEventSelector(state.events);
    } catch (error) {
        setFacultyMessage(error.message, true);
    }
}

const EVENT_POSTER_MAX_BYTES = 2 * 1024 * 1024;
const ALLOWED_POSTER_TYPES = new Set([
    "image/png",
    "image/jpeg",
    "image/jpg",
    "image/webp",
    "image/gif"
]);

function resetEventPosterPreview() {
    const dataInput = getById("eventPosterData");
    const previewWrap = getById("eventPosterPreviewWrap");
    const preview = getById("eventPosterPreview");
    const fileInput = getById("eventPosterFile");

    if (dataInput) {
        dataInput.value = "";
    }
    if (preview) {
        preview.removeAttribute("src");
    }
    if (previewWrap) {
        previewWrap.classList.add("hidden");
    }
    if (fileInput) {
        fileInput.value = "";
    }
}

function readPosterAsDataUrl(file) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result || ""));
        reader.onerror = () => reject(new Error("Unable to read poster image."));
        reader.readAsDataURL(file);
    });
}

async function applyPosterFile(file) {
    if (!file) {
        resetEventPosterPreview();
        return "";
    }

    if (!ALLOWED_POSTER_TYPES.has(String(file.type || "").toLowerCase())) {
        throw new Error("Upload PNG, JPG, WEBP, or GIF poster only.");
    }

    if (file.size > EVENT_POSTER_MAX_BYTES) {
        throw new Error("Poster image must be 2MB or smaller.");
    }

    const dataUrl = await readPosterAsDataUrl(file);
    const dataInput = getById("eventPosterData");
    const previewWrap = getById("eventPosterPreviewWrap");
    const preview = getById("eventPosterPreview");

    if (dataInput) {
        dataInput.value = dataUrl;
    }
    if (preview) {
        preview.src = dataUrl;
    }
    if (previewWrap) {
        previewWrap.classList.remove("hidden");
    }

    return dataUrl;
}

if (eventForm) {
    const posterFileInput = getById("eventPosterFile");
    const posterClearButton = getById("eventPosterClearBtn");
    const posterDataInput = getById("eventPosterData");

    if (posterFileInput) {
        posterFileInput.addEventListener("change", async () => {
            const file = posterFileInput.files && posterFileInput.files[0] ? posterFileInput.files[0] : null;
            try {
                await applyPosterFile(file);
            } catch (error) {
                setFacultyMessage(error.message, true);
                resetEventPosterPreview();
            }
        });
    }

    if (posterClearButton) {
        posterClearButton.addEventListener("click", () => {
            resetEventPosterPreview();
            setFacultyMessage("Poster removed.");
        });
    }

    eventForm.addEventListener("submit", async (event) => {
        event.preventDefault();

        const posterFile = posterFileInput && posterFileInput.files && posterFileInput.files[0] ? posterFileInput.files[0] : null;
        if (posterFile && posterDataInput && !String(posterDataInput.value || "").trim()) {
            try {
                await applyPosterFile(posterFile);
            } catch (error) {
                setFacultyMessage(error.message, true);
                return;
            }
        }

        const fd = new FormData(eventForm);

        const startDate = String(fd.get("startDate") || "").trim();
        const endDate = String(fd.get("endDate") || "").trim();
        let registrationOpenDate = String(fd.get("registrationOpenDate") || "").trim();
        let registrationCloseDate = String(fd.get("registrationCloseDate") || "").trim();

        if (!registrationOpenDate) {
            registrationOpenDate = todayIsoDate();
        }
        if (!registrationCloseDate) {
            registrationCloseDate = endDate || registrationOpenDate;
        }
        if (registrationCloseDate < registrationOpenDate) {
            setFacultyMessage("Registration close date cannot be before registration open date.", true);
            return;
        }

        const body = {
            title: fd.get("title"),
            description: fd.get("description"),
            aboutEvent: fd.get("aboutEvent"),
            posterUrl: fd.get("posterUrl"),
            startDate,
            endDate,
            registrationOpenDate,
            registrationCloseDate,
            registrationFee: Number(fd.get("registrationFee")),
            active: fd.get("active") === "true"
        };

        try {
            const response = await apiFetch("/api/faculty/events", { method: "POST", body });
            setFacultyMessage(`Event created successfully. Event ID: ${response.id}`);
            eventForm.reset();
            resetEventPosterPreview();
            syncEventInputs(response.id);
            loadFacultyEvents();
        } catch (error) {
            setFacultyMessage(error.message, true);
        }
    });
}

if (dynamicFormBuilder) {
    dynamicFormBuilder.addEventListener("submit", async (event) => {
        event.preventDefault();

        try {
            const formData = new FormData(dynamicFormBuilder);
            const eventId = String(formData.get("eventId") || "").trim();
            if (!eventId) {
                throw new Error("Select an event first.");
            }

            const fields = collectFormFields();
            const body = { eventId, fields };

            const response = await apiFetch("/api/faculty/forms", { method: "POST", body });
            setFacultyMessage(`Dynamic form saved. Form ID: ${response.id}`);
        } catch (error) {
            setFacultyMessage(error.message, true);
        }
    });
}

async function loadExistingForm() {
    if (!dynamicFormBuilder) {
        setFacultyMessage("Form builder is unavailable on this page.", true);
        return;
    }

    const eventId = String(dynamicFormBuilder.querySelector("[name='eventId']")?.value || "").trim();
    if (!eventId) {
        setFacultyMessage("Select an event first.", true);
        return;
    }

    try {
        const form = await apiFetch(`/api/faculty/forms/${eventId}`);
        const container = getById("formFieldRows");
        if (!container) {
            return;
        }

        container.innerHTML = "";
        (form.fields || []).forEach((field) => addFieldRow(field));
        if (!form.fields || form.fields.length === 0) {
            addFieldRow();
        }
        setFacultyMessage("Existing form loaded.");
    } catch (error) {
        setFacultyMessage(error.message, true);
    }
}

if (problemForm) {
    problemForm.addEventListener("submit", async (event) => {
        event.preventDefault();

        const fd = new FormData(problemForm);
        const body = {
            eventId: fd.get("eventId"),
            title: fd.get("title"),
            description: fd.get("description")
        };

        try {
            await apiFetch("/api/faculty/problem-statements", { method: "POST", body });
            setFacultyMessage("Problem statement created.");
            const titleInput = problemForm.querySelector("[name='title']");
            const descriptionInput = problemForm.querySelector("[name='description']");
            if (titleInput) {
                titleInput.value = "";
            }
            if (descriptionInput) {
                descriptionInput.value = "";
            }
            loadProblemsForEvent();
        } catch (error) {
            setFacultyMessage(error.message, true);
        }
    });
}

async function loadProblemsForEvent() {
    const eventId = getPreferredEventId("#problemForm [name='eventId']");
    if (!eventId) {
        setFacultyMessage("Choose an event first.", true);
        return;
    }

    try {
        const [problems, teams] = await Promise.all([
            apiFetch(`/api/faculty/problem-statements/${eventId}`),
            apiFetch(`/api/faculty/events/${eventId}/teams`)
        ]);

        state.loadedStatusTeams = teams;
        state.loadedEvaluationTeams = teams;
        state.loadedProblems = problems;

        renderProblemCards(problems);
        renderProblemSelectionCounts(teams, problems);
    } catch (error) {
        setFacultyMessage(error.message, true);
    }
}

async function releaseProblem(problemId) {
    try {
        await apiFetch(`/api/faculty/problem-statements/${problemId}/release`, { method: "PUT" });
        setFacultyMessage("Problem statement released.");
        loadProblemsForEvent();
    } catch (error) {
        setFacultyMessage(error.message, true);
    }
}

if (criterionForm) {
    criterionForm.addEventListener("submit", async (event) => {
        event.preventDefault();

        const fd = new FormData(criterionForm);
        const body = {
            eventId: fd.get("eventId"),
            name: fd.get("name"),
            maxMarks: Number(fd.get("maxMarks"))
        };

        try {
            await apiFetch("/api/faculty/criteria", { method: "POST", body });
            setFacultyMessage("Criterion added successfully.");
            const nameInput = criterionForm.querySelector("[name='name']");
            const maxMarksInput = criterionForm.querySelector("[name='maxMarks']");
            if (nameInput) {
                nameInput.value = "";
            }
            if (maxMarksInput) {
                maxMarksInput.value = "";
            }
            loadCriteria();
        } catch (error) {
            setFacultyMessage(error.message, true);
        }
    });
}

async function loadCriteria() {
    const eventId = getPreferredEventId("#criterionForm [name='eventId']");
    if (!eventId) {
        setFacultyMessage("Choose an event first.", true);
        return;
    }

    try {
        const criteria = await apiFetch(`/api/faculty/criteria/${eventId}`);
        state.loadedCriteria = criteria;
        renderCriteriaCards(criteria);
    } catch (error) {
        setFacultyMessage(error.message, true);
    }
}

async function prepareEvaluation(preferredTeamId = "") {
    if (!evaluationForm) {
        setFacultyMessage("Evaluation form is unavailable on this page.", true);
        return;
    }

    const eventIdInput = evaluationForm.querySelector("[name='eventId']");
    if (!eventIdInput) {
        setFacultyMessage("Event ID input is missing on evaluation form.", true);
        return;
    }

    const eventId = String(eventIdInput.value || "").trim() || getPreferredEventId("#evaluationForm [name='eventId']");

    if (!eventId) {
        setFacultyMessage("Select an event first.", true);
        return;
    }

    eventIdInput.value = eventId;

    try {
        const [criteria, teams] = await Promise.all([
            apiFetch(`/api/faculty/criteria/${eventId}`),
            apiFetch(`/api/faculty/events/${eventId}/teams`)
        ]);

        state.loadedCriteria = criteria;
        state.loadedEvaluationTeams = teams;

        const evaluationSearchInput = getById("evaluationTeamSearch");
        if (evaluationSearchInput) {
            evaluationSearchInput.value = "";
        }

        renderEvaluationRows(criteria);
        renderEvaluationTeams(teams, preferredTeamId);

        if (!teams.length) {
            setFacultyMessage("Scoring sheet loaded, but no teams are registered for this event yet.", true);
            return;
        }

        setFacultyMessage(`Scoring sheet loaded with ${teams.length} team(s). Select a team name and save.`);
    } catch (error) {
        setFacultyMessage(error.message, true);
    }
}

if (evaluationForm) {
    evaluationForm.addEventListener("submit", async (event) => {
        event.preventDefault();

        const fd = new FormData(evaluationForm);
        const eventId = String(fd.get("eventId") || "").trim();
        const teamId = String(fd.get("teamId") || "").trim();
        const description = String(fd.get("description") || "").trim();

        if (!eventId || !teamId) {
            setFacultyMessage("Event and team selection are required.", true);
            return;
        }

        const scoreInputs = Array.from(document.querySelectorAll("#evaluationCriteriaRows input[data-criterion-id]"));
        if (scoreInputs.length === 0) {
            setFacultyMessage("Load criteria for scoring first.", true);
            return;
        }

        const scores = [];
        for (const input of scoreInputs) {
            const criterionId = input.dataset.criterionId;
            const max = Number(input.dataset.max || 0);
            const valueRaw = input.value;

            if (valueRaw === "") {
                continue;
            }

            const marksGiven = Number(valueRaw);
            if (Number.isNaN(marksGiven) || marksGiven < 0) {
                setFacultyMessage("Marks must be a non-negative number.", true);
                return;
            }
            if (marksGiven > max) {
                setFacultyMessage(`Marks cannot exceed max (${max}) for one of the criteria.`, true);
                return;
            }

            scores.push({ criterionId, marksGiven });
        }

        if (scores.length === 0) {
            setFacultyMessage("Enter at least one criterion score.", true);
            return;
        }

        try {
            const body = { eventId, teamId, description, scores };
            const records = await apiFetch("/api/faculty/evaluations", { method: "POST", body });
            const total = records.length > 0 ? Number(records[0].totalScore || 0).toFixed(2) : "0.00";
            setFacultyMessage(`Scores saved. Team total is now ${total}.`);
            showFacultyPopup("Scores Saved", `Team total score: ${total}`);
        } catch (error) {
            setFacultyMessage(error.message, true);
            showFacultyPopup("Unable To Save Scores", error.message, true);
        }
    });
}

async function loadTeamsByEvent() {
    const eventId = getPreferredEventId("#teamsEventId");
    if (!eventId) {
        setFacultyMessage("Choose an event first.", true);
        return;
    }

    try {
        const [teams, problems] = await Promise.all([
            apiFetch(`/api/faculty/events/${eventId}/teams`),
            apiFetch(`/api/faculty/problem-statements/${eventId}`)
        ]);

        state.loadedStatusTeams = teams;
        state.loadedEvaluationTeams = teams;
        state.loadedProblems = problems;

        const teamsSearchInput = getById("teamsSearchInput");
        if (teamsSearchInput) {
            teamsSearchInput.value = "";
        }

        renderTeams(teams);
        renderEvaluationTeams(teams);
        renderProblemSelectionCounts(teams, problems);
    } catch (error) {
        setFacultyMessage(error.message, true);
    }
}

function prefillEvaluationFromQuery() {
    if (!evaluationForm) {
        return;
    }

    const params = new URLSearchParams(window.location.search);
    const eventId = String(params.get("eventId") || "").trim();
    const teamId = String(params.get("teamId") || "").trim();

    if (!eventId) {
        return;
    }

    const eventIdInput = evaluationForm.querySelector("[name='eventId']");
    if (eventIdInput) {
        eventIdInput.value = eventId;
    }

    syncEventInputs(eventId);
    prepareEvaluation(teamId);
}

setFacultyMessage(`Logged in as ${localStorage.getItem("email") || "organiser"}`);
initializeEventFormDefaults();
if (state.selectedEventId) {
    syncEventInputs(state.selectedEventId, { announce: false });
}

if (getById("formFieldRows") && document.querySelectorAll("#formFieldRows .field-builder-row").length === 0) {
    addFieldRow({ key: "teamIdea", label: "Team Idea", type: "TEXTAREA", required: true, options: [] });
    addFieldRow({ key: "contactPhone", label: "Contact Phone", type: "PHONE", required: true, options: [] });
}

if (getById("facultyEventCards") || getById("globalEventSelect")) {
    loadFacultyEvents();
}

if (getById("deploymentChecks")) {
    loadDeploymentReadiness();
}

const globalEventSelect = getById("globalEventSelect");
if (globalEventSelect) {
    globalEventSelect.addEventListener("change", () => {
        const eventId = String(globalEventSelect.value || "").trim();
        if (!eventId) {
            clearSelectedEvent();
            return;
        }
        syncEventInputs(eventId);
    });
}

const evaluationTeamSearchInput = getById("evaluationTeamSearch");
if (evaluationTeamSearchInput) {
    evaluationTeamSearchInput.addEventListener("input", () => {
        renderEvaluationTeams(state.loadedEvaluationTeams);
    });
}

const teamsSearchInput = getById("teamsSearchInput");
if (teamsSearchInput) {
    teamsSearchInput.addEventListener("input", () => {
        renderTeams(state.loadedStatusTeams);
    });
}

const popupOverlay = getById("facultyPopup");
if (popupOverlay) {
    popupOverlay.addEventListener("click", (event) => {
        if (event.target === popupOverlay) {
            closeFacultyPopup();
        }
    });
}

document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
        closeFacultyPopup();
    }
});

prefillEvaluationFromQuery();

window.addFieldRow = addFieldRow;
window.loadExistingForm = loadExistingForm;
window.loadProblemsForEvent = loadProblemsForEvent;
window.loadCriteria = loadCriteria;
window.prepareEvaluation = prepareEvaluation;
window.loadTeamsByEvent = loadTeamsByEvent;
window.loadProblemSelectionCounts = loadProblemSelectionCounts;
window.loadFacultyEvents = loadFacultyEvents;
window.loadDeploymentReadiness = loadDeploymentReadiness;
window.downloadExportReport = downloadExportReport;
window.closeFacultyPopup = closeFacultyPopup;
window.logout = logout;
