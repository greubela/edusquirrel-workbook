# Active interaction refactor

The full user requirements are retained in [tmp/interaction-refactor/USER_PROMPT.txt](tmp/interaction-refactor/USER_PROMPT.txt). Follow them throughout this branch. Additional requirements are retained in [tmp/interaction-refactor/ADDITIONAL_REQUIREMENTS.txt](tmp/interaction-refactor/ADDITIONAL_REQUIREMENTS.txt).

# Repository directory rules

Read the repository layout and content-placement guide in [README.md](README.md) before choosing a location for new files.

- Reuse existing root directories and the appropriate module, package or workbook subdirectory.
- Do not create new directories at the repository root unless no existing directory can serve the purpose and there is a very strong architectural or tooling necessity. Document the reason and update the README directory guide if an exception is required.
- Keep all temporary material under `tmp/`, grouped by its actual subject. Put ad hoc extracts, logs, screenshots, analysis, drafts and disposable scripts in ignored `tmp/work/<subject>/` (for example `evacuation/`, `image-recognition/` or `compression/`). Use `tmp/work/integration/` for cross-workbook checks and `tmp/work/tooling/` for temporary tool dependencies. Do not leave loose files in `tmp/work/` or create root `work/`, `temp_*` or other scratch directories.
- Retained workbook migration references belong in subject directories inside `tmp/`. Published materials, including classic PDF/ZIP downloads, belong in `resources/`; do not move public assets into `tmp/`.
- Keep application code and its tests in the corresponding `modules/` source tree, browser/asset checks in `tools/dev/`, maintained documentation in `docs/`, and presentation styles in dedicated `homepage/css/` files rather than in Laminar.

- Use canonical definitions and API names. Update callers when moving or renaming code; do not add compatibility aliases, re-exports or forwarding methods merely to retain old source names.

- Keep documentation concise: focus on architectural decisions, contracts, key code locations and a few useful examples. Link to source or focused guides for implementation details; avoid method-by-method explanations, extensive prose and repetition.

Follow the workbook element/interaction → renderer → optional editor structure described in [docs/workbook-interaction-architecture.md](docs/workbook-interaction-architecture.md).

State rule: keep changing application, editor and lifecycle state either in `val`-bound Airstream `Var[T]` values in the client or, wherever it need not depend on the UI, in a core `State[T]` (for example the answers in `InteractionController`). Both are transactional and encapsulated: change them only through transitions on their owner and observe their signals or observables, in a functional, event-based style. Do not introduce Scala `var` fields, captured mutable locals or mutable collection holders for this state. Derive values with signals and immutable transformations; keep temporary calculations immutable as well. This applies to tab adapters, optional modules and asynchronous request/lifecycle guards. Shared core remains independent of Laminar/Airstream; client views adapt core State through `StateHelper`/`InteractionSignals`. See [state and serialization](docs/state-and-serialization.md).

Editor architecture: create only the selected editor, bind it to the central answer state (through an `InteractionSignals` Var view), and release it on Laminar unmount. Observe Var signals instead of adding editor-listener callbacks; treat all changes alike, without edit/restoration channels or synchronization-baseline stores. Panels are downstream GUI components receiving the central answer state and task configuration, not contexts bound or activated by Eva. Run long computations in Futures and use workers where supported. Do not introduce fullscreen lifecycle interfaces or retained hidden editors. Derive UI flags from authoritative state (for example, a native handle determines fallback visibility) instead of storing copies.

Persisted-data policy: there is no legacy data. When a saved schema changes, existing records are removed and only the current format is written and read; do not add migration readers, adapters for earlier formats, old field/tag aliases, or backward-compatible payloads. Persisted names (formats, event tags, grader types) are explicit literals, not class names. Points in time are `Instant`s, serialized only by the codec in `DefaultSerializer`; convert to `LocalDateTime` only for display (`InfoUtil.localDateTime`) or zone-less boundaries such as JDBC (UTC) and ZIP entries. Catch malformed/unsupported data at storage boundaries and discard the affected record without crashing or clearing unrelated data.

## Serialization architecture

History APIs carry `[F <: InteractionFormat]`. Formats are sealed singleton values,
serializable by a stable name independently of workbook definitions and element IDs.
`InteractionFormat.State[F]`, `Grading[F]` and `Scaffolding[F]` recover their types;
do not use illegal generic projections such as `F#InteractionStateType`.
The format owns state, feedback, event and history codecs. Exercise-specific
constraints belong in `WorkbookInteractionElement.validateState`, not its codec.

History reads use only `fetch-events-from-db-request`; complete reads use
`Instant.MIN` and `MAX` bounds. Do not reintroduce a separate full-fetch
command or dual-operation fallback. Database setup has one source,
`deployment/database/setup.sql`, and rejects nonempty databases.

Transport and persisted histories use `InteractionHistoryJson(format, historyJson)`.
Do not infer a format from an element ID, reconstruct histories with raw JSON, or
add compatibility readers. See [docs/interaction-format.md](docs/interaction-format.md)
and [docs/database/interaction-history.md](docs/database/interaction-history.md).
