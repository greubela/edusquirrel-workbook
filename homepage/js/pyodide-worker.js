import { loadPyodide } from "https://cdn.jsdelivr.net/pyodide/v0.29.3/full/pyodide.mjs";

let pyodidePromise = null;
let pyodide = null;

const registeredCallbacks = new Map();

let callbackOps = [];
let stdoutBuffer = [];
let stderrBuffer = [];

function ensurePyodide() {
    if (!pyodidePromise) {
        pyodidePromise = loadPyodide().then(instance => {
            pyodide = instance;
            reinstallCallbacks();
            return instance;
        });
    }
    return pyodidePromise;
}

// Start loading immediately when the worker module is evaluated.
const pyodideReady = ensurePyodide();

function ok(id, payload = null) {
    self.postMessage({ id, ok: true, payload });
}

function fail(id, error) {
    self.postMessage({
        id,
        ok: false,
        error: {
            message: error instanceof Error ? error.message : String(error),
            stdout: stdoutBuffer.join(""),
            stderr: stderrBuffer.join("")
        }
    });
}

function clearBuffers() {
    callbackOps = [];
    stdoutBuffer = [];
    stderrBuffer = [];
}

function reinstallCallbacks() {
    if (!pyodide) return;
    for (const [moduleName, methodNames] of registeredCallbacks.entries()) {
        installCallbackModule(moduleName, methodNames);
    }
}

function installCallbackModule(moduleName, methodNames) {
    const moduleObject = {};

    for (const methodName of methodNames) {
        moduleObject[methodName] = (...args) => {
            callbackOps.push({
                module: moduleName,
                method: methodName,
                args
            });
            return undefined;
        };
    }

    pyodide.registerJsModule(moduleName, moduleObject);
}

async function recreatePyodide() {
    pyodidePromise = loadPyodide().then(instance => {
        pyodide = instance;
        reinstallCallbacks();
        return instance;
    });
    await pyodidePromise;
}

function setStreams(captureStdout, captureStderr) {
    pyodide.setStdout({
        batched: captureStdout ? text => stdoutBuffer.push(text) : () => {}
    });

    pyodide.setStderr({
        batched: captureStderr ? text => stderrBuffer.push(text) : () => {}
    });
}

function applyContext(context) {
    if (!context) return;
    for (const [key, value] of Object.entries(context)) {
        pyodide.globals.set(key, value);
    }
}

function snapshotGlobals() {
    return pyodide.globals.toJs({
        dict_converter: Object.fromEntries
    });
}

async function handleInit(id) {
    await pyodideReady;
    ok(id);
}

async function handleAddCallbacks(id, payload) {
    await pyodideReady;
    const moduleName = payload.moduleName;
    const methodNames = Array.from(payload.methodNames);

    registeredCallbacks.set(moduleName, methodNames);
    installCallbackModule(moduleName, methodNames);

    ok(id);
}

async function handleReset(id) {
    await recreatePyodide();
    ok(id);
}

async function handleSnapshotGlobals(id) {
    await pyodideReady;
    ok(id, snapshotGlobals());
}

async function handleRun(id, payload) {
    await pyodideReady;

    const code = payload.code;
    const context = payload.context || {};
    const resetGlobals = !!payload.resetGlobals;
    const captureStdout = !!payload.captureStdout;
    const captureStderr = !!payload.captureStderr;

    if (resetGlobals) {
        await recreatePyodide();
    }

    clearBuffers();
    setStreams(captureStdout, captureStderr);
    applyContext(context);

    try { await pyodide.loadPackagesFromImports(code); } catch (_) {}
    if (Array.isArray(payload.packages) && payload.packages.length > 0) {
        try { await pyodide.loadPackage(payload.packages); } catch (_) {}
    }

    await pyodide.runPythonAsync(code);

    ok(id, {
        callbackOps,
        stdout: stdoutBuffer.join(""),
        stderr: stderrBuffer.join("")
    });
}

self.onmessage = async event => {
    const { id, kind, payload } = event.data;

    try {
        switch (kind) {
            case "init":
                await handleInit(id);
                return;
            case "addCallbacks":
                await handleAddCallbacks(id, payload);
                return;
            case "reset":
                await handleReset(id);
                return;
            case "snapshotGlobals":
                await handleSnapshotGlobals(id);
                return;
            case "run":
                await handleRun(id, payload);
                return;
            case "runTurtle":
                await pyodideReady;

                const code = payload.code;
                const context = payload.context || {};
                const resetGlobals = !!payload.resetGlobals;
                const captureStdout = !!payload.captureStdout;
                const captureStderr = !!payload.captureStderr;
                const turtleMethods = payload.turtleMethods || [];
                const turtleSingleton = self.turtle;
                if (!turtleSingleton) {
                    throw new Error("Global turtle singleton is not available in worker scope.");
                }

                if (resetGlobals) {
                    await recreatePyodide();
                }

                turtleSingleton.reset();

                const turtleModule = {};
                for (const methodName of turtleMethods) {
                    turtleModule[methodName] = (...args) => {
                        callbackOps.push({
                            module: "turtle",
                            method: methodName,
                            args
                        });
                        turtleSingleton.handleCommand(methodName, args);
                        return undefined;
                    };
                }
                pyodide.registerJsModule("turtle", turtleModule);

                clearBuffers();
                setStreams(captureStdout, captureStderr);
                applyContext(context);
                await pyodide.runPythonAsync(code);
                ok(id, {
                    callbackOps,
                    stdout: stdoutBuffer.join(""),
                    stderr: stderrBuffer.join(""),
                    turtleResult: turtleSingleton.executionSnapshot()
                });
                return;
            default:
                throw new Error(`Unknown request kind: ${kind}`);
        }
    } catch (e) {
        fail(id, e);
    }
};
