const LS = {
  account: "leaguedesk.account",
  league: "leaguedesk.league",
  game: "leaguedesk.game",
  season: "leaguedesk.season",
  team: "leaguedesk.team",
  lm: "leaguedesk.lm",
  theme: "leaguedesk.theme",
};

const SPORT_PATH = { fba: "basketball", ffl: "football", flb: "baseball", fhl: "hockey" };

const state = {
  config: null,
  data: null,
  playerIndex: new Map(),
  account: localStorage.getItem(LS.account) || "",
  league: localStorage.getItem(LS.league) || "",
  game: localStorage.getItem(LS.game) || "",
  season: localStorage.getItem(LS.season) || "",
  teamId: Number(localStorage.getItem(LS.team)) || null,
  tab: "rosters",
  search: "",
  position: "ALL",
  sort: "depth",
  lm: localStorage.getItem(LS.lm) === "1",
  theme: localStorage.getItem(LS.theme) || "dark",
  loading: false,
  error: null,
  trade: {
    open: false,
    partnerId: null,
    give: new Set(),
    receive: new Set(),
    comment: "",
    picking: null,
  },
};

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

const els = {
  content: $("#content"),
  teamList: $("#teamList"),
  teamCount: $("#teamCount"),
  sideLeague: $("#sideLeague"),
  accountSelect: $("#accountSelect"),
  accountAvatar: $("#accountAvatar"),
  lmSwitch: $("#lmSwitch"),
  lmBanner: $("#lmBanner"),
  leagueName: $("#leagueName"),
  leagueSeason: $("#leagueSeason"),
  leagueWeek: $("#leagueWeek"),
  topEyebrow: $("#topEyebrow"),
  tradeBadge: $("#tradeBadge"),
  tabs: $("#tabs"),
  searchInput: $("#searchInput"),
  tradePanel: $("#tradePanel"),
  tradeBody: $("#tradeBody"),
  modalRoot: $("#modalRoot"),
  toastRoot: $("#toastRoot"),
  themeIcon: $("#themeIcon"),
  themeLabel: $("#themeLabel"),
  app: $("#app"),
};

function esc(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function icon(name, cls = "") {
  return `<svg class="icon ${cls}" aria-hidden="true"><use href="#i-${name}"/></svg>`;
}

function fmt1(value) {
  if (value === null || value === undefined || value === "" || Number.isNaN(Number(value))) return "â€”";
  return Number(value).toFixed(1);
}

function fmtInt(value) {
  if (value === null || value === undefined || value === "") return "â€”";
  return String(Math.round(Number(value)));
}

function pct(value) {
  if (value === null || value === undefined) return "â€”";
  return `${Number(value).toFixed(1)}%`;
}

function initials(value) {
  const parts = String(value || "").replaceAll(".", " ").split(/\s+/).filter(Boolean);
  return (parts[0]?.[0] || "?") + (parts[1]?.[0] || "");
}

function timeAgo(ms) {
  if (!ms) return "â€”";
  const diff = Date.now() - Number(ms);
  if (diff < 0) return fmtDate(ms);
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}d ago`;
  return fmtDate(ms);
}

function fmtDate(ms) {
  if (!ms) return "â€”";
  return new Date(Number(ms)).toLocaleString(undefined, {
    month: "short", day: "numeric", hour: "numeric", minute: "2-digit",
  });
}

function expiresIn(ms) {
  if (!ms) return null;
  const diff = Number(ms) - Date.now();
  if (diff <= 0) return "expired";
  const hours = Math.floor(diff / 3600000);
  if (hours < 1) return `${Math.max(1, Math.floor(diff / 60000))}m left`;
  if (hours < 48) return `${hours}h left`;
  return `${Math.floor(hours / 24)}d left`;
}

function shortInjury(status) {
  const map = { DAY_TO_DAY: "DTD", QUESTIONABLE: "Q", DOUBTFUL: "D", OUT: "OUT", SUSPENSION: "SUSP", GTD: "GTD", INJURED_RESERVE: "IR" };
  return map[status] || String(status || "").slice(0, 5);
}

function capitalize(value) {
  return String(value || "").charAt(0).toUpperCase() + String(value || "").slice(1);
}

async function api(path, options = {}) {
  const res = await fetch(path, {
    ...options,
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
  });
  let body = null;
  try {
    body = await res.json();
  } catch {
    throw new Error(`Server returned ${res.status}.`);
  }
  if (!res.ok || body?.ok === false) {
    const error = new Error(body?.error || `Request failed (${res.status}).`);
    error.detail = body?.detail;
    throw error;
  }
  return body;
}

function toast(message, type = "info", timeout = 4200) {
  const node = document.createElement("div");
  node.className = `toast ${type}`;
  const iconName = type === "success" ? "check" : type === "error" ? "alert" : "spark";
  node.innerHTML = `${icon(iconName)}<div>${esc(message)}</div>`;
  node.addEventListener("click", () => node.remove());
  els.toastRoot.append(node);
  setTimeout(() => node.remove(), timeout);
}

function teamById(id) {
  return state.data?.teams?.find((t) => Number(t.id) === Number(id)) || null;
}

function myTeam() {
  return teamById(state.data?.myTeamId);
}

function teamAccent(id) {
  return teamById(id)?.accent || { a: "#8b7cff", b: "#22d3ee" };
}

function teamAbbrev(id) {
  return teamById(id)?.abbrev || `T${id}`;
}

function teamName(id) {
  return teamById(id)?.name || `Team ${id}`;
}

function playerById(id) {
  return state.playerIndex.get(Number(id)) || null;
}

function selectedPlayers(set) {
  return [...set].map((id) => playerById(id)).filter(Boolean);
}

function rebuildIndex() {
  state.playerIndex = new Map();
  for (const team of state.data?.teams || []) {
    for (const player of team.roster) state.playerIndex.set(Number(player.id), player);
  }
}

function applyTheme() {
  document.documentElement.dataset.theme = state.theme;
  els.themeIcon.innerHTML = `<use href="#i-${state.theme === "dark" ? "sun" : "moon"}"/>`;
  els.themeLabel.textContent = state.theme === "dark" ? "Light" : "Dark";
}

function toggleTheme() {
  state.theme = state.theme === "dark" ? "light" : "dark";
  localStorage.setItem(LS.theme, state.theme);
  applyTheme();
}

function setLM(value, announce = true) {
  state.lm = Boolean(value);
  localStorage.setItem(LS.lm, state.lm ? "1" : "0");
  els.lmSwitch.setAttribute("aria-checked", state.lm ? "true" : "false");
  $(".lm-row")?.classList.toggle("is-on", state.lm);
  els.lmBanner.hidden = !state.lm;
  if (announce) {
    toast(state.lm ? "LM mode armed. Actions on other teams will escalate and restore the flag."
                    : "LM mode off.", state.lm ? "info" : "info");
  }
  renderMain();
}

async function init() {
  applyTheme();
  setLM(state.lm, false);
  bindStatic();

  try {
    const config = await api("/api/config");
    state.config = config;
    state.league = state.league || config.league;
    state.game = state.game || config.game;
    state.season = state.season || config.season;

    if (!config.accounts.length) {
      state.error = new Error("No usable cookie files in the cookies directory.");
      render();
      return;
    }
    if (!config.accounts.includes(state.account)) state.account = config.defaultAccount;

    els.accountSelect.innerHTML = config.accounts
      .map((name) => `<option value="${esc(name)}">${esc(name)}</option>`)
      .join("");
    els.accountSelect.value = state.account;
  } catch (error) {
    state.error = error;
    render();
    return;
  }

  await loadState();
}

function bindStatic() {
  els.accountSelect.addEventListener("change", () => {
    state.account = els.accountSelect.value;
    localStorage.setItem(LS.account, state.account);
    state.data = null;
    state.trade.open = false;
    state.trade.give.clear();
    state.trade.receive.clear();
    state.trade.partnerId = null;
    state.teamId = null;
    loadState();
  });

  els.searchInput.addEventListener("input", () => {
    state.search = els.searchInput.value;
    if (state.search.trim()) state.tab = "rosters";
    renderTabs();
    renderMain();
  });

  els.lmSwitch.addEventListener("click", () => setLM(!state.lm));

  els.tabs.addEventListener("click", (event) => {
    const tab = event.target.closest("[data-tab]");
    if (!tab) return;
    state.tab = tab.dataset.tab;
    state.search = "";
    els.searchInput.value = "";
    state.trade.picking = null;
    renderTabs();
    renderMain();
    window.scrollTo({ top: 0, behavior: "smooth" });
  });

  let resizeTimer = null;
  window.addEventListener("resize", () => {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => {
      if (window.innerWidth > 1180) els.app.classList.toggle("trade-open", state.trade.open);
    }, 120);
  });

  document.addEventListener("keydown", (event) => {
    const tag = event.target?.tagName;
    const typing = tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || event.target?.isContentEditable;
    if (event.key === "/" && !typing) {
      event.preventDefault();
      els.searchInput.focus();
    }
    if (event.key.toLowerCase() === "k" && (event.metaKey || event.ctrlKey)) {
      event.preventDefault();
      els.searchInput.focus();
    }
    if (event.key === "Enter" && !typing) {
      const card = event.target.closest?.(".player-card");
      if (card?.dataset.player) {
        event.preventDefault();
        openPlayer(Number(card.dataset.player));
      }
    }
    if (event.key === "Escape") {
      if (els.modalRoot.innerHTML) {
        closeModal();
      } else if (state.trade.open) {
        closeTrade();
      } else if (document.activeElement === els.searchInput) {
        els.searchInput.blur();
      }
    }
  });

  document.addEventListener("click", handleClick);
  document.addEventListener("change", handleChange);
  document.addEventListener("input", handleInput);
  document.addEventListener("submit", handleSubmit);

  const searchWrap = $(".search");
  searchWrap?.addEventListener("click", () => {
    if (window.innerWidth <= 680) {
      searchWrap.classList.add("is-open");
      els.searchInput.focus();
      const close = () => {
        if (!els.searchInput.value) {
          searchWrap.classList.remove("is-open");
          els.searchInput.removeEventListener("blur", close);
        }
      };
      els.searchInput.addEventListener("blur", close);
    }
  });
}

async function loadState(force = false) {
  if (!state.account) return;
  state.loading = true;
  state.error = null;
  $('[data-action="refresh"]')?.classList.add("is-spinning");

  const params = new URLSearchParams({
    account: state.account,
    league: state.league,
    game: state.game,
    season: state.season,
    refresh: force ? "1" : "0",
  });

  try {
    const body = await api(`/api/state?${params}`);
    const fresh = body.state;
    state.data = fresh;
    rebuildIndex();

    const teams = fresh.teams || [];
    if (!teams.some((t) => Number(t.id) === Number(state.teamId))) {
      state.teamId = fresh.myTeamId ?? teams[0]?.id ?? null;
    }
    localStorage.setItem(LS.team, String(state.teamId ?? ""));

    const trade = state.trade;
    const my = fresh.myTeamId;
    const myRoster = new Set((teamById(my)?.roster || []).map((p) => Number(p.id)));
    for (const id of [...trade.give]) if (!myRoster.has(id)) trade.give.delete(id);

    if (trade.partnerId && !teamById(trade.partnerId)) {
      trade.partnerId = null;
      trade.receive.clear();
    }
    const partnerRoster = new Set((teamById(trade.partnerId)?.roster || []).map((p) => Number(p.id)));
    for (const id of [...trade.receive]) if (!partnerRoster.has(id)) trade.receive.delete(id);

    if (!my) {
      trade.open = false;
      trade.give.clear();
      trade.receive.clear();
    }
    render();
  } catch (error) {
    state.error = error;
    if (state.data) {
      toast(error.message, "error", 6000);
      render();
    } else {
      render();
    }
  } finally {
    state.loading = false;
    $('[data-action="refresh"]')?.classList.remove("is-spinning");
  }
}

function render() {
  renderSidebar();
  renderTopbar();
  renderMain();
  renderTradePanel();
}

function renderTabs() {
  $$(".tab", els.tabs).forEach((tab) => {
    tab.classList.toggle("is-active", tab.dataset.tab === state.tab);
  });
}

function renderTopbar() {
  const data = state.data;
  const league = data?.league;

  els.leagueName.textContent = league?.name || "League Desk";
  els.leagueSeason.textContent = league ? `Season ${league.seasonLabel}` : "";
  els.leagueWeek.textContent = league ? `Week ${league.week}` : "";
  els.topEyebrow.textContent = league
    ? `${capitalize(league.sport)} Â· ${league.size} teams Â· ${data.account.name}`
    : "League office";

  const count = data?.pendingTrades?.length || 0;
  els.tradeBadge.hidden = count === 0;
  els.tradeBadge.textContent = String(count);

  renderTabs();
  els.lmBanner.hidden = !state.lm;
}

function renderSidebar() {
  const data = state.data;

  if (!data) {
    els.sideLeague.innerHTML = `<span class="skeleton skeleton-line" style="width:70%"></span>`;
    els.teamList.innerHTML = "";
    els.teamCount.textContent = "â€”";
    return;
  }

  const league = data.league;
  els.sideLeague.innerHTML = `
    <div class="sl-name" title="${esc(league.name)}">${esc(league.name)}</div>
    <div class="sl-meta">
      <span class="sl-dot"></span>
      <span>${esc(league.seasonLabel)}</span>
      <span>Â·</span>
      <span>Week ${esc(league.week)}</span>
    </div>
  `;

  els.teamCount.textContent = data.teams.length;
  els.accountAvatar.textContent = initials(data.account.name).toUpperCase();

  const ordered = [...data.teams].sort((a, b) => {
    const wa = Number(a.record?.wins || 0) - Number(b.record?.wins || 0);
    if (wa !== 0) return -wa;
    return Number(b.totalFppg || 0) - Number(a.totalFppg || 0);
  });

  els.teamList.innerHTML = ordered.map((team, index) => `
    <button class="team-row ${Number(team.id) === Number(state.teamId) ? "is-active" : ""}"
            style="--row-a:${esc(team.accent.a)}"
            data-action="select-team" data-team="${team.id}">
      <span class="team-row-logo">
        ${team.logo ? `<img src="${esc(team.logo)}" alt="" loading="lazy" onerror="this.style.display='none'">` : ""}
      </span>
      <span class="team-row-copy">
        <span class="team-row-name">${esc(team.name)}</span>
        <span class="team-row-meta">
          ${team.record.wins}-${team.record.losses}
          <span>Â·</span>
          ${team.totalPlayers} players
          ${team.isMine ? `<span class="team-row-you">YOU</span>` : ""}
        </span>
      </span>
      <span class="team-row-rank">${index + 1}</span>
    </button>
  `).join("");

  els.accountSelect.value = state.account;
  $(".lm-row")?.classList.toggle("is-on", state.lm);
  els.lmSwitch.setAttribute("aria-checked", state.lm ? "true" : "false");
}

function renderMain() {
  if (state.error && !state.data) {
    renderError(state.error);
    return;
  }
  if (!state.data) return;

  if (state.search.trim()) {
    renderSearchResults();
    return;
  }

  if (state.tab === "trades") renderTrades();
  else if (state.tab === "activity") renderActivity();
  else renderRoster();
}

function renderError(error) {
  const message = error?.message || "Could not load the league.";
  const detail = error?.detail ? `<div class="detail">${esc(error.detail)}</div>` : "";
  els.content.innerHTML = `
    <div class="error-state">
      <div class="empty-icon">${icon("alert")}</div>
      <h2>Couldn't load the league</h2>
      <p>${esc(message)}</p>
      ${detail}
      <div class="pm-actions" style="justify-content:center">
        <button class="btn btn-primary" data-action="refresh">${icon("refresh")} Try again</button>
        <button class="btn btn-ghost" data-action="settings">${icon("settings")} Settings</button>
      </div>
    </div>
  `;
}

function renderRoster() {
  const team = teamById(state.teamId) || state.data.teams[0];
  if (!team) return;
  state.teamId = team.id;

  const showPickBanner = state.trade.open && state.trade.picking;
  const pickCopy = state.trade.picking === "give"
    ? `Picking players to <strong>send</strong>${state.trade.partnerId ? ` to ${esc(teamName(state.trade.partnerId))}` : ""}`
    : `Picking players to <strong>receive</strong>${state.trade.partnerId ? ` from ${esc(teamName(state.trade.partnerId))}` : ""}`;

  els.content.innerHTML = `
    ${showPickBanner ? `
      <div class="pick-banner">
        ${icon("spark")}
        <span>${pickCopy} â€” use the <b>+</b> button on a player card.</span>
        <button class="btn btn-sm btn-ghost" data-action="done-picking">Done</button>
      </div>` : ""}
    ${heroMarkup(team)}
    ${toolbarMarkup(team)}
    ${playerGridMarkup(team)}
  `;

  $(".sort-select")?.addEventListener("change", (event) => {
    state.sort = event.target.value;
    renderRoster();
  });
}

function heroMarkup(team) {
  const isMine = Number(team.id) === Number(state.data.myTeamId);
  const league = state.data.league;
  const sportPath = SPORT_PATH[league.game] || "basketball";
  const espnUrl = `https://fantasy.espn.com/${sportPath}/league?leagueId=${encodeURIComponent(league.id)}&seasonId=${encodeURIComponent(league.season)}`;
  const record = `${team.record.wins}-${team.record.losses}${team.record.ties ? `-${team.record.ties}` : ""}`;

  return `
    <section class="team-hero" style="--a:${esc(team.accent.a)};--b:${esc(team.accent.b)}">
      <div class="hero-logo">
        ${team.logo ? `<img src="${esc(team.logo)}" alt="${esc(team.name)} logo">` : `<span class="mono">${esc(team.abbrev)}</span>`}
      </div>
      <div class="hero-main">
        <div class="hero-topline">
          ${isMine ? `<span class="badge accent">${icon("shield")} Your team</span>` : `<span class="badge">Owned by ${esc(team.owners.join(", "))}</span>`}
          <span class="badge">Rank #${esc(team.rank ?? "â€”")}</span>
          ${team.waiverRank ? `<span class="badge">Waiver ${esc(team.waiverRank)}</span>` : ""}
        </div>
        <h1 class="hero-name">${esc(team.name)}</h1>
        <div class="hero-sub">
          <span>${team.totalPlayers} players</span>
          <span class="sep">/</span>
          <span>${record} record</span>
          <span class="sep">/</span>
          <span>${esc(team.abbrev)}</span>
          ${isMine ? `<span class="sep">/</span><span class="mine">you</span>` : ""}
        </div>
        <div class="hero-actions">
          ${isMine
            ? `<button class="btn btn-primary" data-action="open-trade">${icon("trade")} Propose a trade</button>`
            : `<button class="btn btn-primary" data-action="open-trade" data-team="${team.id}">${icon("trade")} Trade with ${esc(team.abbrev)}</button>`}
          ${!isMine && state.data.myTeamId
            ? `<button class="btn btn-ghost" data-action="select-team" data-team="${state.data.myTeamId}">${icon("users")} My team</button>` : ""}
          <a class="btn btn-ghost" href="${espnUrl}" target="_blank" rel="noreferrer">${icon("arrow")} ESPN</a>
        </div>
      </div>
      <div class="hero-stats">
        <div class="hero-stat">
          <div class="label">Record</div>
          <div class="value">${record}</div>
        </div>
        <div class="hero-stat">
          <div class="label">Points for</div>
          <div class="value">${fmt1(team.record.pointsFor)}</div>
        </div>
        <div class="hero-stat">
          <div class="label">Team FPPG</div>
          <div class="value">${fmt1(team.totalFppg)}<small>avg</small></div>
        </div>
        <div class="hero-stat">
          <div class="label">Players</div>
          <div class="value">${team.totalPlayers}</div>
        </div>
      </div>
    </section>
  `;
}

function toolbarMarkup(team) {
  const positions = ["ALL", ...new Set(team.roster.map((p) => p.position).filter((p) => p && p !== "â€”"))];
  const counts = team.roster.length;
  return `
    <div class="roster-toolbar">
      <div class="filters">
        ${positions.map((pos) => `
          <button class="filter-chip ${state.position === pos ? "is-active" : ""}"
                  data-action="filter-position" data-pos="${esc(pos)}">${esc(pos === "ALL" ? "All" : pos)}</button>
        `).join("")}
      </div>
      <div class="select-wrap">
        <select class="sort-select" aria-label="Sort players">
          <option value="depth" ${state.sort === "depth" ? "selected" : ""}>Depth chart</option>
          <option value="fppg" ${state.sort === "fppg" ? "selected" : ""}>Fantasy PPG</option>
          <option value="name" ${state.sort === "name" ? "selected" : ""}>Name Aâ€“Z</option>
          <option value="owned" ${state.sort === "owned" ? "selected" : ""}>Ownership</option>
        </select>
        ${icon("chevron")}
      </div>
      <div class="toolbar-spacer"></div>
      <div class="toolbar-note">${counts} players Â· click a player for details</div>
    </div>
  `;
}

function playerGridMarkup(team, options = {}) {
  const showTeam = Boolean(options.showTeam);
  let players = team.roster.filter((p) => !options.filterTeam || Number(p.teamId) === Number(options.filterTeam));

  if (state.position !== "ALL") {
    players = players.filter((p) => p.position === state.position);
  }

  if (!players.length) {
    return `
      <div class="player-grid">
        <div class="empty-grid">
          ${icon("search")}
          <h3>No players match</h3>
          <p>Try a different position filter or clear the search.</p>
        </div>
      </div>`;
  }

  if (state.sort === "depth" && !options.search) {
    const starters = players.filter((p) => p.isStarter);
    const bench = players.filter((p) => !p.isStarter);
    const parts = [];
    if (starters.length) {
      parts.push(`<div class="roster-group-label">Starters <span class="count">${starters.length}</span></div>`);
      parts.push(...starters.map((p) => playerCard(p, showTeam)));
    }
    if (bench.length) {
      parts.push(`<div class="roster-group-label">Bench / IR <span class="count">${bench.length}</span></div>`);
      parts.push(...bench.map((p) => playerCard(p, showTeam)));
    }
    return `<div class="player-grid">${parts.join("")}</div>`;
  }

  const sorted = [...players].sort((a, b) => {
    if (state.sort === "name") return a.name.localeCompare(b.name);
    if (state.sort === "owned") return (b.ownership.owned || 0) - (a.ownership.owned || 0);
    return (b.fppg || 0) - (a.fppg || 0);
  });

  return `<div class="player-grid">${sorted.map((p) => playerCard(p, showTeam)).join("")}</div>`;
}

function playerCard(player, showTeam = false) {
  const accent = teamAccent(player.teamId);
  const selected = state.trade.give.has(Number(player.id)) || state.trade.receive.has(Number(player.id));
  const injury = player.injured && player.injuryStatus
    ? `<span class="pc-injury" title="${esc(player.injuryStatus)}">${esc(shortInjury(player.injuryStatus))}</span>` : "";
  const meta = showTeam
    ? `${esc(teamName(player.teamId))}`
    : `${esc(player.slot || "â€”")} Â· ${esc(player.proTeamName || player.proTeam || "")}`;

  return `
    <article class="player-card ${selected ? "is-selected" : ""} ${state.trade.open ? "is-pickable" : ""}"
             style="--pc-a:${esc(accent.a)}"
             data-action="player" data-player="${player.id}" tabindex="0" role="button"
             aria-label="${esc(player.name)}, ${esc(player.position)}, ${fmt1(player.fppg)} fantasy points per game">
      <div class="pc-media">
        <span class="initials">${esc(player.initials)}</span>
        <img src="${esc(player.headshot)}" alt="${esc(player.name)}" loading="lazy" onerror="this.style.display='none'">
        <span class="pc-pos">${esc(player.position || "â€”")}</span>
        ${injury}
        <span class="pc-team">${esc(player.proTeam || "FA")}</span>
        <button class="pc-add" data-action="toggle-trade" data-player="${player.id}"
                title="${selected ? "Remove from trade" : "Add to trade"}"
                aria-label="${selected ? "Remove from trade" : "Add to trade"}">${icon("plus")}</button>
      </div>
      <div class="pc-body">
        <div class="pc-name" title="${esc(player.name)}">${esc(player.name)}</div>
        <div class="pc-meta">${meta}</div>
        <div class="pc-nums">
          <div class="pc-fppg"><b>${fmt1(player.fppg)}</b><span>FPPG</span></div>
          <div class="pc-own"><b>${pct(player.ownership.owned)}</b> owned</div>
        </div>
      </div>
    </article>
  `;
}

function renderSearchResults() {
  const query = state.search.trim().toLowerCase();
  const hits = [];
  for (const team of state.data.teams) {
    for (const player of team.roster) {
      const haystack = `${player.name} ${player.position} ${player.proTeam} ${player.proTeamName} ${team.name}`.toLowerCase();
      if (haystack.includes(query)) hits.push({ player, team });
    }
  }
  hits.sort((a, b) => (b.player.fppg || 0) - (a.player.fppg || 0));

  els.content.innerHTML = `
    <div class="section-head">
      <div>
        <h2>${icon("search")} Search results</h2>
        <p>${hits.length} player${hits.length === 1 ? "" : "s"} across the league match â€œ${esc(state.search.trim())}â€.</p>
      </div>
      <button class="btn btn-ghost btn-sm" data-action="clear-search">${icon("close")} Clear</button>
    </div>
    ${hits.length ? `<div class="player-grid">${hits.map(({ player }) => playerCard(player, true)).join("")}</div>` : `
      <div class="empty-state">
        <div class="empty-icon">${icon("search")}</div>
        <h3>Nothing found</h3>
        <p>Try a player name, position, pro team, or fantasy team.</p>
      </div>`}
  `;
}

function openPlayer(playerId) {
  const player = playerById(playerId);
  if (!player) return;
  const team = teamById(player.teamId);
  const accent = team?.accent || { a: "#8b7cff", b: "#22d3ee" };
  const selected = state.trade.give.has(Number(player.id)) || state.trade.receive.has(Number(player.id));
  const isMine = Number(player.teamId) === Number(state.data.myTeamId);
  const naturalLM = state.data.account.isLM;

  const badges = [
    player.injured && player.injuryStatus ? `<span class="badge bad">${esc(shortInjury(player.injuryStatus))}</span>` : `<span class="badge good">Healthy</span>`,
    player.tradeLocked ? `<span class="badge warn">${icon("lock")} Trade locked</span>` : "",
    !player.droppable ? `<span class="badge warn">Undroppable</span>` : "",
    player.pendingTransactions?.length ? `<span class="badge accent">Pending move</span>` : "",
  ].filter(Boolean).join("");

  const stats = player.statLine?.length ? `
    <div>
      <div class="pm-section-title">Per game Â· ${esc(player.statsSeason || "season")}</div>
      <div class="stat-line">
        ${player.statLine.map((stat) => `<div class="stat-cell"><b>${esc(stat.value)}</b><span>${esc(stat.label)}</span></div>`).join("")}
      </div>
    </div>` : "";

  const details = [
    ["Fantasy team", team?.name || "â€”"],
    ["Lineup slot", player.slot || "â€”"],
    ["Acquired", capitalize(player.acquisitionType || "â€”")],
    ["ADP", player.ownership.adp ?? "â€”"],
    ["Auction value", player.ownership.auction ?? "â€”"],
    ["Ownership change", player.ownership.change != null ? `${player.ownership.change > 0 ? "+" : ""}${player.ownership.change}%` : "â€”"],
  ];

  setModal(`
    <button class="modal-close" data-action="close-modal" aria-label="Close">${icon("close")}</button>
    <div class="pm-hero" style="--a:${esc(accent.a)}">
      <div class="pm-photo">
        <span class="initials">${esc(player.initials)}</span>
        <img src="${esc(player.headshot)}" alt="${esc(player.name)}" onerror="this.style.display='none'">
      </div>
      <div class="pm-copy">
        <div class="pm-kicker">
          <span class="badge accent">${esc(player.position || "â€”")}</span>
          <span class="badge">${esc(player.proTeam || "Free agent")}</span>
          ${isMine ? `<span class="badge">Your roster</span>` : ""}
        </div>
        <h2 class="pm-name">${esc(player.name)}</h2>
        <div class="pm-meta">
          <span>${esc(player.proTeamName || "â€”")}</span>
          <span class="dot"></span>
          <span>${esc(player.slot || "â€”")}</span>
          <span class="dot"></span>
          <span>${esc(team?.name || "")}</span>
        </div>
        <div class="pm-badges">${badges}</div>
      </div>
    </div>
    <div class="pm-body">
      <div class="stat-tiles">
        <div class="stat-tile">
          <div class="label">Fantasy PPG</div>
          <div class="value">${fmt1(player.fppg)}<small>avg</small></div>
          <div class="sub">${player.gamesPlayed ? `${player.gamesPlayed} games` : "season average"}</div>
        </div>
        <div class="stat-tile">
          <div class="label">Projected</div>
          <div class="value">${fmt1(player.projFppg)}<small>avg</small></div>
          <div class="sub">per game</div>
        </div>
        <div class="stat-tile">
          <div class="label">Rostered</div>
          <div class="value">${pct(player.ownership.owned)}</div>
          <div class="sub">${pct(player.ownership.started)} started</div>
        </div>
        <div class="stat-tile">
          <div class="label">Last season</div>
          <div class="value">${fmt1(player.lastFppg)}<small>avg</small></div>
          <div class="sub">${esc(player.statsSeason || "â€”")}</div>
        </div>
      </div>

      ${stats}

      ${player.outlook ? `
        <div>
          <div class="pm-section-title">Season outlook</div>
          <p class="pm-outlook">${esc(player.outlook)}</p>
        </div>` : ""}

      <div>
        <div class="pm-section-title">Roster</div>
        <div class="pm-grid">
          ${details.map(([label, value]) => `<div class="kv"><span>${esc(label)}</span><b>${esc(value)}</b></div>`).join("")}
        </div>
      </div>

      <div class="pm-actions">
        <button class="btn btn-primary" data-action="add-to-trade" data-player="${player.id}">
          ${icon(selected ? "close" : "plus")} ${selected ? "Remove from trade" : "Add to trade"}
        </button>
        <button class="btn ${isMine ? "btn-ghost" : "btn-danger"}" data-action="open-drop" data-player="${player.id}" ${!isMine && !state.lm && !naturalLM ? "disabled title='Arm LM mode to drop from another roster'" : ""}>
          ${icon("drop")} ${isMine ? "Drop player" : "Drop via LM"}
        </button>
      </div>
      ${!isMine && !state.lm && !naturalLM ? `
        <div class="callout">${icon("shield")} <span>Dropping from <strong>${esc(team?.name || "another team")}</strong> requires <strong>LM mode</strong>. Arm it in the sidebar first.</span></div>` : ""}
    </div>
  `);
}

function openTrade(partnerId = null) {
  if (!state.data?.myTeamId) {
    toast("This account does not own a team in the league.", "error");
    return;
  }
  if (partnerId && Number(partnerId) === Number(state.data.myTeamId)) return;
  if (partnerId) {
    if (state.trade.partnerId && Number(state.trade.partnerId) !== Number(partnerId)) {
      state.trade.receive.clear();
    }
    state.trade.partnerId = Number(partnerId);
  } else if (!state.trade.partnerId) {
    const other = state.data.teams.find((t) => Number(t.id) !== Number(state.data.myTeamId) && t.roster.length);
    state.trade.partnerId = other ? Number(other.id) : null;
  }
  state.trade.open = true;
  els.app.classList.add("trade-open");
  if (partnerId) state.teamId = Number(partnerId);
  renderTradePanel();
  renderMain();
}

function closeTrade() {
  state.trade.open = false;
  state.trade.picking = null;
  els.app.classList.remove("trade-open");
  renderTradePanel();
  renderMain();
}

function toggleTradePlayer(playerId) {
  const player = playerById(playerId);
  if (!player) return;
  const my = state.data?.myTeamId;
  if (!my) {
    toast("This account does not own a team.", "error");
    return;
  }

  const isMine = Number(player.teamId) === Number(my);
  let added = false;

  if (isMine) {
    const id = Number(player.id);
    if (state.trade.give.has(id)) state.trade.give.delete(id);
    else { state.trade.give.add(id); added = true; }
  } else {
    if (state.trade.partnerId !== Number(player.teamId)) {
      state.trade.receive.clear();
      state.trade.partnerId = Number(player.teamId);
    }
    const id = Number(player.id);
    if (state.trade.receive.has(id)) state.trade.receive.delete(id);
    else { state.trade.receive.add(id); added = true; }
  }

  state.trade.open = true;
  els.app.classList.add("trade-open");
  toast(`${added ? "Added" : "Removed"} ${player.name} ${added ? "to" : "from"} trade.`, "info", 2200);
  renderMain();
  renderTradePanel();
}

function renderTradePanel() {
  if (!state.trade.open) {
    els.tradeBody.innerHTML = "";
    els.app.classList.remove("trade-open");
    return;
  }
  els.app.classList.add("trade-open");

  const data = state.data;
  const my = myTeam();
  if (!data || !my) {
    els.tradeBody.innerHTML = `<div class="tp-body"><div class="tp-empty">No team is attached to this account.</div></div>`;
    return;
  }

  const partner = teamById(state.trade.partnerId);
  const give = selectedPlayers(state.trade.give).filter((p) => Number(p.teamId) === Number(my.id));
  const receive = selectedPlayers(state.trade.receive).filter((p) => !partner || Number(p.teamId) === Number(partner.id));
  const giveValue = give.reduce((sum, p) => sum + (p.fppg || 0), 0);
  const receiveValue = receive.reduce((sum, p) => sum + (p.fppg || 0), 0);
  const verdict = tradeVerdict(giveValue, receiveValue);
  const canPropose = Boolean(partner) && (give.length > 0 || receive.length > 0);
  const partnerOptions = data.teams
    .filter((team) => Number(team.id) !== Number(my.id))
    .map((team) => `<option value="${team.id}" ${Number(team.id) === Number(state.trade.partnerId) ? "selected" : ""}>${esc(team.name)}</option>`)
    .join("");

  els.tradeBody.innerHTML = `
    <div class="tp-head">
      <div>
        <h2>Trade builder</h2>
        <p>Propose Â· review Â· send</p>
      </div>
      <button class="icon-btn" data-action="close-trade" aria-label="Close trade builder">${icon("close")}</button>
    </div>

    <div class="tp-body">
      <div class="tp-field">
        <div class="tp-label">Trade partner <span class="hint">pick a team</span></div>
        <div class="tp-team-select">
          <select id="tradePartner" aria-label="Trade partner">
            ${partnerOptions}
          </select>
          ${icon("chevron")}
        </div>
      </div>

      ${sideMarkup("give", "You send", my, give, giveValue)}
      ${sideMarkup("receive", "You receive", partner, receive, receiveValue)}

      <div class="tp-balance">
        <div class="tp-balance-top">
          <div>
            <div class="tp-balance-label">You send</div>
            <div class="tp-balance-val">${fmt1(giveValue)}<small>fppg</small></div>
          </div>
          <div style="text-align:right">
            <div class="tp-balance-label">You receive</div>
            <div class="tp-balance-val">${fmt1(receiveValue)}<small>fppg</small></div>
          </div>
        </div>
        ${fairnessBar(giveValue, receiveValue)}
        <div class="tp-verdict">${esc(verdict)}</div>
      </div>

      <div class="tp-field tp-comment">
        <div class="tp-label">Message <span class="hint">optional</span></div>
        <textarea id="tradeComment" maxlength="200" placeholder="Add a note to your offerâ€¦">${esc(state.trade.comment)}</textarea>
      </div>

      ${state.lm ? `<div class="tp-note">${icon("shield")} <span>LM mode is armed. Proposals stay on your own team, so no escalation is needed to send this offer.</span></div>` : ""}
    </div>

    <div class="tp-footer">
      <button class="btn btn-primary" data-action="propose-trade" ${canPropose ? "" : "disabled"}>
        ${icon("trade")} Propose trade
      </button>
      <div class="tp-note">${icon("alert")} <span>ESPN may reject a trade if a player is locked, recently dropped, or undroppable. Values are this season's fantasy PPG, not a projection.</span></div>
    </div>
  `;
}

function sideMarkup(kind, kicker, team, players, value) {
  const isGive = kind === "give";
  const body = players.length
    ? players.map((p) => `
        <div class="tp-player">
          <img src="${esc(p.headshot)}" alt="" loading="lazy" onerror="this.style.display='none'">
          <span class="tp-player-copy">
            <span class="tp-player-name">${esc(p.name)}</span>
            <span class="tp-player-meta">${esc(p.position)} Â· ${esc(p.proTeam || "â€”")}</span>
          </span>
          <span class="tp-player-val">${fmt1(p.fppg)}</span>
          <button class="tp-remove" data-action="remove-trade" data-side="${kind}" data-player="${p.id}" aria-label="Remove ${esc(p.name)}">${icon("close")}</button>
        </div>`).join("")
    : `<div class="tp-empty">${isGive ? "Add players from your roster to send." : "Add players from the partner roster to request."}</div>`;

  return `
    <div class="tp-side">
      <div class="tp-side-head">
        <div>
          <div class="tp-side-kicker">${esc(kicker)} <span class="mono">${fmt1(value)}</span></div>
          <div class="tp-side-team">${esc(team?.name || (isGive ? "Your team" : "Choose a partner"))}</div>
        </div>
        <button class="btn btn-sm btn-ghost tp-side-add" data-action="pick-side" data-side="${kind}" ${!team ? "disabled" : ""}>
          ${icon("plus")} Pick
        </button>
      </div>
      <div class="tp-side-players">${body}</div>
    </div>
  `;
}

function fairnessBar(a, b) {
  const total = (a || 0) + (b || 0);
  const left = total ? ((a || 0) / total) * 100 : 50;
  const right = total ? ((b || 0) / total) * 100 : 50;
  return `
    <div class="fairness-bar">
      <span class="left" style="width:${left}%"></span>
      <span class="right" style="width:${right}%"></span>
    </div>`;
}

function tradeVerdict(give, receive) {
  if (!give && !receive) return "Add players to see the balance.";
  const max = Math.max(give, receive, 1);
  const diff = Math.abs(give - receive) / max;
  if (diff < 0.08) return "Balanced by fantasy PPG.";
  return give > receive ? "You're sending the stronger side by fantasy PPG." : "You're receiving the stronger side by fantasy PPG.";
}

function pickSide(side) {
  const my = state.data?.myTeamId;
  if (!my) return;
  if (side === "receive" && !state.trade.partnerId) {
    toast("Choose a trade partner first.", "info");
    return;
  }
  state.trade.picking = side;
  state.tab = "rosters";
  state.search = "";
  els.searchInput.value = "";
  state.teamId = side === "give" ? my : state.trade.partnerId;
  render();
}

async function proposeTrade() {
  const my = state.data?.myTeamId;
  const partner = state.trade.partnerId;
  if (!my || !partner) {
    toast("Pick a trade partner first.", "error");
    return;
  }
  const give = [...state.trade.give];
  const receive = [...state.trade.receive];
  if (!give.length && !receive.length) {
    toast("Select at least one player.", "error");
    return;
  }
  if (state.trade.comment.length > 200) {
    toast("Message is too long.", "error");
    return;
  }

  const button = $('[data-action="propose-trade"]');
  if (button) {
    button.disabled = true;
    button.innerHTML = `${icon("clock")} Sendingâ€¦`;
  }

  try {
    const result = await api("/api/trade/propose", {
      method: "POST",
      body: JSON.stringify({
        account: state.account,
        league: state.league,
        game: state.game,
        season: state.season,
        teamId: my,
        toTeamId: partner,
        give,
        receive,
        comment: state.trade.comment,
        lm: false,
      }),
    });
    toast(result.message || "Trade proposed.", "success");
    if (result.restoreWarning) toast(result.restoreWarning, "error", 9000);
    state.trade.give.clear();
    state.trade.receive.clear();
    state.trade.comment = "";
    state.trade.picking = null;
    state.trade.open = false;
    els.app.classList.remove("trade-open");
    state.tab = "trades";
    renderTabs();
    await loadState(true);
  } catch (error) {
    toast(error.message, "error", 7000);
    if (button) {
      button.disabled = false;
      button.innerHTML = `${icon("trade")} Propose trade`;
    }
  }
}

function openAcceptModal(tradeId, teamId = null) {
  const trade = state.data?.pendingTrades?.find((t) => t.id === tradeId);
  if (!trade) {
    toast("That trade is no longer pending.", "error");
    return;
  }
  const naturalLM = state.data.account.isLM;
  const options = trade.acceptOptions.filter((opt) => !opt.requiresLM || state.lm || naturalLM);
  if (!options.length) {
    toast("Arm LM mode to accept this trade for another team.", "info");
    return;
  }

  const selectedId = Number(teamId) || Number(options.find((opt) => !opt.requiresLM)?.teamId) || Number(options[0].teamId);
  const selected = options.find((opt) => Number(opt.teamId) === selectedId) || options[0];
  const requiresLM = selected.requiresLM && !naturalLM;

  const optionsHtml = options.map((opt) => `
    <option value="${opt.teamId}" ${Number(opt.teamId) === Number(selected.teamId) ? "selected" : ""}>
      ${esc(opt.teamName)}${opt.requiresLM ? " (LM)" : " (you)"}
    </option>
  `).join("");

  const sides = trade.sides.map((side) => `
    <div class="summary-line">
      <span>${esc(side.teamName)}</span>
      <span class="grow">${side.players.map((p) => esc(p.name)).join(", ")}</span>
      <b class="mono">${fmt1(side.value)}</b>
    </div>
  `).join("");

  setModal(`
    <button class="modal-close" data-action="close-modal" aria-label="Close">${icon("close")}</button>
    <div class="modal-head">
      <h2>Accept trade</h2>
      <p>You're accepting this trade on behalf of the selected team. ESPN records the transaction and then runs its normal review.</p>
    </div>
    <div class="modal-body">
      <div class="field">
        <label for="acceptTeam">Accept as</label>
        <select id="acceptTeam">${optionsHtml}</select>
      </div>
      ${sides}
      <div class="callout" id="acceptCallout" ${requiresLM ? "" : "hidden"}>
        ${icon("shield")}
        <span><strong>LM override required.</strong> The account will be promoted to League Manager, the accepting team's owner is used as the member, then the original flag is restored.</span>
      </div>
      ${trade.direction === "outgoing" ? `<div class="callout">${icon("alert")} <span>This is your own proposal. Accepting it as the other team completes the loop without them acting â€” that's the bypass this toolkit already demonstrates.</span></div>` : ""}
    </div>
    <div class="modal-foot">
      <button class="btn btn-ghost" data-action="close-modal">Cancel</button>
      <button class="btn btn-primary" data-action="confirm-accept" data-trade="${esc(trade.id)}">${icon("check")} Accept trade</button>
    </div>
  `, { small: false, onMount: () => updateAcceptCallout(naturalLM) });
}

function updateAcceptCallout(naturalLM) {
  const select = $("#acceptTeam");
  const callout = $("#acceptCallout");
  if (!select || !callout) return;
  const tradeId = $('[data-action="confirm-accept"]')?.dataset.trade;
  const trade = state.data?.pendingTrades?.find((t) => t.id === tradeId);
  const opt = trade?.acceptOptions?.find((o) => Number(o.teamId) === Number(select.value));
  if (!opt) return;
  callout.hidden = !(opt.requiresLM && !naturalLM);
}

async function confirmAccept(tradeId) {
  const select = $("#acceptTeam");
  if (!select) return;
  const teamId = Number(select.value);
  const trade = state.data.pendingTrades.find((t) => t.id === tradeId);
  const option = trade?.acceptOptions.find((o) => Number(o.teamId) === teamId);
  const lm = Boolean(option?.requiresLM) && !state.data.account.isLM;

  const button = $('[data-action="confirm-accept"]');
  if (button) {
    button.disabled = true;
    button.innerHTML = `${icon("clock")} Acceptingâ€¦`;
  }

  try {
    const result = await api("/api/trade/accept", {
      method: "POST",
      body: JSON.stringify({
        account: state.account,
        league: state.league,
        game: state.game,
        season: state.season,
        tradeId,
        teamId,
        lm,
      }),
    });
    closeModal();
    toast(result.message || "Trade accepted.", "success", 6000);
    if (result.restoreWarning) toast(result.restoreWarning, "error", 9000);
    await loadState(true);
  } catch (error) {
    toast(error.message, "error", 8000);
    if (button) {
      button.disabled = false;
      button.innerHTML = `${icon("check")} Accept trade`;
    }
  }
}

function openCancelModal(tradeId) {
  const trade = state.data?.pendingTrades?.find((t) => t.id === tradeId);
  if (!trade) return;
  setModal(`
    <button class="modal-close" data-action="close-modal" aria-label="Close">${icon("close")}</button>
    <div class="modal-head">
      <h2>Cancel trade proposal?</h2>
      <p>This withdraws your offer. The other team will no longer be able to accept it.</p>
    </div>
    <div class="modal-body">
      ${trade.sides.map((side) => `
        <div class="summary-line">
          <span>${esc(side.teamName)}</span>
          <span class="grow">${side.players.map((p) => esc(p.name)).join(", ")}</span>
          <b class="mono">${fmt1(side.value)}</b>
        </div>`).join("")}
    </div>
    <div class="modal-foot">
      <button class="btn btn-ghost" data-action="close-modal">Keep offer</button>
      <button class="btn btn-danger" data-action="confirm-cancel" data-trade="${esc(trade.id)}">${icon("close")} Cancel trade</button>
    </div>
  `, { small: true });
}

async function confirmCancel(tradeId) {
  const button = $('[data-action="confirm-cancel"]');
  if (button) {
    button.disabled = true;
    button.innerHTML = `${icon("clock")} Cancellingâ€¦`;
  }
  try {
    const result = await api("/api/trade/cancel", {
      method: "POST",
      body: JSON.stringify({
        account: state.account,
        league: state.league,
        game: state.game,
        season: state.season,
        tradeId,
        lm: false,
      }),
    });
    closeModal();
    toast(result.message || "Trade cancelled.", "success");
    await loadState(true);
  } catch (error) {
    toast(error.message, "error", 7000);
    if (button) {
      button.disabled = false;
      button.innerHTML = `${icon("close")} Cancel trade`;
    }
  }
}

function openDropModal(playerId) {
  const player = playerById(playerId);
  if (!player) return;
  const team = teamById(player.teamId);
  const isMine = Number(player.teamId) === Number(state.data.myTeamId);
  const naturalLM = state.data.account.isLM;
  const useLM = !isMine && !naturalLM;

  if (!isMine && !state.lm && !naturalLM) {
    toast("Arm LM mode to drop from another roster.", "info");
    return;
  }

  setModal(`
    <button class="modal-close" data-action="close-modal" aria-label="Close">${icon("close")}</button>
    <div class="modal-head">
      <h2>Drop ${esc(player.name)}?</h2>
      <p>${esc(team?.name || "This team")} loses the player immediately and they return to the free-agent pool.</p>
    </div>
    <div class="modal-body">
      <div class="summary-line">
        <img src="${esc(player.headshot)}" alt="" style="width:38px;height:38px;border-radius:10px;object-fit:cover;object-position:top center;background:var(--img-bg)" onerror="this.style.display='none'">
        <span class="grow"><b>${esc(player.name)}</b><br><span class="muted">${esc(player.position)} Â· ${esc(player.proTeam || "â€”")} Â· ${fmt1(player.fppg)} FPPG</span></span>
        <span class="badge">${esc(team?.abbrev || "")}</span>
      </div>
      ${useLM ? `<div class="callout">${icon("shield")} <span><strong>LM override.</strong> Your account will be temporarily promoted to League Manager to drop from ${esc(team?.name || "another team")}, then restored.</span></div>` : ""}
      ${!player.droppable ? `<div class="callout">${icon("alert")} <span>ESPN marks this player as <strong>undroppable</strong>. The request will likely be rejected.</span></div>` : ""}
    </div>
    <div class="modal-foot">
      <button class="btn btn-ghost" data-action="close-modal">Keep player</button>
      <button class="btn btn-danger" data-action="confirm-drop" data-player="${player.id}" data-lm="${useLM ? "1" : "0"}">${icon("drop")} Drop player</button>
    </div>
  `, { small: true });
}

async function confirmDrop(playerId, useLm) {
  const player = playerById(playerId);
  if (!player) return;
  const button = $('[data-action="confirm-drop"]');
  if (button) {
    button.disabled = true;
    button.innerHTML = `${icon("clock")} Droppingâ€¦`;
  }
  try {
    const result = await api("/api/drop", {
      method: "POST",
      body: JSON.stringify({
        account: state.account,
        league: state.league,
        game: state.game,
        season: state.season,
        teamId: player.teamId,
        playerId: player.id,
        lm: Boolean(useLm),
      }),
    });
    state.trade.give.delete(Number(player.id));
    state.trade.receive.delete(Number(player.id));
    closeModal();
    toast(result.message || "Player dropped.", "success");
    if (result.restoreWarning) toast(result.restoreWarning, "error", 9000);
    await loadState(true);
  } catch (error) {
    toast(error.message, "error", 8000);
    if (button) {
      button.disabled = false;
      button.innerHTML = `${icon("drop")} Drop player`;
    }
  }
}

function renderTrades() {
  const trades = state.data?.pendingTrades || [];
  const my = state.data?.myTeamId;

  if (!trades.length) {
    els.content.innerHTML = `
      <div class="section-head">
        <div>
          <h2>${icon("trade")} Pending trades</h2>
          <p>Offers waiting on a response or ESPN's processing window.</p>
        </div>
        ${my ? `<button class="btn btn-primary" data-action="open-trade">${icon("plus")} New trade</button>` : ""}
      </div>
      <div class="empty-state">
        <div class="empty-icon">${icon("trade")}</div>
        <h3>No trades on the board</h3>
        <p>Build an offer, pick players from both rosters, and send it. Accepted offers you're not part of will appear here too.</p>
        ${my ? `<button class="btn btn-primary" data-action="open-trade">${icon("trade")} Propose a trade</button>` : ""}
      </div>
    `;
    return;
  }

  els.content.innerHTML = `
    <div class="section-head">
      <div>
        <h2>${icon("trade")} Pending trades <span class="side-count">${trades.length}</span></h2>
        <p>Accept an offer, review value, or cancel your own proposal.</p>
      </div>
      ${my ? `<button class="btn btn-primary" data-action="open-trade">${icon("plus")} New trade</button>` : ""}
    </div>
    <div class="trade-list">
      ${trades.map((trade) => tradeCard(trade)).join("")}
    </div>
  `;
}

function tradeCard(trade) {
  const sides = trade.sides.slice(0, 2);
  const left = sides[0];
  const right = sides[1];
  const naturalLM = Boolean(state.data.account.isLM);
  const ownOptions = trade.acceptOptions.filter((opt) => !opt.requiresLM);
  const lmOptions = trade.acceptOptions.filter((opt) => opt.requiresLM);

  const acceptButtons = [
    ...ownOptions.map((opt) => `
      <button class="btn btn-primary btn-sm" data-action="open-accept" data-trade="${esc(trade.id)}" data-team="${opt.teamId}">
        ${icon("check")} Accept
      </button>`),
    ...((state.lm || naturalLM)
      ? lmOptions.map((opt) => `
        <button class="btn btn-sm" data-action="open-accept" data-trade="${esc(trade.id)}" data-team="${opt.teamId}">
          ${icon("shield")} Accept as ${esc(teamAbbrev(opt.teamId))}
        </button>`)
      : []),
  ].join("");

  const waitingOn = trade.needsAcceptFrom
    .filter((id) => !trade.acceptOptions.some((opt) => Number(opt.teamId) === Number(id) && !opt.requiresLM))
    .map((id) => teamAbbrev(id));

  const directionLabel = trade.direction === "incoming" ? "Incoming offer"
    : trade.direction === "outgoing" ? "Your offer" : "League trade";

  const dirClass = trade.direction === "incoming" ? "incoming"
    : trade.direction === "outgoing" ? "outgoing" : "";

  const timeLabel = trade.status === "pending" && trade.expirationDate
    ? `${icon("clock")} ${expiresIn(trade.expirationDate) || timeAgo(trade.lastEventDate)}`
    : `${icon("clock")} ${timeAgo(trade.lastEventDate)}`;

  const statusCopy = trade.status === "processing"
    ? `<span class="badge accent">Accepted â€” awaiting processing</span>`
    : "";

  return `
    <article class="trade-card">
      <div class="trade-card-head">
        <span class="status-dot ${trade.status === "processing" ? "processing" : ""}"></span>
        <div class="trade-card-title">
          ${esc(directionLabel)}
          <span class="trade-direct ${dirClass}">${trade.direction === "incoming" ? "to you" : trade.direction === "outgoing" ? "from you" : "league"}</span>
          <span class="muted">Â· ${esc(trade.proposerTeam)} proposed</span>
        </div>
        ${statusCopy}
        <div class="trade-card-time">${timeLabel}</div>
      </div>

      <div class="trade-sides">
        ${left ? tradeSide(left) : ""}
        <div class="trade-mid">${icon("trade")}</div>
        ${right ? tradeSide(right) : ""}
      </div>

      <div class="trade-foot">
        <div class="fairness">
          ${fairnessBar(left?.value || 0, right?.value || 0)}
          <div class="fairness-copy">
            <span>${esc(left?.teamName || "")} <b>${fmt1(left?.value)}</b></span>
            <span><b>${fmt1(right?.value)}</b> ${esc(right?.teamName || "")}</span>
          </div>
        </div>
        <div class="trade-actions">
          ${acceptButtons}
          ${trade.canCancel ? `<button class="btn btn-ghost btn-sm" data-action="open-cancel" data-trade="${esc(trade.id)}">${icon("close")} Cancel</button>` : ""}
          ${!acceptButtons && trade.status === "pending" && waitingOn.length
            ? `<span class="badge">Waiting on ${esc(waitingOn.join(", "))}</span>` : ""}
        </div>
      </div>
    </article>
  `;
}

function tradeSide(side) {
  return `
    <div class="trade-side ${side.isMine ? "is-mine" : ""} ${side.accepted ? "accepted" : ""}">
      <div class="trade-side-head">
        ${side.teamLogo ? `<img src="${esc(side.teamLogo)}" alt="" onerror="this.style.display='none'">` : ""}
        <span class="trade-side-team">${esc(side.teamName)}${side.isMine ? " (you)" : ""}</span>
        ${side.accepted ? `<span class="trade-side-tag">${icon("check")} accepted</span>` : ""}
      </div>
      <div class="trade-side-players">
        ${side.players.map((player) => `
          <div class="trade-player">
            ${player.headshot ? `<img src="${esc(player.headshot)}" alt="" loading="lazy" onerror="this.style.display='none'">` : ""}
            <span class="trade-player-name">${esc(player.name)}</span>
            <span class="trade-player-pos">${esc(player.position || "")}</span>
            <span class="trade-player-fppg">${fmt1(player.fppg)}</span>
          </div>`).join("")}
      </div>
    </div>
  `;
}

function renderActivity() {
  const activity = state.data?.activity || [];
  if (!activity.length) {
    els.content.innerHTML = `
      <div class="section-head">
        <div><h2>${icon("activity")} League activity</h2><p>Completed trades and roster moves.</p></div>
      </div>
      <div class="empty-state">
        <div class="empty-icon">${icon("activity")}</div>
        <h3>Nothing here yet</h3>
        <p>Once trades process or players move, the timeline fills in.</p>
      </div>`;
    return;
  }

  els.content.innerHTML = `
    <div class="section-head">
      <div><h2>${icon("activity")} League activity</h2><p>Completed trades, drops, and roster moves, newest first.</p></div>
    </div>
    <div class="feed">
      ${activity.map((item) => activityItem(item)).join("")}
    </div>
  `;
}

function activityItem(item) {
  const isTrade = item.kind === "trade";
  const chips = [];
  for (const side of item.sides || []) {
    for (const player of side.players || []) {
      const destination = player.toTeamId
        ? `<span class="to">â†’ ${esc(teamAbbrev(player.toTeamId))}</span>`
        : player.fromTeamId ? `<span class="to">released</span>` : "";
      chips.push(`
        <span class="feed-chip">
          ${player.headshot ? `<img src="${esc(player.headshot)}" alt="" loading="lazy" onerror="this.style.display='none'">` : ""}
          ${esc(player.name)} ${destination}
        </span>`);
    }
  }

  return `
    <div class="feed-item">
      <div class="feed-dot ${isTrade ? "trade" : "roster"}">${icon(isTrade ? "trade" : "drop")}</div>
      <div class="feed-body">
        <div class="feed-title">
          ${esc(item.title)}
          <span class="badge">${isTrade ? "trade" : "roster"}</span>
          <span class="feed-time">${timeAgo(item.date)}</span>
        </div>
        <div class="feed-sub">${esc(item.subtitle || "")}</div>
        ${chips.length ? `<div class="feed-players">${chips.join("")}</div>` : ""}
      </div>
    </div>
  `;
}

function openSettings() {
  const config = state.config;
  if (!config) return;
  setModal(`
    <button class="modal-close" data-action="close-modal" aria-label="Close">${icon("close")}</button>
    <div class="modal-head">
      <h2>League connection</h2>
      <p>Point the dashboard at a league and account. Cookie files stay local â€” the server only reads cookies/&lt;account&gt;.txt.</p>
    </div>
    <form id="settingsForm">
      <div class="modal-body">
        <div class="settings-grid">
          <div class="field">
            <label for="settingsAccount">Account</label>
            <select id="settingsAccount" name="account">
              ${config.accounts.map((name) => `<option value="${esc(name)}" ${name === state.account ? "selected" : ""}>${esc(name)}</option>`).join("")}
            </select>
            <small>File stem in the cookies directory.</small>
          </div>
          <div class="field">
            <label for="settingsLeague">League ID</label>
            <input id="settingsLeague" name="league" value="${esc(state.league)}" inputmode="numeric" required>
            <small>Found in the ESPN league URL.</small>
          </div>
          <div class="field">
            <label for="settingsGame">Sport</label>
            <select id="settingsGame" name="game">
              ${config.games.map((game) => `<option value="${esc(game.id)}" ${game.id === state.game ? "selected" : ""}>${esc(game.label)}</option>`).join("")}
            </select>
          </div>
          <div class="field">
            <label for="settingsSeason">Season</label>
            <input id="settingsSeason" name="season" value="${esc(state.season)}" inputmode="numeric" required>
            <small>ESPN season id, e.g. 2027.</small>
          </div>
        </div>
        <div class="callout">
          ${icon("shield")}
          <span><strong>Local only.</strong> The dashboard listens on 127.0.0.1. Cookies are read from <span class="mono">${esc(config.cookiesDir)}</span> and never returned to the browser.</span>
        </div>
      </div>
      <div class="modal-foot">
        <button type="button" class="btn btn-ghost" data-action="close-modal">Cancel</button>
        <button type="submit" class="btn btn-primary">${icon("check")} Apply</button>
      </div>
    </form>
  `, { small: true });
}

async function saveSettings(form) {
  const fd = new FormData(form);
  const account = String(fd.get("account") || "").trim();
  const league = String(fd.get("league") || "").trim();
  const game = String(fd.get("game") || "").trim();
  const season = String(fd.get("season") || "").trim();
  if (!account || !league || !game || !season) {
    toast("Fill in all four fields.", "error");
    return;
  }
  state.account = account;
  state.league = league;
  state.game = game;
  state.season = season;
  state.teamId = null;
  state.trade.open = false;
  state.trade.give.clear();
  state.trade.receive.clear();
  state.trade.partnerId = null;
  state.trade.comment = "";
  state.data = null;
  state.error = null;
  localStorage.setItem(LS.account, account);
  localStorage.setItem(LS.league, league);
  localStorage.setItem(LS.game, game);
  localStorage.setItem(LS.season, season);
  els.accountSelect.value = account;
  els.searchInput.value = "";
  state.search = "";
  closeModal();
  els.content.innerHTML = skeletonMarkup();
  await loadState();
}

function skeletonMarkup() {
  return `
    <div class="loading-grid">
      <div class="skeleton skeleton-hero"></div>
      <div class="skeleton-grid">
        ${Array.from({ length: 8 }, () => `<div class="skeleton skeleton-card"></div>`).join("")}
      </div>
    </div>`;
}

function setModal(html, options = {}) {
  els.modalRoot.innerHTML = `
    <div class="modal-backdrop">
      <div class="modal ${options.small ? "is-small" : ""}" role="dialog" aria-modal="true">
        ${html}
      </div>
    </div>`;
  document.body.style.overflow = "hidden";
  const focusable = els.modalRoot.querySelector(".modal select, .modal input, .modal textarea, .modal .modal-close");
  setTimeout(() => focusable?.focus({ preventScroll: true }), 40);
  if (options.onMount) options.onMount();
}

function closeModal() {
  els.modalRoot.innerHTML = "";
  document.body.style.overflow = "";
}

async function handleClick(event) {
  const target = event.target.closest("[data-action]");
  if (!target) return;
  const action = target.dataset.action;

  if (action === "modal-backdrop") {
    if (event.target === target) closeModal();
    return;
  }

  switch (action) {
    case "select-team": {
      const id = Number(target.dataset.team);
      if (!teamById(id)) return;
      state.teamId = id;
      state.search = "";
      els.searchInput.value = "";
      state.tab = "rosters";
      localStorage.setItem(LS.team, String(id));
      render();
      window.scrollTo({ top: 0, behavior: "smooth" });
      break;
    }
    case "filter-position":
      state.position = target.dataset.pos || "ALL";
      renderMain();
      break;
    case "clear-search":
      state.search = "";
      els.searchInput.value = "";
      renderMain();
      break;
    case "player":
      openPlayer(Number(target.dataset.player));
      break;
    case "toggle-trade":
      event.stopPropagation();
      toggleTradePlayer(Number(target.dataset.player));
      break;
    case "add-to-trade":
      closeModal();
      toggleTradePlayer(Number(target.dataset.player));
      break;
    case "open-trade":
      openTrade(target.dataset.team ? Number(target.dataset.team) : null);
      break;
    case "open-my-team":
      if (state.data?.myTeamId) {
        state.teamId = state.data.myTeamId;
        render();
      }
      break;
    case "close-trade":
      closeTrade();
      break;
    case "pick-side":
      pickSide(target.dataset.side);
      break;
    case "done-picking":
      state.trade.picking = null;
      renderMain();
      break;
    case "remove-trade": {
      const set = target.dataset.side === "give" ? state.trade.give : state.trade.receive;
      set.delete(Number(target.dataset.player));
      renderMain();
      renderTradePanel();
      break;
    }
    case "propose-trade":
      await proposeTrade();
      break;
    case "open-accept":
      openAcceptModal(target.dataset.trade, Number(target.dataset.team) || null);
      break;
    case "confirm-accept":
      await confirmAccept(target.dataset.trade);
      break;
    case "open-cancel":
      openCancelModal(target.dataset.trade);
      break;
    case "confirm-cancel":
      await confirmCancel(target.dataset.trade);
      break;
    case "open-drop":
      openDropModal(Number(target.dataset.player));
      break;
    case "confirm-drop":
      await confirmDrop(Number(target.dataset.player), target.dataset.lm === "1");
      break;
    case "close-modal":
      closeModal();
      break;
    case "refresh":
      await loadState(true);
      break;
    case "settings":
      openSettings();
      break;
    case "toggle-theme":
      toggleTheme();
      break;
    case "toggle-lm":
      setLM(!state.lm);
      break;
    default:
      break;
  }
}

function handleChange(event) {
  const el = event.target;
  if (el.id === "tradePartner") {
    state.trade.partnerId = Number(el.value) || null;
    state.trade.receive.clear();
    renderTradePanel();
    if (state.trade.picking === "receive") {
      state.teamId = state.trade.partnerId;
    }
    renderMain();
  }
  if (el.id === "acceptTeam" && state.data) {
    updateAcceptCallout(Boolean(state.data.account.isLM));
  }
  if (el.id === "sortSelect") {
    state.sort = el.value;
    renderMain();
  }
}

function handleInput(event) {
  if (event.target.id === "tradeComment") {
    state.trade.comment = event.target.value;
  }
}

function handleSubmit(event) {
  if (event.target.id === "settingsForm") {
    event.preventDefault();
    saveSettings(event.target);
  }
}

document.addEventListener("DOMContentLoaded", init);

