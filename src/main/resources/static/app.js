// Minimal vanilla-JS client for the Mortgage Pricing Exception Service demo UI.
// Users/roles are hardcoded to match V2__seed_data.sql; applications are hardcoded too since
// there is no "list applications" endpoint in the API.

const USERS = [
  { id: "rm-1", role: "RELATIONSHIP_MANAGER", label: "Dana Whitfield (RM, rm-1)" },
  { id: "rm-2", role: "RELATIONSHIP_MANAGER", label: "Marcus Ojo (RM, rm-2)" },
  { id: "rev-1", role: "REVIEWER", label: "Priya Kapoor (Reviewer, rev-1)" },
  { id: "rev-2", role: "REVIEWER", label: "Tom Bracewell (Reviewer, rev-2)" },
];

const APPLICATION_IDS = ["app-1001", "app-1002", "app-1003", "app-1004", "app-1005"];

let currentRequest = null;

function currentUser() {
  const id = document.getElementById("userSelect").value;
  return USERS.find((u) => u.id === id);
}

function authHeaders() {
  const user = currentUser();
  return { "X-User-Id": user.id, "X-User-Role": user.role };
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: { ...authHeaders(), ...(options.headers || {}) },
  });
  const text = await response.text();
  let body;
  try {
    body = text ? JSON.parse(text) : null;
  } catch (e) {
    body = text;
  }
  if (!response.ok) {
    const message = body && body.message ? body.message : `HTTP ${response.status}`;
    const error = new Error(message);
    error.body = body;
    error.status = response.status;
    throw error;
  }
  return body;
}

function populateUserSelect() {
  const select = document.getElementById("userSelect");
  select.innerHTML = USERS.map((u) => `<option value="${u.id}">${u.label}</option>`).join("");
}

function populateAppIdList() {
  const list = document.getElementById("appIdList");
  list.innerHTML = APPLICATION_IDS.map((id) => `<option value="${id}"></option>`).join("");
}

async function createRequest() {
  const resultEl = document.getElementById("createResult");
  resultEl.textContent = "";
  const applicationId = document.getElementById("createAppId").value.trim();
  const requestedDiscountBps = parseInt(document.getElementById("createDiscount").value, 10);
  const reason = document.getElementById("createReason").value.trim();

  try {
    const created = await api("/api/requests", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Idempotency-Key": crypto.randomUUID(),
      },
      body: JSON.stringify({ applicationId, requestedDiscountBps, reason }),
    });
    resultEl.style.color = "#1a7a1a";
    resultEl.textContent = `Created request ${created.id} (status ${created.status})`;
    await listRequests();
  } catch (e) {
    resultEl.style.color = "#b00020";
    resultEl.textContent = `Error: ${e.message}`;
  }
}

async function listRequests() {
  const applicationId = document.getElementById("filterAppId").value.trim();
  const status = document.getElementById("filterStatus").value;
  const params = new URLSearchParams();
  if (applicationId) params.set("applicationId", applicationId);
  if (status) params.set("status", status);

  const tbody = document.querySelector("#requestsTable tbody");
  tbody.innerHTML = "";
  try {
    const requests = await api(`/api/requests?${params.toString()}`);
    for (const r of requests) {
      const tr = document.createElement("tr");
      tr.innerHTML = `<td>${r.id.slice(0, 8)}...</td><td>${r.applicationId}</td><td>${r.requestedDiscountBps}</td>` +
        `<td class="status-${r.status}">${r.status}</td><td>${r.createdByUserId}</td>`;
      tr.addEventListener("click", () => showDetail(r.id));
      tbody.appendChild(tr);
    }
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="5" class="error">Error: ${e.message}</td></tr>`;
  }
}

async function showDetail(id) {
  const section = document.getElementById("detailSection");
  section.style.display = "block";
  try {
    const request = await api(`/api/requests/${id}`);
    currentRequest = request;
    renderDetail(request);
    const history = await api(`/api/requests/${id}/history`);
    renderHistory(history);
  } catch (e) {
    document.getElementById("detail").innerHTML = `<p class="error">Error: ${e.message}</p>`;
  }
}

function renderDetail(r) {
  document.getElementById("detail").innerHTML = `<pre>${JSON.stringify(r, null, 2)}</pre>`;

  const user = currentUser();
  const actions = document.getElementById("detailActions");
  actions.innerHTML = "";

  if (r.status === "PENDING" && user.role === "REVIEWER") {
    const approveBtn = document.createElement("button");
    approveBtn.textContent = "Approve";
    approveBtn.onclick = () => decide(r.id, "APPROVE");
    const declineBtn = document.createElement("button");
    declineBtn.textContent = "Decline";
    declineBtn.onclick = () => decide(r.id, "DECLINE");
    actions.append(approveBtn, declineBtn);
  }

  if (r.status === "PENDING" && user.role === "RELATIONSHIP_MANAGER") {
    const withdrawBtn = document.createElement("button");
    withdrawBtn.textContent = "Withdraw";
    withdrawBtn.onclick = () => withdraw(r.id);
    actions.append(withdrawBtn);
  }
}

function renderHistory(events) {
  const tbody = document.querySelector("#historyTable tbody");
  tbody.innerHTML = events
    .map(
      (e) =>
        `<tr><td>${e.eventType}</td><td>${e.actorUserId}</td><td>${e.timestamp}</td><td>${e.notes || ""}</td></tr>`
    )
    .join("");
}

async function decide(id, decision) {
  const reason = prompt(`Reason for ${decision.toLowerCase()} (required for DECLINE)`) || "";
  try {
    await api(`/api/requests/${id}/decision`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ decision, reason }),
    });
    await showDetail(id);
    await listRequests();
  } catch (e) {
    alert(`Error: ${e.message}`);
  }
}

async function withdraw(id) {
  const reason = prompt("Reason for withdrawal (optional)") || "";
  try {
    await api(`/api/requests/${id}/withdraw`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ reason }),
    });
    await showDetail(id);
    await listRequests();
  } catch (e) {
    alert(`Error: ${e.message}`);
  }
}

async function lookupApprovedDiscount() {
  const applicationId = document.getElementById("discountAppId").value.trim();
  const resultEl = document.getElementById("discountResult");
  if (!applicationId) {
    // A blank applicationId collapses the URL to a double slash, which never reaches the
    // controller (it 404s as an unmatched route) - so validate client-side instead of relying
    // on the server to produce a friendly message.
    resultEl.textContent = "Error: Application id is required";
    return;
  }
  try {
    const result = await api(`/api/applications/${applicationId}/approved-discount`);
    resultEl.textContent = JSON.stringify(result, null, 2);
  } catch (e) {
    resultEl.textContent = `Error: ${e.message}`;
  }
}

function init() {
  populateUserSelect();
  populateAppIdList();
  listRequests();
  document.getElementById("createBtn").addEventListener("click", createRequest);
  document.getElementById("listBtn").addEventListener("click", listRequests);
  document.getElementById("discountBtn").addEventListener("click", lookupApprovedDiscount);

}

init();
