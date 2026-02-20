ensureAuthenticated();

const MIN_TEAM_MEMBERS = 1;
const MAX_TEAM_MEMBERS = 4;

const state = {
    step: 1,
    events: [],
    selectedEvent: null,
    formFields: [],
    registeredTeam: null,
    myTeams: [],
    registrationLocked: false,
    leaderboardAvailable: false
};
const PHONEPE_PENDING_KEY = "phonepePendingPayment";

function userEmail() {
    return localStorage.getItem("email") || "user";
}

function getSplitMembers(event) {
    const split = Number(event && event.feeSplitMembers);
    return Number.isFinite(split) && split > 0 ? split : 4;
}

function getPerMemberFee(event) {
    const perMemberFromApi = Number(event && event.feePerMember);
    if (Number.isFinite(perMemberFromApi) && perMemberFromApi > 0) {
        return perMemberFromApi;
    }

    const totalFee = Number(event && event.registrationFee);
    if (!Number.isFinite(totalFee) || totalFee <= 0) {
        return 0;
    }

    return totalFee / getSplitMembers(event);
}

function getPayableFee(event, memberCount) {
    return getPerMemberFee(event) * Math.max(Number(memberCount) || 1, 1);
}

function formatInr(amount) {
    const value = Number(amount);
    const safe = Number.isFinite(value) ? value : 0;
    return `INR ${safe.toFixed(2)}`;
}

function getSafeTelegramUrl(rawUrl) {
    const raw = String(rawUrl || "").trim();
    if (!raw) {
        return null;
    }

    let candidate = raw;
    if (candidate.startsWith("t.me/") || candidate.startsWith("telegram.me/")) {
        candidate = `https://${candidate}`;
    }

    try {
        const parsed = new URL(candidate);
        if (!["http:", "https:"].includes(parsed.protocol)) {
            return null;
        }
        const host = String(parsed.hostname || "").toLowerCase();
        if (!host.includes("t.me") && !host.includes("telegram.me")) {
            return null;
        }
        return parsed.toString();
    } catch (_) {
        return null;
    }
}

function getEventForTeam(team) {
    if (!team || !team.eventId) {
        return null;
    }
    return state.events.find((event) => event.id === team.eventId) || null;
}

function renderTelegramJoin(team) {
    const wrap = document.getElementById("telegramJoinWrap");
    if (!wrap) {
        return;
    }

    const paymentStatus = String(team && team.paymentStatus || "").toUpperCase();
    if (paymentStatus !== "SUCCESS") {
        wrap.classList.add("hidden");
        wrap.innerHTML = "";
        return;
    }

    const event = getEventForTeam(team);
    const telegramUrl = getSafeTelegramUrl(event && event.telegramGroupLink);
    if (!telegramUrl) {
        wrap.classList.add("hidden");
        wrap.innerHTML = "";
        return;
    }

    wrap.innerHTML = `
        <a class="telegram-join-link" href="${telegramUrl}" target="_blank" rel="noopener noreferrer" aria-label="Join Telegram Group">
            <span class="telegram-icon-pulse" aria-hidden="true">
                <svg viewBox="0 0 24 24" focusable="false">
                    <path d="M9.2 15.7l-.4 5.4c.6 0 .9-.3 1.3-.6l3.1-3 6.4 4.7c1.2.7 2 .3 2.3-1.1l4.2-19.6h0c.4-1.7-.6-2.4-1.7-2L.5 9.7c-1.6.6-1.6 1.5-.3 1.9l6.1 1.9L20.7 4c.7-.4 1.4-.2.8.4"/>
                </svg>
            </span>
            <span>Join Telegram Group</span>
        </a>
    `;
    wrap.classList.remove("hidden");
}

function setMessage(text, isError = false) {
    const el = document.getElementById("userMessage");
    el.textContent = text;
    el.classList.toggle("error", isError);
}

function renderLivePaymentPreview(memberCount) {
    const amountEl = document.getElementById("livePaymentAmount");
    if (!amountEl) {
        return;
    }

    const event = state.selectedEvent;
    const count = Math.max(Number(memberCount) || 1, 1);
    const perMemberFee = getPerMemberFee(event);
    const amount = getPayableFee(event, count);
    amountEl.textContent = `Payable Amount: ${formatInr(amount)} (${formatInr(perMemberFee)} x ${count})`;
}

function renderPhonePePaymentPanel(team) {
    const amountEl = document.getElementById("phonePeAmountSummary");
    if (!amountEl) {
        return;
    }

    const event = getEventForTeam(team) || state.selectedEvent;
    const teamSize = Math.max(Number(team && team.teamSize) || 1, 1);
    const perMemberFee = getPerMemberFee(event);
    const amount = getPayableFee(event, teamSize);
    amountEl.textContent = `Payable Amount: ${formatInr(amount)} (${formatInr(perMemberFee)} x ${teamSize} member${teamSize > 1 ? "s" : ""})`;
}

function setStep(stepNumber) {
    if (state.registrationLocked && stepNumber < 4) {
        stepNumber = 4;
    }

    state.step = stepNumber;

    document.querySelectorAll(".wizard-panel").forEach((panel) => panel.classList.add("hidden"));
    document.getElementById(`step-${stepNumber}`).classList.remove("hidden");

    document.querySelectorAll(".step").forEach((stepEl) => {
        const stepId = Number(stepEl.dataset.step);
        stepEl.classList.toggle("active", stepId === stepNumber);
        stepEl.classList.toggle("completed", stepId < stepNumber);
    });

    if (stepNumber === 3) {
        renderReview();
    }
}

function nextStep() {
    if (state.registrationLocked) {
        setMessage("You already registered one team. Only payment/status actions are available.", true);
        return;
    }

    if (!validateStep(state.step)) {
        return;
    }

    if (state.step < 4) {
        setStep(state.step + 1);
    }
}

function prevStep() {
    if (state.registrationLocked) {
        return;
    }

    if (state.step > 1) {
        setStep(state.step - 1);
    }
}

function validateStep(step) {
    if (step === 1 && !state.selectedEvent) {
        setMessage("Select an event first.", true);
        return false;
    }

    if (step === 2) {
        const members = collectMembers();
        if (members.length < MIN_TEAM_MEMBERS) {
            setMessage(`Add at least ${MIN_TEAM_MEMBERS} team member.`, true);
            return false;
        }

        if (members.length > MAX_TEAM_MEMBERS) {
            setMessage(`You can add up to ${MAX_TEAM_MEMBERS} members only.`, true);
            return false;
        }

        const leaders = members.filter((member) => member.leader).length;
        if (leaders !== 1) {
            setMessage("Exactly one member must be selected as team leader.", true);
            return false;
        }

        const emailSet = new Set();
        for (const member of members) {
            if (emailSet.has(member.email)) {
                setMessage("Duplicate member email detected.", true);
                return false;
            }
            emailSet.add(member.email);
        }

        const requiredFieldMissing = Array.from(document.querySelectorAll("#dynamicFieldsContainer [data-required='true']"))
            .some((input) => !String(input.value || "").trim());

        if (requiredFieldMissing) {
            setMessage("Fill all required registration form fields.", true);
            return false;
        }

        saveDraft();
    }

    return true;
}

function draftKey(eventId) {
    return `registrationDraft:${userEmail()}:${eventId}`;
}

function saveDraft() {
    if (!state.selectedEvent || state.registrationLocked) {
        return;
    }

    const draft = {
        formResponses: collectFormResponses(),
        members: collectMembers()
    };

    localStorage.setItem(draftKey(state.selectedEvent.id), JSON.stringify(draft));
    setMessage("Draft saved.");
}

function loadDraft(eventId) {
    const raw = localStorage.getItem(draftKey(eventId));
    if (!raw) {
        renderMembersFromDraft([]);
        return;
    }

    try {
        const draft = JSON.parse(raw);
        renderMembersFromDraft(draft.members || []);
        setTimeout(() => applyFormDraftValues(draft.formResponses || {}), 0);
        setMessage("Saved draft loaded for this event.");
    } catch (_) {
        localStorage.removeItem(draftKey(eventId));
    }
}

function applyFormDraftValues(values) {
    document.querySelectorAll("#dynamicFieldsContainer [data-key]").forEach((input) => {
        const key = input.dataset.key;
        if (Object.prototype.hasOwnProperty.call(values, key)) {
            input.value = values[key];
        }
    });
}

function renderEvents(events) {
    const container = document.getElementById("eventCards");
    container.innerHTML = "";

    if (!events.length) {
        container.innerHTML = "<p>No active events available right now.</p>";
        return;
    }

    for (const event of events) {
        const card = document.createElement("div");
        card.className = "event-card";
        const perMemberFee = getPerMemberFee(event);
        const registrationOpen = Boolean(event.registrationOpen);
        card.innerHTML = `
            <h3>${event.title}</h3>
            <p>${event.description}</p>
            <div class="meta-row">
                <span>${event.startDate} to ${event.endDate}</span>
                <span class="price">${formatInr(perMemberFee)}/member</span>
            </div>
            <p class="muted">Registration: <strong>${registrationOpen ? "Open" : "Closed"}</strong></p>
            <p class="muted">Fee per member: ${formatInr(perMemberFee)} (set by organizer event fee).</p>
            <button type="button" data-event-id="${event.id}">${registrationOpen ? "Select Event" : "Registration Closed"}</button>
        `;

        const selectButton = card.querySelector("button");
        selectButton.addEventListener("click", () => selectEvent(event.id));
        if (state.registrationLocked || !registrationOpen) {
            selectButton.disabled = true;
        }

        container.appendChild(card);
    }
}

function updateLeaderboardLinks(events) {
    const canViewLeaderboard = events.some((event) => Boolean(event.leaderboardVisible));
    state.leaderboardAvailable = canViewLeaderboard;

    const headerLink = document.getElementById("headerLeaderboardLink");
    const sideLink = document.getElementById("sideLeaderboardLink");

    if (headerLink) {
        headerLink.classList.toggle("hidden", !canViewLeaderboard);
    }
    if (sideLink) {
        sideLink.classList.toggle("hidden", !canViewLeaderboard);
    }
}

function renderSelectedEvent(event) {
    const selectedCard = document.getElementById("selectedEventCard");
    const perMemberFee = getPerMemberFee(event);
    const registrationStatus = event.registrationOpen ? "Open" : "Closed";
    selectedCard.classList.remove("hidden");
    selectedCard.innerHTML = `
        <h3>Selected: ${event.title}</h3>
        <p>${event.description}</p>
        <div class="meta-row">
            <span>${event.startDate} to ${event.endDate}</span>
            <strong>Fee: ${formatInr(perMemberFee)} per member</strong>
        </div>
        <p class="muted">Registration Status: ${registrationStatus}</p>
        <p class="muted">Total payable updates based on team size (1 to 4 members).</p>
    `;
}

function renderDynamicFields(fields) {
    const container = document.getElementById("dynamicFieldsContainer");
    container.innerHTML = "";

    if (!fields.length) {
        container.innerHTML = "<p class='muted'>No extra dynamic fields configured by organizer for this event.</p>";
        return;
    }

    fields.forEach((field) => {
        const wrapper = document.createElement("label");
        wrapper.className = "form-unit";
        wrapper.innerHTML = `<span>${field.label}${field.required ? " *" : ""}</span>`;

        let input;
        if (field.type === "TEXTAREA") {
            input = document.createElement("textarea");
            input.rows = 3;
        } else if (field.type === "SELECT") {
            input = document.createElement("select");
            const placeholder = document.createElement("option");
            placeholder.value = "";
            placeholder.textContent = "Select an option";
            input.appendChild(placeholder);
            (field.options || []).forEach((opt) => {
                const option = document.createElement("option");
                option.value = opt;
                option.textContent = opt;
                input.appendChild(option);
            });
        } else {
            input = document.createElement("input");
            input.type = mapInputType(field.type);
        }

        input.dataset.key = field.key;
        input.dataset.required = String(!!field.required);
        input.required = !!field.required;
        input.disabled = state.registrationLocked;
        input.addEventListener("input", () => {
            if (state.selectedEvent && !state.registrationLocked) {
                localStorage.setItem(
                    draftKey(state.selectedEvent.id),
                    JSON.stringify({
                        formResponses: collectFormResponses(),
                        members: collectMembers()
                    })
                );
            }
        });

        wrapper.appendChild(input);
        container.appendChild(wrapper);
    });
}

function mapInputType(fieldType) {
    switch (fieldType) {
        case "NUMBER":
            return "number";
        case "EMAIL":
            return "email";
        case "DATE":
            return "date";
        default:
            return "text";
    }
}

function memberTemplate(index, member = {}) {
    return `
        <div class="member-card" data-index="${index}">
            <div class="member-header">
                <h4>Member ${index + 1}</h4>
                <button type="button" class="ghost-btn small remove-member">Remove</button>
            </div>
            <div class="member-grid">
                <label>Name<input class="member-name" value="${member.name || ""}" required></label>
                <label>Email<input type="email" class="member-email" value="${member.email || ""}" required></label>
                <label>Phone<input class="member-phone" value="${member.phone || ""}" required></label>
                <label>College<input class="member-college" value="${member.college || ""}" required></label>
            </div>
            <label class="leader-flag">
                <input type="radio" name="teamLeader" class="member-leader" ${member.leader ? "checked" : ""}>
                Set as Team Leader
            </label>
        </div>
    `;
}

function updateMemberControls() {
    const cards = document.querySelectorAll("#membersContainer .member-card");
    const count = cards.length;

    const addButton = document.getElementById("addMemberBtn");
    if (addButton) {
        const reachedMax = count >= MAX_TEAM_MEMBERS;
        addButton.disabled = state.registrationLocked || reachedMax;
        addButton.textContent = reachedMax ? `Max ${MAX_TEAM_MEMBERS} Members` : "Add Member";
    }

    const hint = document.getElementById("memberLimitHint");
    if (hint) {
        hint.textContent = `Team size: ${count}. Minimum ${MIN_TEAM_MEMBERS} member and maximum ${MAX_TEAM_MEMBERS} members.`;
    }

    const removeButtons = document.querySelectorAll(".remove-member");
    removeButtons.forEach((button) => {
        button.disabled = state.registrationLocked || count <= MIN_TEAM_MEMBERS;
    });
}

function bindMemberEvents() {
    document.querySelectorAll(".remove-member").forEach((btn) => {
        btn.onclick = () => {
            if (state.registrationLocked) {
                return;
            }
            const cards = document.querySelectorAll("#membersContainer .member-card");
            if (cards.length <= MIN_TEAM_MEMBERS) {
                setMessage(`Team must have at least ${MIN_TEAM_MEMBERS} member.`, true);
                return;
            }
            btn.closest(".member-card").remove();
            reindexMembers();
            saveDraft();
        };
        btn.disabled = state.registrationLocked;
    });

    document.querySelectorAll("#membersContainer input").forEach((input) => {
        input.disabled = state.registrationLocked;
        input.oninput = () => {
            if (state.selectedEvent && !state.registrationLocked) {
                saveDraft();
            }
            renderLivePaymentPreview(collectMembers().length);
        };
    });

    updateMemberControls();
}

function reindexMembers() {
    const current = collectMembers();
    renderMembersFromDraft(current);
}

function renderMembersFromDraft(members) {
    const container = document.getElementById("membersContainer");
    container.innerHTML = "";

    let safeMembers = Array.isArray(members) ? members.slice(0, MAX_TEAM_MEMBERS) : [];

    if (!safeMembers.length) {
        safeMembers = [{ leader: true }];
    }

    const hasLeader = safeMembers.some((member) => Boolean(member.leader));
    if (!hasLeader) {
        safeMembers[0] = { ...safeMembers[0], leader: true };
    }

    safeMembers.forEach((member, index) => {
        container.insertAdjacentHTML("beforeend", memberTemplate(index, member));
    });
    bindMemberEvents();
    renderLivePaymentPreview(safeMembers.length);
}

function addMemberRow() {
    if (state.registrationLocked) {
        return;
    }

    const container = document.getElementById("membersContainer");
    const count = container.querySelectorAll(".member-card").length;
    if (count >= MAX_TEAM_MEMBERS) {
        setMessage(`You can add up to ${MAX_TEAM_MEMBERS} members only.`, true);
        updateMemberControls();
        return;
    }

    const index = count;
    container.insertAdjacentHTML("beforeend", memberTemplate(index, { leader: count === 0 }));
    bindMemberEvents();
    saveDraft();
    renderLivePaymentPreview(count + 1);
}

function collectFormResponses() {
    const responses = {};
    document.querySelectorAll("#dynamicFieldsContainer [data-key]").forEach((el) => {
        responses[el.dataset.key] = el.value;
    });
    return responses;
}

function collectMembers() {
    const rows = document.querySelectorAll("#membersContainer .member-card");
    return Array.from(rows).map((row) => ({
        name: row.querySelector(".member-name").value.trim(),
        email: row.querySelector(".member-email").value.trim(),
        phone: row.querySelector(".member-phone").value.trim(),
        college: row.querySelector(".member-college").value.trim(),
        leader: row.querySelector(".member-leader").checked
    }));
}

function renderReview() {
    const container = document.getElementById("reviewContainer");
    const members = collectMembers();
    const responses = collectFormResponses();
    const perMemberFee = getPerMemberFee(state.selectedEvent);
    const payable = getPayableFee(state.selectedEvent, members.length);

    container.innerHTML = `
        <section>
            <h3>Event</h3>
            <p><strong>${state.selectedEvent.title}</strong></p>
            <p>${state.selectedEvent.description}</p>
            <p>Fee Per Member: ${formatInr(perMemberFee)}</p>
            <p>Payable Amount: ${formatInr(payable)}</p>
        </section>
        <section>
            <h3>Team Members</h3>
            <ul>
                ${members.map((member) => `<li>${member.name} (${member.email})${member.leader ? " - Leader" : ""}</li>`).join("")}
            </ul>
        </section>
        <section>
            <h3>Form Responses</h3>
            <ul>
                ${Object.entries(responses).map(([key, value]) => `<li><strong>${key}</strong>: ${value || "-"}</li>`).join("")}
            </ul>
        </section>
    `;
}

async function loadEvents() {
    try {
        const response = await apiFetch("/api/user/events");
        state.events = response;
        renderEvents(response);
        updateLeaderboardLinks(response);

        if (state.selectedEvent) {
            const selected = response.find((event) => event.id === state.selectedEvent.id);
            if (selected) {
                state.selectedEvent = selected;
                renderSelectedEvent(selected);
            }
        }

        if (state.registeredTeam && String(state.registeredTeam.paymentStatus || "").toUpperCase() === "SUCCESS") {
            renderTelegramJoin(state.registeredTeam);
        }
    } catch (error) {
        setMessage(error.message, true);
    }
}

async function selectEvent(eventId) {
    if (state.registrationLocked) {
        setMessage("Only one team is allowed. You already have a registered team.", true);
        return;
    }

    const event = state.events.find((item) => item.id === eventId);
    if (!event) {
        return;
    }

    state.selectedEvent = event;
    renderSelectedEvent(event);

    try {
        const form = await apiFetch(`/api/user/forms/${eventId}`);
        state.formFields = form.fields || [];
    } catch (error) {
        state.formFields = [];
        setMessage(`Unable to load registration form for this event: ${error.message}`, true);
    }

    renderDynamicFields(state.formFields);
    loadDraft(eventId);
    setMessage(`Event selected: ${event.title}`);
}

function renderTeamConfirmation(team) {
    const isPaid = String(team.paymentStatus || "").toUpperCase() === "SUCCESS";
    document.getElementById("teamConfirmation").innerHTML = `
        <div class="confirm-card">
            <h3>${team.teamName}</h3>
            <p>Team ID: <strong>${team.teamId}</strong></p>
            <p>Team Size: ${team.teamSize}</p>
            <p>Status: ${team.paymentStatus}</p>
        </div>
    `;

    if (isPaid) {
        showPaymentSuccessView(team, "Your registration is confirmed.");
    } else {
        showPaymentFlowView(team);
    }
}

function showPaymentFlowView(team) {
    document.getElementById("paymentFlowView").classList.remove("hidden");
    document.getElementById("paymentSuccessView").classList.add("hidden");
    renderPhonePePaymentPanel(team || state.registeredTeam);
    renderTelegramJoin(null);
}

function showPaymentSuccessView(team, message) {
    document.getElementById("paymentFlowView").classList.add("hidden");
    document.getElementById("paymentSuccessView").classList.remove("hidden");
    document.getElementById("thankYouLine").textContent = `Thank you! ${message}`;
    document.getElementById("thankYouTeam").textContent = team
        ? `Team: ${team.teamName} (${team.teamId})`
        : "";
    renderTelegramJoin(team);
}

async function submitRegistration() {
    if (state.registrationLocked) {
        setMessage("You already have one registered team.", true);
        return;
    }

    if (!state.selectedEvent) {
        setMessage("Select an event before submitting.", true);
        return;
    }

    const members = collectMembers();
    if (members.length < MIN_TEAM_MEMBERS || members.length > MAX_TEAM_MEMBERS) {
        setMessage(`Team members should be between ${MIN_TEAM_MEMBERS} and ${MAX_TEAM_MEMBERS}.`, true);
        return;
    }

    const body = {
        eventId: state.selectedEvent.id,
        members,
        formResponses: collectFormResponses()
    };

    try {
        const response = await apiFetch("/api/user/teams/register", { method: "POST", body });
        state.registeredTeam = response;
        document.getElementById("paymentTeamId").value = response.teamId;
        renderTeamConfirmation(response);

        localStorage.removeItem(draftKey(state.selectedEvent.id));
        setMessage(`Team registered successfully: ${response.teamName}`);
        setStep(4);
        loadMyTeams();
    } catch (error) {
        setMessage(error.message, true);
    }
}

async function startPhonePePayment() {
    const teamId = document.getElementById("paymentTeamId").value || (state.registeredTeam && state.registeredTeam.teamId);
    if (!teamId) {
        setMessage("No team selected for payment.", true);
        return;
    }

    try {
        const response = await apiFetch(`/api/user/payments/${teamId}/order`, { method: "POST" });
        if (!response || !response.redirectUrl) {
            if (Number(response && response.amount) <= 0) {
                await checkPhonePePaymentStatus(teamId);
                return;
            }
            setMessage("Unable to start PhonePe checkout. Please try again.", true);
            return;
        }

        localStorage.setItem(PHONEPE_PENDING_KEY, JSON.stringify({
            teamId,
            merchantTransactionId: response.orderId
        }));
        setMessage("Redirecting to PhonePe checkout...");
        window.location.href = response.redirectUrl;
    } catch (error) {
        setMessage(error.message, true);
    }
}

async function checkPhonePePaymentStatus(explicitTeamId = null, explicitTxnId = null) {
    const teamId = explicitTeamId || document.getElementById("paymentTeamId").value || (state.registeredTeam && state.registeredTeam.teamId);
    if (!teamId) {
        setMessage("No team selected for payment status check.", true);
        return;
    }

    let merchantTransactionId = explicitTxnId || "";
    if (!merchantTransactionId) {
        const pendingRaw = localStorage.getItem(PHONEPE_PENDING_KEY);
        if (pendingRaw) {
            try {
                const pending = JSON.parse(pendingRaw);
                if (pending && pending.teamId === teamId && pending.merchantTransactionId) {
                    merchantTransactionId = pending.merchantTransactionId;
                }
            } catch (_) {
                // ignore invalid local storage payload
            }
        }
    }

    try {
        const query = merchantTransactionId
            ? `?merchantTransactionId=${encodeURIComponent(merchantTransactionId)}`
            : "";
        const response = await apiFetch(`/api/user/payments/${teamId}/phonepe/status${query}`);

        const latestTeams = await apiFetch("/api/user/teams");
        state.myTeams = latestTeams;
        const team = latestTeams.find((item) => item.teamId === teamId) || state.registeredTeam;
        state.registeredTeam = team || state.registeredTeam;
        renderMyTeams(latestTeams);
        applySingleTeamPolicy();

        const status = String(response && response.paymentStatus || "").toUpperCase();
        if (status === "SUCCESS") {
            showPaymentSuccessView(state.registeredTeam, "Your PhonePe payment was successful.");
            localStorage.removeItem(PHONEPE_PENDING_KEY);
        } else if (status === "FAILED") {
            showPaymentFlowView(state.registeredTeam);
        }

        setMessage(response.message || "Payment status updated.");
    } catch (error) {
        setMessage(error.message, true);
    }
}

async function handlePhonePeReturn() {
    const params = new URLSearchParams(window.location.search);
    const teamId = params.get("paymentTeamId");
    const txnId = params.get("phonepeTxnId");
    if (!teamId || !txnId) {
        return;
    }

    await checkPhonePePaymentStatus(teamId, txnId);
    params.delete("paymentTeamId");
    params.delete("phonepeTxnId");
    const cleanedQuery = params.toString();
    const cleanedUrl = cleanedQuery ? `${window.location.pathname}?${cleanedQuery}` : window.location.pathname;
    window.history.replaceState({}, "", cleanedUrl);
}

function renderMyTeams(teams) {
    const container = document.getElementById("myTeamsList");
    container.innerHTML = "";

    if (!teams.length) {
        container.innerHTML = "<p class='muted'>No teams registered yet.</p>";
        return;
    }

    teams.forEach((team) => {
        const isPaymentSuccess = String(team.paymentStatus || "").toUpperCase() === "SUCCESS";
        const card = document.createElement("div");
        card.className = "mini-card";
        card.innerHTML = `
            <h4>${team.teamName}</h4>
            <p>Team ID: ${team.teamId}</p>
            <p>Size: ${team.teamSize}</p>
            <p>Payment: <strong>${team.paymentStatus}</strong></p>
            <p>Problem: ${team.selectedProblemStatementTitle || "Not selected"}</p>
            <p>Score: ${Number(team.totalScore || 0).toFixed(2)}</p>
            ${isPaymentSuccess ? "<p class='muted'>Payment completed.</p>" : `<button type="button" class="ghost-btn small" data-team-id="${team.teamId}">Pay with PhonePe</button>`}
        `;

        const payButton = card.querySelector("button");
        if (payButton) {
            payButton.addEventListener("click", () => {
                document.getElementById("paymentTeamId").value = team.teamId;
                state.registeredTeam = team;
                renderTeamConfirmation(team);
                setStep(4);
            });
        }

        container.appendChild(card);
    });
}

function applySingleTeamPolicy() {
    const note = document.getElementById("registrationPolicyNote");
    const hasTeam = state.myTeams.length > 0;

    state.registrationLocked = hasTeam;

    if (hasTeam) {
        const team = state.myTeams[0];
        state.registeredTeam = team;
        note.classList.remove("hidden");
        note.textContent = "One-team policy active: you already registered a team. You can only track status and complete payment.";
        document.getElementById("paymentTeamId").value = team.teamId;
        renderTeamConfirmation(team);
        setStep(4);
    } else {
        note.classList.add("hidden");
        note.textContent = "";
        showPaymentFlowView(null);
    }
}

async function loadMyTeams() {
    try {
        const teams = await apiFetch("/api/user/teams");
        state.myTeams = teams;
        renderMyTeams(teams);
        applySingleTeamPolicy();
    } catch (error) {
        setMessage(error.message, true);
    }
}

async function init() {
    setMessage(`Logged in as ${userEmail()}`);
    setStep(1);
    await loadEvents();
    await loadMyTeams();
    renderMembersFromDraft([]);
    await handlePhonePeReturn();
}

init();

window.nextStep = nextStep;
window.prevStep = prevStep;
window.loadEvents = loadEvents;
window.addMemberRow = addMemberRow;
window.submitRegistration = submitRegistration;
window.startPhonePePayment = startPhonePePayment;
window.checkPhonePePaymentStatus = checkPhonePePaymentStatus;
window.loadMyTeams = loadMyTeams;
window.saveDraft = saveDraft;
window.logout = logout;
