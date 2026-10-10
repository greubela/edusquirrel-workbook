# Repository directory rules

Read the repository layout and content-placement guide in [README.md](README.md) before choosing a location for new files.

- Reuse existing root directories and the appropriate module, package or workbook subdirectory.
- Do not create new directories at the repository root unless no existing directory can serve the purpose and there is a very strong architectural or tooling necessity. Document the reason and update the README directory guide if an exception is required.
- Keep all temporary material under `tmp/`, grouped by its actual subject. Put ad hoc extracts, logs, screenshots, analysis, drafts and disposable scripts in ignored `tmp/work/<subject>/` (for example `evacuation/`, `image-recognition/` or `compression/`). Use `tmp/work/integration/` for cross-workbook checks and `tmp/work/tooling/` for temporary tool dependencies. Do not leave loose files in `tmp/work/` or create root `work/`, `temp_*` or other scratch directories.
- Retained workbook migration references belong in subject directories inside `tmp/`. Published materials, including classic PDF/ZIP downloads, belong in `resources/`; do not move public assets into `tmp/`.
- Keep application code and its tests in the corresponding `modules/` source tree, browser/asset checks in `tools/dev/`, maintained documentation in `docs/`, and presentation styles in dedicated `homepage/css/` files rather than in Laminar.

Follow the workbook element/interaction → renderer → optional editor structure described in [docs/workbook-interaction-architecture.md](docs/workbook-interaction-architecture.md).
