const { app, BrowserWindow, desktopCapturer, globalShortcut, ipcMain, screen } = require("electron");
const fs = require("node:fs/promises");
const path = require("node:path");

const API_BASE_URL = process.env.QFORGE_API_BASE_URL || "http://localhost:8080";
const WS_BASE_URL = process.env.QFORGE_WS_BASE_URL || "ws://localhost:8089";
const SCREENSHOT_SHORTCUT = process.env.QFORGE_SCREENSHOT_SHORTCUT || "CommandOrControl+Alt+A";

let mainWindow = null;
let screenshotWindow = null;

function credentialsFilePath() {
  return path.join(app.getPath("userData"), "credentials.json");
}

async function loadCredentials() {
  try {
    const content = await fs.readFile(credentialsFilePath(), "utf8");
    const parsed = JSON.parse(content);
    return {
      remember: Boolean(parsed.remember),
      username: parsed.username || "",
      password: parsed.password || ""
    };
  } catch {
    return { remember: false, username: "", password: "" };
  }
}

async function saveCredentials(payload) {
  const normalized = {
    remember: Boolean(payload && payload.remember),
    username: (payload && payload.username) || "",
    password: (payload && payload.password) || ""
  };
  await fs.mkdir(path.dirname(credentialsFilePath()), { recursive: true });
  await fs.writeFile(credentialsFilePath(), JSON.stringify(normalized, null, 2), "utf8");
  return normalized;
}

async function clearCredentials() {
  try {
    await fs.unlink(credentialsFilePath());
  } catch {
    // Ignore missing file for MVP.
  }
}

async function requestBackend({ endpointPath, method = "GET", token, body }) {
  if (!endpointPath || typeof endpointPath !== "string") {
    throw new Error("endpointPath is required");
  }
  const target = endpointPath.startsWith("http")
    ? endpointPath
    : `${API_BASE_URL}${endpointPath}`;

  const headers = {};
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  let payload;
  if (body !== undefined && body !== null) {
    headers["Content-Type"] = "application/json";
    payload = JSON.stringify(body);
  }

  const response = await fetch(target, {
    method: method.toUpperCase(),
    headers,
    body: payload
  });

  const raw = await response.text();
  let data = null;
  if (raw) {
    try {
      data = JSON.parse(raw);
    } catch {
      data = { raw };
    }
  }

  if (!response.ok) {
    const error = new Error(`Request failed: ${response.status}`);
    error.status = response.status;
    error.data = data;
    throw error;
  }

  return {
    status: response.status,
    data
  };
}

function sendToMainWindow(channel, payload) {
  if (!mainWindow || mainWindow.isDestroyed()) return;
  mainWindow.webContents.send(channel, payload);
}

async function captureTargetDisplay() {
  const cursorPoint = screen.getCursorScreenPoint();
  const targetDisplay = screen.getDisplayNearestPoint(cursorPoint);
  const scaleFactor = targetDisplay.scaleFactor || 1;
  const width = Math.max(1, Math.floor(targetDisplay.bounds.width * scaleFactor));
  const height = Math.max(1, Math.floor(targetDisplay.bounds.height * scaleFactor));

  const sources = await desktopCapturer.getSources({
    types: ["screen"],
    thumbnailSize: { width, height }
  });

  let source = sources.find((item) => item.display_id === String(targetDisplay.id));
  if (!source && sources.length > 0) {
    // Some Windows environments do not expose stable display_id mapping.
    // Fallback to the source whose thumbnail size is closest to target display pixels.
    let bestScore = Number.POSITIVE_INFINITY;
    for (const item of sources) {
      const size = item.thumbnail.getSize();
      const score = Math.abs(size.width - width) + Math.abs(size.height - height);
      if (score < bestScore) {
        bestScore = score;
        source = item;
      }
    }
  }
  if (!source) {
    throw new Error("未获取到可用屏幕源");
  }

  return {
    targetDisplay,
    imageDataUrl: source.thumbnail.toDataURL()
  };
}

function closeScreenshotWindow() {
  if (!screenshotWindow || screenshotWindow.isDestroyed()) {
    screenshotWindow = null;
    return;
  }
  screenshotWindow.close();
  screenshotWindow = null;
}

async function openScreenshotWindow() {
  if (screenshotWindow && !screenshotWindow.isDestroyed()) {
    screenshotWindow.focus();
    return;
  }

  const { targetDisplay, imageDataUrl } = await captureTargetDisplay();

  screenshotWindow = new BrowserWindow({
    x: targetDisplay.bounds.x,
    y: targetDisplay.bounds.y,
    width: targetDisplay.bounds.width,
    height: targetDisplay.bounds.height,
    frame: false,
    transparent: true,
    resizable: false,
    movable: false,
    skipTaskbar: true,
    alwaysOnTop: true,
    fullscreenable: false,
    hasShadow: false,
    webPreferences: {
      preload: path.join(__dirname, "screenshot-preload.js"),
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  screenshotWindow.setMenuBarVisibility(false);
  screenshotWindow.setAlwaysOnTop(true, "screen-saver");
  screenshotWindow.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true });
  screenshotWindow.once("closed", () => {
    screenshotWindow = null;
  });

  await screenshotWindow.loadFile(path.join(__dirname, "screenshot.html"));
  screenshotWindow.show();
  screenshotWindow.focus();
  screenshotWindow.webContents.send("screenshot:init", {
    imageDataUrl,
    shortcut: SCREENSHOT_SHORTCUT
  });
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1520,
    height: 980,
    minWidth: 1280,
    minHeight: 800,
    backgroundColor: "#f1f4fa",
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  mainWindow.loadFile(path.join(__dirname, "..", "src", "index.html"));
}

app.whenReady().then(() => {
  ipcMain.handle("config:get", async () => ({
    apiBaseUrl: API_BASE_URL,
    wsBaseUrl: WS_BASE_URL,
    screenshotShortcut: SCREENSHOT_SHORTCUT
  }));

  ipcMain.handle("auth:login", async (_event, payload) => {
    const { username, password } = payload || {};
    const result = await requestBackend({
      endpointPath: "/api/auth/login",
      method: "POST",
      body: { username, password }
    });

    return result.data;
  });

  ipcMain.handle("api:request", async (_event, payload) => {
    return requestBackend(payload || {});
  });

  ipcMain.handle("credentials:load", loadCredentials);
  ipcMain.handle("credentials:save", async (_event, payload) => saveCredentials(payload || {}));
  ipcMain.handle("credentials:clear", clearCredentials);
  ipcMain.handle("screenshot:trigger", async () => {
    await openScreenshotWindow();
    return { ok: true };
  });

  ipcMain.on("screenshot:cancel", () => {
    closeScreenshotWindow();
  });
  ipcMain.on("screenshot:confirm", (_event, payload) => {
    sendToMainWindow("screenshot:captured", payload || {});
    closeScreenshotWindow();
  });

  createWindow();

  const registered = globalShortcut.register(SCREENSHOT_SHORTCUT, () => {
    openScreenshotWindow().catch((error) => {
      sendToMainWindow("screenshot:error", { message: error.message || String(error) });
    });
  });
  if (!registered) {
    sendToMainWindow("screenshot:error", {
      message: `快捷键注册失败: ${SCREENSHOT_SHORTCUT}`
    });
  }

  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on("will-quit", () => {
  globalShortcut.unregisterAll();
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") {
    app.quit();
  }
});
