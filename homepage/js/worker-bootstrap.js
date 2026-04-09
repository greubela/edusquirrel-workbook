self.__workerServerStarted = false;

self.onmessage = (event) => {
  const msg = event.data || {};
  if (msg.kind !== 'init-server') return;

  if (self.__workerServerStarted) {
    self.postMessage({ kind: 'server-ready' });
    return;
  }

  try {
    importScripts(msg.moduleUrl);
    const starter = self[msg.exportedName];
    if (typeof starter !== 'function') {
      throw new Error(`Export '${msg.exportedName}' not found on worker global scope.`);
    }
    starter();
    self.__workerServerStarted = true;
    self.postMessage({ kind: 'server-ready' });
  } catch (err) {
    const message = err && err.message ? err.message : String(err);
    self.postMessage({ kind: 'server-failed', error: message });
  }
};
