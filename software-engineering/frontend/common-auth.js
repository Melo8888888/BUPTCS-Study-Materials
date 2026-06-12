(function () {
  const TOKEN_KEY = "ev.token";
  const VID_KEY = "ev.vehicleId";
  const ROLE_KEY = "ev.role";
  const USER_KEY = "ev.userId";
  const USERNAME_KEY = "ev.username";

  function authHeaders() {
    const token = localStorage.getItem(TOKEN_KEY);
    return token ? { Authorization: "Bearer " + token } : {};
  }

  function isLoginPage() {
    return /0_login\.html$/.test(location.pathname);
  }

  function apiBase() {
    return location.origin && location.origin.startsWith("http")
      ? location.origin
      : "http://localhost:8080";
  }

  // Returns the unwrapped `data` field on success; throws Error(message) on failure.
  // On HTTP 401 clears auth and redirects to login page (unless already there).
  async function api(path, options = {}) {
    const opts = Object.assign({}, options);
    const headers = Object.assign(
      {},
      (opts.body && !(opts.body instanceof FormData)) ? { "Content-Type": "application/json" } : {},
      authHeaders(),
      opts.headers || {}
    );
    opts.headers = headers;
    const response = await fetch(apiBase() + path, opts);
    if (response.status === 401) {
      localStorage.removeItem(TOKEN_KEY);
      if (!isLoginPage()) {
        location.href = "0_login.html";
      }
      throw new Error("Unauthorized");
    }
    const text = await response.text();
    let payload;
    try {
      payload = text ? JSON.parse(text) : null;
    } catch (e) {
      throw new Error(text || response.statusText);
    }
    if (!response.ok) {
      throw new Error(payload && payload.message ? payload.message : response.statusText);
    }
    if (payload && payload.success === false) {
      throw new Error(payload.message || "Request failed");
    }
    return payload ? payload.data : null;
  }

  // Same as api() but returns the raw envelope { success, data, message }.
  async function apiEnvelope(path, options = {}) {
    try {
      const data = await api(path, options);
      return { success: true, data, message: "OK" };
    } catch (e) {
      return { success: false, data: null, message: e.message || String(e) };
    }
  }

  function saveAuth(data) {
    if (!data) return;
    if (data.token) localStorage.setItem(TOKEN_KEY, data.token);
    if (data.vehicleId) localStorage.setItem(VID_KEY, data.vehicleId);
    if (data.role) localStorage.setItem(ROLE_KEY, data.role);
    if (data.userId) localStorage.setItem(USER_KEY, data.userId);
    if (data.username) localStorage.setItem(USERNAME_KEY, data.username);
  }

  function clearAuth() {
    [TOKEN_KEY, VID_KEY, ROLE_KEY, USER_KEY, USERNAME_KEY].forEach(k => localStorage.removeItem(k));
  }

  function currentAuth() {
    return {
      token: localStorage.getItem(TOKEN_KEY),
      vehicleId: localStorage.getItem(VID_KEY),
      role: localStorage.getItem(ROLE_KEY),
      userId: localStorage.getItem(USER_KEY),
      username: localStorage.getItem(USERNAME_KEY)
    };
  }

  function ensureAuthOrRedirect(requiredRole) {
    const { token, role } = currentAuth();
    if (!token) {
      if (!isLoginPage()) location.href = "0_login.html";
      return false;
    }
    if (requiredRole && role !== requiredRole) {
      clearAuth();
      if (!isLoginPage()) location.href = "0_login.html";
      return false;
    }
    return true;
  }

  function authToken() {
    return localStorage.getItem(TOKEN_KEY);
  }

  function logout() {
    api("/api/auth/logout", { method: "POST" }).catch(() => {});
    clearAuth();
    location.href = "0_login.html";
  }

  function escapeHtml(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, (c) => ({
      "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[c]);
  }

  // 给 nav 里的 .user 容器挂上下拉菜单（含退出登录）。无 .user 元素时静默返回。
  // 调用时机：登录页之外的所有页面，在 DOMContentLoaded 后即可执行。
  function mountUserMenu() {
    if (isLoginPage()) return;
    const userEl = document.querySelector(".user");
    if (!userEl || userEl.querySelector(".user-menu")) return;
    const auth = currentAuth();
    const isAdmin = (auth.role || "").toUpperCase() === "ADMIN";
    const usernameTxt = auth.username || (isAdmin ? "管理员" : "车主");
    const vidLine = auth.vehicleId
      ? `<div class="label-line">车辆 ${escapeHtml(auth.vehicleId)}</div>` : "";
    const homePath = isAdmin ? "4_admin_piles.html" : "1_charge_request.html";
    const menu = document.createElement("div");
    menu.className = "user-menu";
    menu.innerHTML = `
      <div class="label-line">${isAdmin ? "管理员" : "车主"} · ${escapeHtml(usernameTxt)}</div>
      ${vidLine}
      <div class="divider"></div>
      <a class="item" href="${homePath}">回到主页</a>
      <button class="item danger" type="button" data-action="logout">退出登录</button>
    `;
    userEl.appendChild(menu);
    if (!userEl.hasAttribute("tabindex")) userEl.setAttribute("tabindex", "0");
    userEl.addEventListener("click", (e) => {
      if (e.target.closest(".user-menu")) return;
      userEl.classList.toggle("is-open");
    });
    document.addEventListener("click", (e) => {
      if (!userEl.contains(e.target)) userEl.classList.remove("is-open");
    });
    menu.querySelector('[data-action="logout"]').addEventListener("click", (e) => {
      e.preventDefault();
      logout();
    });
  }

  // 自动挂载（无 .user 容器时无副作用）
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", mountUserMenu);
  } else {
    mountUserMenu();
  }

  window.EVAuth = {
    api,
    apiEnvelope,
    saveAuth,
    clearAuth,
    currentAuth,
    ensureAuthOrRedirect,
    authToken,
    authHeaders,
    apiBase,
    logout,
    mountUserMenu
  };
  // Backwards-compatible global `api(path, opts)` matching the existing pages' convention.
  window.api = api;
})();
