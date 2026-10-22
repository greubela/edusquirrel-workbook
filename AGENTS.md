# Repository directory rules

Read the repository layout and content-placement guide in [README.md](README.md) before choosing a location for new files.

- Reuse existing root directories and the appropriate module, package or workbook subdirectory.
- Do not create new directories at the repository root unless no existing directory can serve the purpose and there is a very strong architectural or tooling necessity. Document the reason and update the README directory guide if an exception is required.
- Keep all temporary material under `tmp/`, grouped by its actual subject. Put ad hoc extracts, logs, screenshots, analysis, drafts and disposable scripts in ignored `tmp/work/<subject>/` (for example `evacuation/`, `image-recognition/` or `compression/`). Use `tmp/work/integration/` for cross-workbook checks and `tmp/work/tooling/` for temporary tool dependencies. Do not leave loose files in `tmp/work/` or create root `work/`, `temp_*` or other scratch directories.
- Retained workbook migration references belong in subject directories inside `tmp/`. Published materials, including classic PDF/ZIP downloads, belong in `resources/`; do not move public assets into `tmp/`.
- Keep application code and its tests in the corresponding `modules/` source tree, browser/asset checks in `tools/dev/`, maintained documentation in `docs/`, and presentation styles in dedicated `homepage/css/` files rather than in Laminar.

- Keep documentation concise: focus on important design decisions, contracts and verification commands; avoid extensive prose and repeated implementation details.

Follow the workbook element/interaction → renderer → optional editor structure described in [docs/workbook-interaction-architecture.md](docs/workbook-interaction-architecture.md).

Client state rule: keep changing application, editor and lifecycle state in `val`-bound Airstream `Var[T]` values. Do not introduce Scala `var` fields, captured mutable locals or mutable collection holders for this state. Derive values with signals and immutable transformations; keep temporary calculations immutable as well. This applies to tab adapters, optional modules and asynchronous request/lifecycle guards. Shared core remains independent of Laminar/Airstream; model its transitions as immutable values. See [state and serialization](docs/state-and-serialization.md).

Editor architecture: create only the selected editor, bind it to the central answer Var, and release it on Laminar unmount. Observe Var signals instead of adding editor-listener callbacks; treat all changes alike, without edit/restoration channels or synchronization-baseline stores. Panels are downstream GUI components receiving the central Var and task configuration, not contexts bound or activated by Eva. Run long computations in Futures and use workers where supported. Do not introduce fullscreen lifecycle interfaces or retained hidden editors. Derive UI flags from authoritative state (for example, a native handle determines fallback visibility) instead of storing copies.
