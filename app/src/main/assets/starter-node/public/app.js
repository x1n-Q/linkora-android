const output = document.querySelector("#api-output");
const form = document.querySelector("#message-form");
const formStatus = document.querySelector("#form-status");
const messageList = document.querySelector("#message-list");

function showOutput(data) {
  output.textContent = JSON.stringify(data, null, 2);
}

function formatUptime(seconds) {
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  return `${minutes}m ${seconds % 60}s`;
}

async function requestJson(url, options) {
  const response = await fetch(url, options);
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || `Request failed with ${response.status}`);
  return data;
}

async function loadStatus(showResult = false) {
  try {
    const data = await requestJson("/api/status");
    document.querySelector("#runtime").textContent = data.runtime;
    document.querySelector("#platform").textContent = data.platform;
    document.querySelector("#uptime").textContent = formatUptime(data.uptimeSeconds);
    document.querySelector("#requests").textContent = data.requestCount;
    if (showResult) showOutput(data);
  } catch (error) {
    showOutput({ error: error.message });
  }
}

function renderMessages(messages) {
  messageList.replaceChildren(
    ...messages.map((message) => {
      const item = document.createElement("li");
      const text = document.createElement("p");
      const time = document.createElement("time");
      text.textContent = message.text;
      time.dateTime = message.createdAt;
      time.textContent = new Date(message.createdAt).toLocaleTimeString([], {
        hour: "2-digit",
        minute: "2-digit",
      });
      item.append(text, time);
      return item;
    }),
  );
}

async function loadMessages() {
  try {
    const data = await requestJson("/api/messages");
    renderMessages(data.messages);
  } catch (error) {
    formStatus.textContent = error.message;
  }
}

document.querySelector("#test-api").addEventListener("click", () => loadStatus(true));

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const input = form.elements.message;
  const text = input.value.trim();
  if (!text) return;

  formStatus.textContent = "Sending...";
  try {
    const data = await requestJson("/api/messages", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ text }),
    });
    input.value = "";
    formStatus.textContent = "Message accepted by the Node.js API.";
    showOutput(data);
    await Promise.all([loadMessages(), loadStatus()]);
  } catch (error) {
    formStatus.textContent = error.message;
    showOutput({ error: error.message });
  }
});

loadStatus();
loadMessages();
setInterval(() => loadStatus(), 5000);
