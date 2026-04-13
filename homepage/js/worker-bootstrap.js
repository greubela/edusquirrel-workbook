self.__workerServerStarted = false;

self.onmessage = async (event) => {
  const msg = event.data || {};
  if (msg.kind !== 'init-server') return;

  if (self.__workerServerStarted) {
    self.postMessage({ kind: 'server-ready' });
    return;
  }

  try {
    let starter = null;
    let owner = self;

    if (msg.moduleType === 'module') {
      const mod = await import(msg.moduleUrl);
      if (typeof mod?.[msg.exportedName] === 'function') {
        starter = mod[msg.exportedName];
        owner = mod;
      } else {
        starter = self[msg.exportedName];
      }
    } else {
      importScripts(msg.moduleUrl);
      starter = self[msg.exportedName];
    }

    if (typeof starter !== 'function') {
      throw new Error(`Export '${msg.exportedName}' not found.`);
    }

    starter.call(owner);
    self.__workerServerStarted = true;
    self.postMessage({ kind: 'server-ready' });
  } catch (err) {
    const message = err && err.message ? err.message : String(err);
    self.postMessage({ kind: 'server-failed', error: message });
  }
};
