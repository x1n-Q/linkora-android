const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");

const host = process.env.HOST || "127.0.0.1";
const port = Number(process.env.PORT || 8787);
const publicRoot = path.join(__dirname, "public");
const startedAt = Date.now();
const messages = [
  {
    id: 1,
    text: "This message came from the Node.js API running on your phone.",
    createdAt: new Date().toISOString(),
  },
];
let nextMessageId = 2;
let requestCount = 0;

const contentTypes = {
  ".css": "text/css; charset=utf-8",
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".svg": "image/svg+xml",
};

function sendJson(response, status, body) {
  response.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
  });
  response.end(JSON.stringify(body));
}

function readJson(request) {
  return new Promise((resolve, reject) => {
    let body = "";
    request.setEncoding("utf8");
    request.on("data", (chunk) => {
      body += chunk;
      if (body.length > 16_384) {
        reject(new Error("Request body is too large."));
        request.destroy();
      }
    });
    request.on("end", () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch {
        reject(new Error("Request body must be valid JSON."));
      }
    });
    request.on("error", reject);
  });
}

function serveStatic(pathname, response) {
  const requestedPath = pathname === "/" ? "index.html" : pathname.slice(1);
  const filePath = path.resolve(publicRoot, requestedPath);

  if (!filePath.startsWith(`${publicRoot}${path.sep}`)) {
    response.writeHead(403).end("Forbidden");
    return;
  }

  fs.readFile(filePath, (error, content) => {
    if (error) {
      response.writeHead(error.code === "ENOENT" ? 404 : 500).end("Not found");
      return;
    }
    response.writeHead(200, {
      "Content-Type": contentTypes[path.extname(filePath)] || "application/octet-stream",
      "Cache-Control": "no-store",
    });
    response.end(content);
  });
}

const server = http.createServer(async (request, response) => {
  requestCount += 1;
  const url = new URL(request.url, `http://${host}:${port}`);

  if (request.method === "GET" && url.pathname === "/api/status") {
    sendJson(response, 200, {
      status: "online",
      runtime: process.version,
      platform: `${process.platform}/${process.arch}`,
      uptimeSeconds: Math.floor((Date.now() - startedAt) / 1000),
      requestCount,
      timestamp: new Date().toISOString(),
    });
    return;
  }

  if (request.method === "GET" && url.pathname === "/api/messages") {
    sendJson(response, 200, { messages });
    return;
  }

  if (request.method === "POST" && url.pathname === "/api/messages") {
    try {
      const body = await readJson(request);
      const text = typeof body.text === "string" ? body.text.trim() : "";
      if (!text || text.length > 140) {
        sendJson(response, 400, { error: "Message must contain 1 to 140 characters." });
        return;
      }
      const message = {
        id: nextMessageId++,
        text,
        createdAt: new Date().toISOString(),
      };
      messages.unshift(message);
      sendJson(response, 201, { message });
    } catch (error) {
      sendJson(response, 400, { error: error.message });
    }
    return;
  }

  if (request.method === "GET" && url.pathname === "/api/hello") {
    const name = (url.searchParams.get("name") || "developer").slice(0, 40);
    sendJson(response, 200, { message: `Hello, ${name}.`, servedBy: "Linkora Android" });
    return;
  }

  if (request.method === "GET") {
    serveStatic(url.pathname, response);
    return;
  }

  sendJson(response, 404, { error: "Route not found." });
});

server.listen(port, host, () => {
  console.log(`Linkora Node sample listening at http://${host}:${port}`);
});
