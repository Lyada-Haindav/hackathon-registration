const homeRoot = document.querySelector(".home-neo");

function escapeHtml(value) {
    return String(value || "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function formatDateRange(event) {
    const start = String(event.startDate || "").trim();
    const end = String(event.endDate || "").trim();

    if (start && end) {
        return `${start} to ${end}`;
    }
    if (start) {
        return start;
    }
    if (end) {
        return end;
    }
    return "Dates will be announced";
}

function posterMarkup(event) {
    const posterUrl = String(event.posterUrl || "").trim();
    if (posterUrl) {
        return `<img src="${escapeHtml(posterUrl)}" alt="${escapeHtml(event.title)} poster" loading="lazy">`;
    }

    const firstLetter = escapeHtml(String(event.title || "H").charAt(0).toUpperCase() || "H");
    return `<div class="home-event-poster-fallback">${firstLetter}</div>`;
}

function eventAbout(event) {
    const about = String(event.aboutEvent || "").trim();
    if (about) {
        return about;
    }
    const description = String(event.description || "").trim();
    if (description) {
        return description;
    }
    return "Details will be published by organiser.";
}

function eventStatusPill(event) {
    if (event.registrationOpen) {
        return `<span class="status-pill active">REGISTRATION OPEN</span>`;
    }
    return `<span class="status-pill pending">REGISTRATION CLOSED</span>`;
}

function renderHomeEvents(events) {
    const list = document.getElementById("homeEventsList");
    const message = document.getElementById("homeEventsMessage");
    if (!list || !message) {
        return;
    }

    list.innerHTML = "";
    const safeEvents = Array.isArray(events) ? events : [];

    if (!safeEvents.length) {
        message.textContent = "No events available right now.";
        return;
    }

    message.textContent = "";

    safeEvents.forEach((event) => {
        const card = document.createElement("article");
        card.className = "home-event-card";

        const registerClass = event.registrationOpen ? "home-event-register" : "home-event-register disabled";
        const registerHref = event.registrationOpen ? "/login" : "#";

        card.innerHTML = `
            <div class="home-event-poster">${posterMarkup(event)}</div>
            <div class="home-event-content">
                <div class="section-title-row">
                    <h3>${escapeHtml(event.title || "Hackathon Event")}</h3>
                    ${eventStatusPill(event)}
                </div>
                <p class="home-event-about">${escapeHtml(eventAbout(event))}</p>
                <p class="home-event-meta">${formatDateRange(event)}</p>
                <div class="home-event-actions">
                    <a class="${registerClass}" href="${registerHref}">Register</a>
                </div>
            </div>
        `;

        if (!event.registrationOpen) {
            card.querySelector(".home-event-register")?.addEventListener("click", (ev) => {
                ev.preventDefault();
            });
        }

        list.appendChild(card);
    });
}

async function loadHomeEvents() {
    const message = document.getElementById("homeEventsMessage");
    if (message) {
        message.textContent = "Loading events...";
    }

    try {
        const events = await apiFetch("/api/public/events", {}, false);
        renderHomeEvents(events);
    } catch (error) {
        if (message) {
            message.textContent = error.message || "Unable to load events right now.";
        }
    }
}

if (homeRoot) {
    requestAnimationFrame(() => homeRoot.classList.add("home-ready"));

    const updatePointerGlow = (x, y) => {
        const normalizedX = x / Math.max(window.innerWidth, 1);
        const normalizedY = y / Math.max(window.innerHeight, 1);
        document.documentElement.style.setProperty("--mx", `${(normalizedX * 100).toFixed(2)}%`);
        document.documentElement.style.setProperty("--my", `${(normalizedY * 100).toFixed(2)}%`);
    };

    homeRoot.addEventListener("pointermove", (event) => {
        updatePointerGlow(event.clientX, event.clientY);
    });

    homeRoot.addEventListener("pointerleave", () => {
        document.documentElement.style.setProperty("--mx", "50%");
        document.documentElement.style.setProperty("--my", "35%");
    });

    loadHomeEvents();
}
