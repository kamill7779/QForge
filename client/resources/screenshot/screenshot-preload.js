const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("qforgeScreenshot", {
  onInit: (handler) => {
    const wrapped = (_event, payload) => handler(payload || {});
    ipcRenderer.on("screenshot:init", wrapped);
    return () => ipcRenderer.removeListener("screenshot:init", wrapped);
  },
  confirm: (payload) => ipcRenderer.send("screenshot:confirm", payload || {}),
  cancel: () => ipcRenderer.send("screenshot:cancel")
});
