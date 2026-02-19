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

function setMessage(text, isError = false) {
    const el = document.getElementById("userMessage");
    el.textContent = text;
    el.classList.toggle("error", isError);
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
        const fee = Number(event.registrationFee || 0);
        const perMemberFee = getPerMemberFee(event);
        const registrationOpen = Boolean(event.registrationOpen);
        card.innerHTML = `
            <h3>${event.title}</h3>
            <p>${event.description}</p>
            <div class="meta-row">
                <span>${event.startDate} to ${event.endDate}</span>
                <span class="price">INR ${perMemberFee.toFixed(2)}/member</span>
            </div>
            <p class="muted">Registration: <strong>${registrationOpen ? "Open" : "Closed"}</strong></p>
            <p class="muted">Configured total fee: INR ${fee.toFixed(2)}</p>
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
    const fee = Number(event.registrationFee || 0);
    const perMemberFee = getPerMemberFee(event);
    const splitMembers = getSplitMembers(event);
    const registrationStatus = event.registrationOpen ? "Open" : "Closed";
    selectedCard.classList.remove("hidden");
    selectedCard.innerHTML = `
        <h3>Selected: ${event.title}</h3>
        <p>${event.description}</p>
        <div class="meta-row">
            <span>${event.startDate} to ${event.endDate}</span>
            <strong>Fee: INR ${perMemberFee.toFixed(2)} per member</strong>
        </div>
        <p class="muted">Registration Status: ${registrationStatus}</p>
        <p class="muted">Configured as INR ${fee.toFixed(2)} for ${splitMembers} members.</p>
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

    container.innerHTML = `
        <section>
            <h3>Event</h3>
            <p><strong>${state.selectedEvent.title}</strong></p>
            <p>${state.selectedEvent.description}</p>
            <p>Total Event Fee: INR ${Number(state.selectedEvent.registrationFee || 0).toFixed(2)}</p>
            <p>Per Member Fee: INR ${getPerMemberFee(state.selectedEvent).toFixed(2)}</p>
            <p>Payable Amount: INR ${getPayableFee(state.selectedEvent, members.length).toFixed(2)}</p>
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
        showPaymentFlowView();
    }
}

function showPaymentFlowView() {
    document.getElementById("paymentFlowView").classList.remove("hidden");
    document.getElementById("paymentSuccessView").classList.add("hidden");
}

function showPaymentSuccessView(team, message) {
    document.getElementById("paymentFlowView").classList.add("hidden");
    document.getElementById("paymentSuccessView").classList.remove("hidden");
    document.getElementById("thankYouLine").textContent = `Thank you! ${message}`;
    document.getElementById("thankYouTeam").textContent = team
        ? `Team: ${team.teamName} (${team.teamId})`
        : "";
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

async function createOrder() {
    const teamId = document.getElementById("paymentTeamId").value || (state.registeredTeam && state.registeredTeam.teamId);
    if (!teamId) {
        setMessage("No team selected for payment.", true);
        return;
    }

    try {
        const order = await apiFetch(`/api/user/payments/${teamId}/order`, { method: "POST" });

        if (String(order.orderId || "").startsWith("mock_order_")) {
            setMessage("Mock payment mode active. Completing payment automatically.");
            await verifyPayment(order.teamId, {
                razorpay_order_id: order.orderId,
                razorpay_payment_id: `mock_pay_${Date.now()}`,
                razorpay_signature: "mock_signature"
            });
            return;
        }

        if (order.orderId === "FREE_REGISTRATION") {
            setMessage("Thank you! Registration confirmed.");
            loadMyTeams();
            return;
        }

        if (window.Razorpay) {
            openRazorpayCheckout(order);
            return;
        }

        setMessage("Razorpay script unavailable. Copy order data for manual verification.", true);
    } catch (error) {
        setMessage(error.message, true);
    }
}

function openRazorpayCheckout(order) {
    const options = {
        key: order.keyId,
        amount: Math.round(Number(order.amount) * 100),
        currency: order.currency,
        name: "KLH hackathon registration",
        description: "Team Registration Fee",
        order_id: order.orderId,
        handler: async function (response) {
            await verifyPayment(order.teamId, response);
        },
        theme: { color: "#D35400" }
    };

    const razorpay = new Razorpay(options);
    razorpay.on("payment.failed", () => setMessage("Payment failed. Please retry.", true));
    razorpay.open();
}

async function verifyPayment(teamId, razorpayResponse) {
    const payload = {
        razorpayOrderId: razorpayResponse.razorpay_order_id,
        razorpayPaymentId: razorpayResponse.razorpay_payment_id,
        razorpaySignature: razorpayResponse.razorpay_signature
    };

    try {
        const response = await apiFetch(`/api/user/payments/${teamId}/verify`, {
            method: "POST",
            body: payload
        });

        if (String(response.paymentStatus || "").toUpperCase() === "SUCCESS") {
            const team = state.registeredTeam || state.myTeams.find((item) => item.teamId === teamId);
            showPaymentSuccessView(team, "Your payment was successful.");
            setMessage("Thank you! Payment successful.");
        } else {
            setMessage(response.message);
        }
        loadMyTeams();
    } catch (error) {
        setMessage(error.message, true);
    }
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
            ${isPaymentSuccess ? "<p class='muted'>Payment completed.</p>" : `<button type="button" class="ghost-btn small" data-team-id="${team.teamId}">Pay / Retry</button>`}
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
        showPaymentFlowView();
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

setMessage(`Logged in as ${userEmail()}`);
setStep(1);
loadEvents();
loadMyTeams();
renderMembersFromDraft([]);

window.nextStep = nextStep;
window.prevStep = prevStep;
window.loadEvents = loadEvents;
window.addMemberRow = addMemberRow;
window.submitRegistration = submitRegistration;
window.createOrder = createOrder;
window.loadMyTeams = loadMyTeams;
window.saveDraft = saveDraft;
window.logout = logout;
