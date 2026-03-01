const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("qforge", {
  config: {
    get: () => ipcRenderer.invoke("config:get")
  },
  auth: {
    login: (username, password) => ipcRenderer.invoke("auth:login", { username, password })
  },
  api: {
    request: (endpointPath, method, token, body) =>
      ipcRenderer.invoke("api:request", { endpointPath, method, token, body })
  },
  credentials: {
    load: () => ipcRenderer.invoke("credentials:load"),
    save: (payload) => ipcRenderer.invoke("credentials:save", payload),
    clear: () => ipcRenderer.invoke("credentials:clear")
  },
  screenshot: {
    trigger: () => ipcRenderer.invoke("screenshot:trigger"),
    onCaptured: (handler) => {
      const wrapped = (_event, payload) => handler(payload || {});
      ipcRenderer.on("screenshot:captured", wrapped);
      return () => ipcRenderer.removeListener("screenshot:captured", wrapped);
    },
    onError: (handler) => {
      const wrapped = (_event, payload) => handler(payload || {});
      ipcRenderer.on("screenshot:error", wrapped);
      return () => ipcRenderer.removeListener("screenshot:error", wrapped);
    }
  }
});
