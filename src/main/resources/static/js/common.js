(() => {
    const faviconHref = "/favicon.ico?v=h3";
    let favicon = document.querySelector("link[rel='icon']");
    if (!favicon) {
        favicon = document.createElement("link");
        favicon.rel = "icon";
        document.head.appendChild(favicon);
    }
    favicon.type = "image/x-icon";
    favicon.href = faviconHref;
})();

function setSession(auth) {
    if (!auth || !auth.token) {
        localStorage.removeItem("token");
        localStorage.removeItem("role");
        localStorage.removeItem("userId");
        localStorage.removeItem("name");
        localStorage.removeItem("email");
        return;
    }
    localStorage.setItem("token", auth.token);
    localStorage.setItem("role", auth.role);
    localStorage.setItem("userId", auth.userId);
    localStorage.setItem("name", auth.name);
    localStorage.setItem("email", auth.email);
}

function getToken() {
    return localStorage.getItem("token");
}

function getRole() {
    return localStorage.getItem("role");
}

function logout() {
    localStorage.clear();
    window.location.href = "/login";
}

function ensureAuthenticated() {
    if (!getToken()) {
        window.location.href = "/login";
        return false;
    }
    return true;
}

function ensureRole(requiredRole) {
    if (!ensureAuthenticated()) {
        return false;
    }
    const role = getRole();
    if (role !== requiredRole) {
        alert("Access denied for role: " + role + ". Required: " + requiredRole);
        window.location.href = "/";
        return false;
    }
    return true;
}

async function apiFetch(url, options = {}, authRequired = true) {
    const headers = {
        "Content-Type": "application/json",
        ...(options.headers || {})
    };

    if (authRequired) {
        const token = getToken();
        if (token) {
            headers.Authorization = "Bearer " + token;
        }
    }

    const config = {
        ...options,
        headers
    };

    if (config.body && typeof config.body !== "string") {
        config.body = JSON.stringify(config.body);
    }

    const response = await fetch(url, config);
    const contentType = response.headers.get("content-type") || "";
    const isJson = contentType.includes("application/json");
    const payload = isJson ? await response.json() : await response.text();

    if (!response.ok) {
        const message = isJson ? payload.message || JSON.stringify(payload) : payload;
        throw new Error(message || "Request failed");
    }

    return payload;
}
