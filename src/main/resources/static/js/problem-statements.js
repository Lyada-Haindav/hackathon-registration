ensureAuthenticated();

const state = {
    events: [],
    selectedEventId: null,
    selectedTeam: null
};

function setMessage(text, isError = false) {
    const el = document.getElementById("problemPageMessage");
    el.textContent = text;
    el.classList.toggle("error", isError);
}

function renderEvents(events) {
    const container = document.getElementById("eventList");
    container.innerHTML = "";

    if (!events.length) {
        container.innerHTML = "<p>No active events found.</p>";
        return;
    }

    events.forEach((event) => {
        const card = document.createElement("div");
        card.className = "event-card";
        card.innerHTML = `
            <h3>${event.title}</h3>
            <p>${event.description}</p>
            <div class="meta-row">
                <span>${event.startDate} to ${event.endDate}</span>
                <button type="button" data-id="${event.id}">View Statements</button>
            </div>
        `;

        card.querySelector("button").addEventListener("click", () => selectEvent(event.id));
        container.appendChild(card);
    });
}

function renderSelectedTeamBox(team) {
    const box = document.getElementById("selectedTeamBox");
    const policyNote = document.getElementById("selectionPolicyNote");

    if (!team) {
        box.classList.add("hidden");
        box.innerHTML = "";
        if (policyNote) {
            policyNote.textContent = "Problem statement can be selected only once. Choose carefully before submitting.";
        }
        return;
    }

    box.classList.remove("hidden");
    box.innerHTML = `
        <h4>Your Team: ${team.teamName}</h4>
        <p>Team ID: ${team.teamId}</p>
        <p>Selected Problem: ${team.selectedProblemStatementTitle || "Not selected yet"}</p>
    `;
    if (policyNote) {
        policyNote.textContent = team.selectedProblemStatementId
            ? "Problem statement can be selected only once. Your team has already locked its selection."
            : "Problem statement can be selected only once. Choose carefully before submitting.";
    }
}

function renderProblemStatements(problems) {
    const list = document.getElementById("problemStatementsList");
    list.innerHTML = "";

    if (!problems.length) {
        list.innerHTML = "<p class='muted'>No released problem statements yet for this event.</p>";
        return;
    }

    const hasLockedSelection = Boolean(state.selectedTeam && state.selectedTeam.selectedProblemStatementId);

    problems.forEach((problem) => {
        const selected = state.selectedTeam && state.selectedTeam.selectedProblemStatementId === problem.id;

        const item = document.createElement("div");
        item.className = "mini-card";
        item.innerHTML = `
            <h4>${problem.title}</h4>
            <p>${problem.description}</p>
            <p class="muted">Released at: ${problem.releasedAt || "pending"}</p>
            <div class="row top-gap">
                <button type="button" class="${selected ? "ghost-btn" : ""}">Select for My Team</button>
            </div>
        `;

        const button = item.querySelector("button");
        if (!state.selectedTeam) {
            button.disabled = true;
            button.textContent = "Register team first";
        } else if (hasLockedSelection) {
            button.disabled = true;
            button.textContent = selected ? "Selected" : "Selection Locked";
            if (selected) {
                button.classList.add("ghost-btn");
            }
        } else {
            button.addEventListener("click", () => assignProblemStatement(problem));
        }

        list.appendChild(item);
    });
}

async function loadEvents() {
    try {
        const events = await apiFetch("/api/user/events");
        state.events = events;
        renderEvents(events);

        if (state.selectedEventId) {
            const selected = events.find((event) => event.id === state.selectedEventId);
            if (selected) {
                document.getElementById("selectedEventTitle").textContent = `Problem Statements - ${selected.title}`;
            }
        }
    } catch (error) {
        setMessage(error.message, true);
    }
}

async function loadTeamForEvent(eventId) {
    try {
        const team = await apiFetch(`/api/user/teams/${eventId}`);
        state.selectedTeam = team;
        renderSelectedTeamBox(team);
    } catch (_) {
        state.selectedTeam = null;
        renderSelectedTeamBox(null);
        setMessage("No registered team found for this event. Register team first to select a problem.", true);
    }
}

async function selectEvent(eventId) {
    const event = state.events.find((item) => item.id === eventId);
    if (!event) {
        return;
    }

    state.selectedEventId = eventId;
    document.getElementById("selectedEventTitle").textContent = `Problem Statements - ${event.title}`;

    await loadTeamForEvent(eventId);

    try {
        const problems = await apiFetch(`/api/user/problem-statements/${eventId}`);
        renderProblemStatements(problems);
        setMessage(`Loaded problem statements for ${event.title}`);
    } catch (error) {
        setMessage(error.message, true);
    }
}

async function assignProblemStatement(problem) {
    if (!state.selectedTeam) {
        setMessage("Register team first before selecting a problem statement.", true);
        return;
    }

    const confirmed = window.confirm(
        `Do you confirm selecting "${problem.title}"?\nYou cannot select another problem statement again.`
    );
    if (!confirmed) {
        setMessage("Selection cancelled. You can still choose another problem.");
        return;
    }

    try {
        const updatedTeam = await apiFetch(
            `/api/user/teams/${state.selectedTeam.teamId}/problem-statements/${problem.id}`,
            { method: "PUT" }
        );

        state.selectedTeam = updatedTeam;
        renderSelectedTeamBox(updatedTeam);

        const problems = await apiFetch(`/api/user/problem-statements/${state.selectedEventId}`);
        renderProblemStatements(problems);

        setMessage("Problem statement selected successfully. Selection is now locked.");
    } catch (error) {
        setMessage(error.message, true);
    }
}

setMessage("Select an event to view released problem statements. Problem statement selection is one-time.");
loadEvents();

window.loadEvents = loadEvents;
window.logout = logout;
