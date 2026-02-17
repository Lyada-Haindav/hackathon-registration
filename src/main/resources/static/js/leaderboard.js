let leaderboardInterval = null;

function renderLeaderboard(data) {
    document.getElementById("leaderboardOutput").textContent = JSON.stringify(data, null, 2);
    const list = document.getElementById("leaderboardList");
    if (!list) {
        return;
    }
    const ranked = [...data].sort((a, b) => {
        const scoreDelta = Number(b.totalScore || 0) - Number(a.totalScore || 0);
        if (scoreDelta !== 0) {
            return scoreDelta;
        }
        return String(a.teamName || "").localeCompare(String(b.teamName || ""));
    });
    list.innerHTML = "";
    ranked.forEach((entry, index) => {
        const li = document.createElement("li");
        li.textContent = `#${index + 1} ${entry.teamName} - ${Number(entry.totalScore || 0).toFixed(2)}`;
        list.appendChild(li);
    });
}

async function fetchLeaderboard(eventId) {
    if (!eventId) {
        return;
    }
    try {
        const data = await apiFetch(`/api/leaderboard/${eventId}`);
        renderLeaderboard(data);
    } catch (error) {
        document.getElementById("leaderboardOutput").textContent = error.message;
    }
}

function startLeaderboard() {
    stopLeaderboard();

    const eventId = document.getElementById("leaderboardEventId").value;
    if (!eventId) {
        return;
    }

    fetchLeaderboard(eventId);
    leaderboardInterval = setInterval(() => fetchLeaderboard(eventId), 5000);
}

function stopLeaderboard() {
    if (leaderboardInterval) {
        clearInterval(leaderboardInterval);
        leaderboardInterval = null;
    }
}

window.startLeaderboard = startLeaderboard;
window.stopLeaderboard = stopLeaderboard;

const eventFromQuery = new URLSearchParams(window.location.search).get("eventId");
if (eventFromQuery) {
    document.getElementById("leaderboardEventId").value = eventFromQuery;
    startLeaderboard();
}
