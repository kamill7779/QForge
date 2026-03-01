const canvas = document.getElementById("shot-canvas");
const ctx = canvas.getContext("2d");
const captureBox = document.getElementById("capture-box");
const dragBar = document.getElementById("capture-drag-bar");
const resizeHandle = document.getElementById("capture-resize-handle");
const tip = document.getElementById("tip");
const confirmBtn = document.getElementById("confirm-btn");
const cancelBtn = document.getElementById("cancel-btn");

let screenshotImage = null;
let viewportWidth = 0;
let viewportHeight = 0;
let dpr = 1;

let box = { x: 0, y: 0, w: 0, h: 0 };
let mode = "idle";
let pointerStart = { x: 0, y: 0 };
let boxStart = { x: 0, y: 0, w: 0, h: 0 };

const MIN_W = 80;
const MIN_H = 50;

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
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

function initBoxRect() {
  const w = Math.round(viewportWidth * 0.36);
  const h = Math.round(viewportHeight * 0.24);
  box.w = clamp(w, MIN_W, Math.max(MIN_W, viewportWidth - 40));
  box.h = clamp(h, MIN_H, Math.max(MIN_H, viewportHeight - 40));
  box.x = Math.round((viewportWidth - box.w) / 2);
  box.y = Math.round(viewportHeight * 0.14);

  if (box.y + box.h > viewportHeight - 20) {
    box.y = Math.max(20, viewportHeight - box.h - 20);
  }
}

function syncBoxElement() {
  captureBox.style.left = `${box.x}px`;
  captureBox.style.top = `${box.y}px`;
  captureBox.style.width = `${box.w}px`;
  captureBox.style.height = `${box.h}px`;
}

function draw() {
  if (!screenshotImage) return;

  ctx.clearRect(0, 0, viewportWidth, viewportHeight);
  ctx.drawImage(screenshotImage, 0, 0, viewportWidth, viewportHeight);

  // Keep the selected area as a transparent hole by shading only outside.
  ctx.fillStyle = "rgba(0, 0, 0, 0.42)";
  ctx.fillRect(0, 0, viewportWidth, box.y);
  ctx.fillRect(0, box.y, box.x, box.h);
  ctx.fillRect(box.x + box.w, box.y, viewportWidth - box.x - box.w, box.h);
  ctx.fillRect(0, box.y + box.h, viewportWidth, viewportHeight - box.y - box.h);

  ctx.strokeStyle = "#1e8dff";
  ctx.lineWidth = 2;
  ctx.strokeRect(box.x + 0.5, box.y + 0.5, box.w - 1, box.h - 1);
}

function refresh() {
  syncBoxElement();
  draw();
}

function pointFromEvent(event) {
  return { x: event.clientX, y: event.clientY };
}

function onPointerMove(event) {
  if (mode === "idle") return;

  const p = pointFromEvent(event);
  const dx = p.x - pointerStart.x;
  const dy = p.y - pointerStart.y;

  if (mode === "move") {
    box.x = clamp(boxStart.x + dx, 0, Math.max(0, viewportWidth - box.w));
    box.y = clamp(boxStart.y + dy, 0, Math.max(0, viewportHeight - box.h));
  } else if (mode === "resize") {
    const maxW = Math.max(MIN_W, viewportWidth - boxStart.x);
    const maxH = Math.max(MIN_H, viewportHeight - boxStart.y);
    box.w = clamp(boxStart.w + dx, MIN_W, maxW);
    box.h = clamp(boxStart.h + dy, MIN_H, maxH);
  }

  refresh();
}

function stopPointer() {
  mode = "idle";
  window.removeEventListener("mousemove", onPointerMove);
  window.removeEventListener("mouseup", stopPointer);
}

function beginPointer(nextMode, event) {
  event.preventDefault();
  mode = nextMode;
  pointerStart = pointFromEvent(event);
  boxStart = { ...box };
  window.addEventListener("mousemove", onPointerMove);
  window.addEventListener("mouseup", stopPointer);
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
  const imageBase64 = cropToBase64();
  window.qforgeScreenshot.confirm({
    imageBase64,
    width: Math.floor(box.w),
    height: Math.floor(box.h)
  });
}

function cancelCapture() {
  window.qforgeScreenshot.cancel();
}

window.qforgeScreenshot.onInit((payload) => {
  const imageDataUrl = payload.imageDataUrl || "";
  const shortcut = payload.shortcut || "CommandOrControl+Alt+A";
  tip.textContent = `把截图框覆盖题干区域，Enter确认，Esc取消（快捷键 ${shortcut}）`;

  const image = new Image();
  image.onload = () => {
    screenshotImage = image;
    setCanvasSize();
    initBoxRect();
    refresh();
  };
  image.src = imageDataUrl;
});

window.addEventListener("resize", () => {
  if (!screenshotImage) return;
  setCanvasSize();

  box.w = clamp(box.w, MIN_W, Math.max(MIN_W, viewportWidth - box.x));
  box.h = clamp(box.h, MIN_H, Math.max(MIN_H, viewportHeight - box.y));
  box.x = clamp(box.x, 0, Math.max(0, viewportWidth - box.w));
  box.y = clamp(box.y, 0, Math.max(0, viewportHeight - box.h));

  refresh();
});

dragBar.addEventListener("mousedown", (event) => beginPointer("move", event));
resizeHandle.addEventListener("mousedown", (event) => beginPointer("resize", event));
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
