const API_BASE = "http://127.0.0.1:9581";

// Create context menu on install
chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: "fluxdm-download",
    title: "Download with FluxDM",
    contexts: ["link"]
  });
});

// Handle context menu click
chrome.contextMenus.onClicked.addListener((info, tab) => {
  if (info.menuItemId === "fluxdm-download" && info.linkUrl) {
    sendToFluxDM(info.linkUrl, tab.id);
  }
});

// Handle messages from popup
chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg.action === "ping") {
    fetch(`${API_BASE}/api/ping`)
      .then(r => r.json())
      .then(data => sendResponse({ ok: true, data }))
      .catch(err => sendResponse({ ok: false, error: err.message }));
    return true; // async response
  }
  if (msg.action === "download" && msg.url) {
    fetch(`${API_BASE}/api/download`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ url: msg.url })
    })
      .then(r => r.json())
      .then(data => sendResponse({ ok: true, data }))
      .catch(err => sendResponse({ ok: false, error: err.message }));
    return true;
  }
});

function sendToFluxDM(url, tabId) {
  fetch(`${API_BASE}/api/download`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ url })
  })
    .then(r => {
      if (!r.ok) throw new Error("Server error " + r.status);
      return r.json();
    })
    .then(() => showBadge(tabId, "OK", "#22c55e"))
    .catch(() => showBadge(tabId, "!", "#ef4444"));
}

function showBadge(tabId, text, color) {
  chrome.action.setBadgeText({ text, tabId });
  chrome.action.setBadgeBackgroundColor({ color, tabId });
  setTimeout(() => chrome.action.setBadgeText({ text: "", tabId }), 2000);
}
