const dot = document.getElementById("dot");
const statusText = document.getElementById("statusText");
const urlInput = document.getElementById("urlInput");
const sendBtn = document.getElementById("sendBtn");
const feedback = document.getElementById("feedback");

let connected = false;

// Check connection on popup open
chrome.runtime.sendMessage({ action: "ping" }, (res) => {
  if (res && res.ok) {
    connected = true;
    dot.classList.add("connected");
    statusText.textContent = "Connected to FluxDM";
    sendBtn.disabled = false;
  } else {
    statusText.textContent = "FluxDM is not running";
  }
});

sendBtn.addEventListener("click", () => {
  const url = urlInput.value.trim();
  if (!url) {
    showFeedback("Please enter a URL", "err");
    return;
  }
  sendBtn.disabled = true;
  feedback.textContent = "";

  chrome.runtime.sendMessage({ action: "download", url }, (res) => {
    if (res && res.ok) {
      showFeedback("Sent to FluxDM!", "ok");
      urlInput.value = "";
    } else {
      showFeedback("Failed: " + (res?.error || "Unknown error"), "err");
    }
    sendBtn.disabled = !connected;
  });
});

urlInput.addEventListener("keydown", (e) => {
  if (e.key === "Enter") sendBtn.click();
});

function showFeedback(msg, type) {
  feedback.textContent = msg;
  feedback.className = "feedback " + type;
}
