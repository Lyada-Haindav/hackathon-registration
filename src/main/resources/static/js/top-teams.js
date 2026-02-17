ensureAuthenticated();

const state = {
    events: [],
    selectedEventId: null,
    timer: null
};

function setMessage(text, isError = false) {
    const el = document.getElementById("topTeamsMessage");
    if (!el) {
        return;
    }
    el.textContent = text;
    el.classList.toggle("error", isError);
}

function setupBackLink() {
    const link = document.getElementById("topTeamsBackLink");
    if (!link) {
        return;
    }

    const role = getRole();
    if (role === "FACULTY") {
        link.textContent = "Faculty Dashboard";
        link.href = "/faculty";
        return;
    }

    if (role === "USER") {
        link.textContent = "User Dashboard";
        link.href = "/user";
        return;
    }

    link.textContent = "Home";
    link.href = "/";
}

function renderEvents(events) {
    const container = document.getElementById("topTeamsEventList");
    if (!container) {
        setMessage("Leaderboard page failed to load. Refresh once.", true);
        return;
    }
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
                <button type="button" data-id="${event.id}">Show Leaderboard</button>
            </div>
        `;

        card.querySelector("button").addEventListener("click", () => selectEvent(event.id));
        container.appendChild(card);
    });
}

function renderLeaderboard(entries) {
    const container = document.getElementById("leaderboardGrid") || document.getElementById("topThreePodium");
    if (!container) {
        setMessage("Leaderboard container not found. Please refresh.", true);
        return;
    }
    container.innerHTML = "";

    if (!entries.length) {
        container.innerHTML = "<p class='muted'>No rankings yet. Faculty evaluation marks are pending.</p>";
        return;
    }

    const sortedEntries = [...entries].sort((a, b) => {
        const scoreDelta = Number(b.totalScore || 0) - Number(a.totalScore || 0);
        if (scoreDelta !== 0) {
            return scoreDelta;
        }
        return String(a.teamName || "").localeCompare(String(b.teamName || ""));
    });

    sortedEntries.forEach((entry, index) => {
        const card = document.createElement("div");
        card.className = `podium-card ${index < 3 ? `rank-${index + 1}` : ""}`.trim();
        card.innerHTML = `
            <p class="podium-rank">#${index + 1}</p>
            <h3>${entry.teamName}</h3>
            <p>Team ID: ${entry.teamId}</p>
            <p class="podium-score">Score: ${Number(entry.totalScore || 0).toFixed(2)}</p>
        `;
        container.appendChild(card);
    });
}

async function loadEvents() {
    try {
        const role = getRole();
        const allEvents = role === "FACULTY"
            ? await apiFetch("/api/faculty/events")
            : await apiFetch("/api/user/events");
        const events = role === "FACULTY"
            ? allEvents
            : allEvents.filter((event) => Boolean(event.leaderboardVisible));

        state.events = events;
        renderEvents(events);

        if (!events.length && role === "USER") {
            setMessage("Leaderboard is not published yet by faculty for active events.");
        }
    } catch (error) {
        setMessage(error.message, true);
    }
}

async function loadLeaderboard() {
    if (!state.selectedEventId) {
        return;
    }

    try {
        const entries = await apiFetch(`/api/leaderboard/${state.selectedEventId}`);
        renderLeaderboard(entries);
        if (!entries.length) {
            setMessage("Waiting for faculty evaluation marks.");
        } else {
            setMessage("Leaderboard updated in real time (high to low scores).");
        }
    } catch (error) {
        setMessage(error.message, true);
    }
}

function selectEvent(eventId) {
    state.selectedEventId = eventId;
    const event = state.events.find((item) => item.id === eventId);
    if (event) {
        const titleEl = document.getElementById("podiumTitle");
        if (titleEl) {
            titleEl.textContent = `Leaderboard - ${event.title}`;
        }
    }

    if (state.timer) {
        clearInterval(state.timer);
    }

    loadLeaderboard();
    state.timer = setInterval(loadLeaderboard, 5000);
}

setMessage("Select an event to view leaderboard by team names and scores (high to low).");
setupBackLink();
loadEvents();

window.loadEvents = loadEvents;
window.logout = logout;
