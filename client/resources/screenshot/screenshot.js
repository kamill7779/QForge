const canvas = document.getElementById("shot-canvas");
const ctx = canvas.getContext("2d");
const tip = document.getElementById("tip");
const confirmBtn = document.getElementById("confirm-btn");
const cancelBtn = document.getElementById("cancel-btn");

let screenshotImage = null;
let viewportWidth = 0;
let viewportHeight = 0;
let dpr = 1;

let box = { x: 0, y: 0, w: 0, h: 0 };
let mode = "idle"; // idle | select | move | resize
let activeHandle = "";
let pointerStart = { x: 0, y: 0 };
let boxStart = { x: 0, y: 0, w: 0, h: 0 };

const MIN_W = 80;
const MIN_H = 50;
const HANDLE_HALF = 5;
const HANDLE_HIT = 10;

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

function hasSelection() {
  return box.w > 0 && box.h > 0;
}

function setCanvasSize() {
  viewportWidth = window.innerWidth;
  viewportHeight = window.innerHeight;
  dpr = window.devicePixelRatio || 1;

  canvas.width = Math.max(1, Math.floor(viewportWidth * dpr));
  canvas.height = Math.max(1, Math.floor(viewportHeight * dpr));
  canvas.style.width = `${viewportWidth}px`;
  canvas.style.height = `${viewportHeight}px`;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
}

function pointFromEvent(event) {
  return { x: event.clientX, y: event.clientY };
}

function pointInRect(p, rect) {
  return p.x >= rect.x && p.x <= rect.x + rect.w && p.y >= rect.y && p.y <= rect.y + rect.h;
}

function getHandlePoints() {
  if (!hasSelection()) return [];
  const cx = box.x + box.w / 2;
  const cy = box.y + box.h / 2;
  const right = box.x + box.w;
  const bottom = box.y + box.h;
  return [
    { key: "nw", x: box.x, y: box.y },
    { key: "n", x: cx, y: box.y },
    { key: "ne", x: right, y: box.y },
    { key: "e", x: right, y: cy },
    { key: "se", x: right, y: bottom },
    { key: "s", x: cx, y: bottom },
    { key: "sw", x: box.x, y: bottom },
    { key: "w", x: box.x, y: cy }
  ];
}

function hitTest(p) {
  if (!hasSelection()) return { type: "none" };

  for (const handle of getHandlePoints()) {
    if (
      Math.abs(p.x - handle.x) <= HANDLE_HIT &&
      Math.abs(p.y - handle.y) <= HANDLE_HIT
    ) {
      return { type: "handle", handle: handle.key };
    }
  }

  if (pointInRect(p, box)) return { type: "move" };
  return { type: "outside" };
}

function cursorForHit(hit) {
  if (!hit || hit.type === "none" || hit.type === "outside") return "crosshair";
  if (hit.type === "move") return "move";
  switch (hit.handle) {
    case "nw":
    case "se":
      return "nwse-resize";
    case "ne":
    case "sw":
      return "nesw-resize";
    case "n":
    case "s":
      return "ns-resize";
    case "e":
    case "w":
      return "ew-resize";
    default:
      return "crosshair";
  }
}

function updateCursorFromPoint(p) {
  if (mode !== "idle") return;
  canvas.style.cursor = cursorForHit(hitTest(p));
}

function drawHandles() {
  for (const handle of getHandlePoints()) {
    ctx.fillStyle = "#ffffff";
    ctx.strokeStyle = "#1e8dff";
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.rect(
      Math.round(handle.x - HANDLE_HALF) + 0.5,
      Math.round(handle.y - HANDLE_HALF) + 0.5,
      HANDLE_HALF * 2,
      HANDLE_HALF * 2
    );
    ctx.fill();
    ctx.stroke();
  }
}

function draw() {
  if (!screenshotImage) return;
  ctx.clearRect(0, 0, viewportWidth, viewportHeight);
  ctx.drawImage(screenshotImage, 0, 0, viewportWidth, viewportHeight);

  ctx.fillStyle = "rgba(0, 0, 0, 0.42)";
  if (!hasSelection()) {
    ctx.fillRect(0, 0, viewportWidth, viewportHeight);
    return;
  }

  const right = box.x + box.w;
  const bottom = box.y + box.h;
  ctx.fillRect(0, 0, viewportWidth, box.y);
  ctx.fillRect(0, box.y, box.x, box.h);
  ctx.fillRect(right, box.y, viewportWidth - right, box.h);
  ctx.fillRect(0, bottom, viewportWidth, viewportHeight - bottom);

  ctx.strokeStyle = "#1e8dff";
  ctx.lineWidth = 2;
  ctx.strokeRect(
    Math.round(box.x) + 0.5,
    Math.round(box.y) + 0.5,
    Math.max(1, Math.round(box.w) - 1),
    Math.max(1, Math.round(box.h) - 1)
  );
  drawHandles();
}

function refresh() {
  draw();
}

function normalizeSelectionRect(x1, y1, x2, y2) {
  const left = clamp(Math.min(x1, x2), 0, viewportWidth);
  const top = clamp(Math.min(y1, y2), 0, viewportHeight);
  const right = clamp(Math.max(x1, x2), 0, viewportWidth);
  const bottom = clamp(Math.max(y1, y2), 0, viewportHeight);
  box.x = left;
  box.y = top;
  box.w = Math.max(0, right - left);
  box.h = Math.max(0, bottom - top);
}

function resizeByHandle(p) {
  let left = boxStart.x;
  let top = boxStart.y;
  let right = boxStart.x + boxStart.w;
  let bottom = boxStart.y + boxStart.h;

  if (activeHandle.includes("w")) left = clamp(p.x, 0, right - MIN_W);
  if (activeHandle.includes("e")) right = clamp(p.x, left + MIN_W, viewportWidth);
  if (activeHandle.includes("n")) top = clamp(p.y, 0, bottom - MIN_H);
  if (activeHandle.includes("s")) bottom = clamp(p.y, top + MIN_H, viewportHeight);

  box.x = left;
  box.y = top;
  box.w = Math.max(0, right - left);
  box.h = Math.max(0, bottom - top);
}

function onPointerMove(event) {
  if (mode === "idle") return;
  const p = pointFromEvent(event);
  const dx = p.x - pointerStart.x;
  const dy = p.y - pointerStart.y;

  if (mode === "select") {
    normalizeSelectionRect(pointerStart.x, pointerStart.y, p.x, p.y);
  } else if (mode === "move") {
    box.x = clamp(boxStart.x + dx, 0, Math.max(0, viewportWidth - boxStart.w));
    box.y = clamp(boxStart.y + dy, 0, Math.max(0, viewportHeight - boxStart.h));
    box.w = boxStart.w;
    box.h = boxStart.h;
  } else if (mode === "resize") {
    resizeByHandle(p);
  }

  refresh();
}

function stopPointer(event) {
  if (mode === "select" && (box.w < MIN_W || box.h < MIN_H)) {
    box = { x: 0, y: 0, w: 0, h: 0 };
  }
  mode = "idle";
  activeHandle = "";
  window.removeEventListener("mousemove", onPointerMove);
  window.removeEventListener("mouseup", stopPointer);
  if (event) updateCursorFromPoint(pointFromEvent(event));
}

function beginPointer(event) {
  if (event.button !== 0 || !screenshotImage) return;
  event.preventDefault();
  const p = pointFromEvent(event);
  pointerStart = p;
  boxStart = { ...box };

  const hit = hitTest(p);
  if (hit.type === "handle") {
    mode = "resize";
    activeHandle = hit.handle;
    canvas.style.cursor = cursorForHit(hit);
  } else if (hit.type === "move") {
    mode = "move";
    canvas.style.cursor = "move";
  } else {
    mode = "select";
    activeHandle = "";
    box = { x: p.x, y: p.y, w: 0, h: 0 };
    canvas.style.cursor = "crosshair";
  }

  window.addEventListener("mousemove", onPointerMove);
  window.addEventListener("mouseup", stopPointer);
  refresh();
}

function cropToBase64() {
  const scaleX = screenshotImage.naturalWidth / viewportWidth;
  const scaleY = screenshotImage.naturalHeight / viewportHeight;

  const sx = Math.max(0, Math.floor(box.x * scaleX));
  const sy = Math.max(0, Math.floor(box.y * scaleY));
  const sw = Math.max(1, Math.floor(box.w * scaleX));
  const sh = Math.max(1, Math.floor(box.h * scaleY));

  const out = document.createElement("canvas");
  out.width = sw;
  out.height = sh;
  const outCtx = out.getContext("2d");
  outCtx.drawImage(screenshotImage, sx, sy, sw, sh, 0, 0, sw, sh);

  const dataUrl = out.toDataURL("image/png");
  const idx = dataUrl.indexOf(",");
  return idx >= 0 ? dataUrl.slice(idx + 1) : dataUrl;
}

function confirmCapture() {
  if (!screenshotImage) return;
  if (!hasSelection() || box.w < MIN_W || box.h < MIN_H) {
    tip.textContent = "请先按住左键框选有效区域（至少 80x50）";
    return;
  }
  const imageBase64 = cropToBase64();
  window.qforgeScreenshot.confirm({
    imageBase64,
    width: Math.floor(box.w),
    height: Math.floor(box.h),
    intent: pendingIntent
  });
}

function cancelCapture() {
  window.qforgeScreenshot.cancel();
}

let pendingIntent = 'ocr';

window.qforgeScreenshot.onInit((payload) => {
  const imageDataUrl = payload.imageDataUrl || "";
  const shortcut = payload.shortcut || "CommandOrControl+Alt+A";
  pendingIntent = payload.intent || 'ocr';
  tip.textContent = `按住左键框选，拖动可调整，Enter确认，Esc取消（快捷键 ${shortcut}）`;

  const image = new Image();
  image.onload = () => {
    screenshotImage = image;
    setCanvasSize();
    box = { x: 0, y: 0, w: 0, h: 0 };
    canvas.style.cursor = "crosshair";
    refresh();
  };
  image.src = imageDataUrl;
});

window.addEventListener("resize", () => {
  if (!screenshotImage) return;
  setCanvasSize();
  if (hasSelection()) {
    box.x = clamp(box.x, 0, viewportWidth);
    box.y = clamp(box.y, 0, viewportHeight);
    box.w = clamp(box.w, 0, Math.max(0, viewportWidth - box.x));
    box.h = clamp(box.h, 0, Math.max(0, viewportHeight - box.y));
  }
  refresh();
});

canvas.addEventListener("mousedown", beginPointer);
canvas.addEventListener("mousemove", (event) => {
  if (!screenshotImage) return;
  if (mode === "idle") {
    updateCursorFromPoint(pointFromEvent(event));
  }
});
confirmBtn.addEventListener("click", confirmCapture);
cancelBtn.addEventListener("click", cancelCapture);

window.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    cancelCapture();
    return;
  }
  if (event.key === "Enter") {
    confirmCapture();
  }
});

window.addEventListener("contextmenu", (event) => {
  event.preventDefault();
  cancelCapture();
});
