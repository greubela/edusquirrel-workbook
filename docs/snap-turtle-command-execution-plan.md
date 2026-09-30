# Snap turtle-command extraction implementation plan

This plan follows the behaviour audit in
`snap-turtle-command-execution-overview.md` and the tests/stubs added before the
production implementation.

1. **Create an isolated execution package.** Keep orchestration and Pyodide
   adaptation under `SnapEditor.execution`; expose a minimal runner interface
   so command derivation is independently testable.
2. **Reuse the canonical transformation.** Derive Python only through
   `SnapProgramDerivation.fromState`. Reject unsupported Snap state before
   execution rather than running a lossy partial program.
3. **Install complete turtle hooks.** Register every Python name and alias from
   `SnapTurtleCatalog` as functions in a synthetic `turtle` module in the
   existing Pyodide Web Worker, then execute generated Python after
   `from turtle import *`. Reset Pyodide globals for every extraction. Runtime
   callbacks—not AST traversal—are the source of the returned list.
4. **Decode without losing command information.** Keep callback order, ignore
   callbacks from other modules, canonicalize aliases via the catalog, store
   finite numeric values in `args`, and store strings/booleans in `stringArgs`.
   Reject unsupported or non-finite argument values explicitly.
5. **Expose the operation on the block editor.** Flush the live IDE first and
   call the extraction service with the state synchronously published by that
   flush. Return a `Future` because Pyodide execution is asynchronous.
6. **Verify at multiple levels.** Unit-test derivation/runner orchestration and
   callback decoding, compile all editor implementations against the extended
   API, run client tests, and run shared-core tests to protect the catalog and
   Python bridge reused by the feature.

The API intentionally propagates derivation and execution failures. Callers can
therefore distinguish a complete empty command list from a program that did not
execute successfully.
