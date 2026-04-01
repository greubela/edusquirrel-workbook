# Agent Guide

## Key Code Areas
- `src/main/scala/de/educorvi/edusquirrel/workbook/`: Scala entry points and application wiring for the interactive workbook runtime.
- `src/main/scala/de/educorvi/edusquirrel/workbook/interactionPlugins/`: Library of interaction plugins powering exercises (drag & drop, quizzes, etc.). Changes here must respect plugin APIs.
- `resources/`: Static assets (HTML templates, styles, localization files) referenced by the Scala backend.
- `homepage/` and `docs/`: Public-facing site and documentation assets.
- `homepage/embroideryWorkbook.html`: Most important page for visual verification; prioritize screenshots of this page for workbook UI changes.

## Planning & Documentation
- `README.md`: High-level overview of project goals, plugin catalog, architecture, and roadmap highlights.
- `planning/ideas.txt`: Backlog of potential enhancements across interaction types, platform features, UX, and technical improvements.
- `planning/designdocs/`: Detailed design proposals ready for future implementation work:
  - `interaction-history.md`
  - `persistent-state-platform.md`
  - `printable-export.md`

## Recommended Onboarding Steps
1. Review `README.md` to understand current capabilities and architecture.
2. Inspect interaction plugin implementations under `src/main/.../interactionPlugins/` to see extension points.
3. Align new feature work with `planning/designdocs/` and update or add documents when scopes evolve.
4. Coordinate with persistence and analytics stakeholders before altering plugin event schemas.

## Screenshot Workflow (CI/Container)
- Prefer serving pages over HTTP (not `file://`) so module scripts and relative assets load correctly.
- Option 1 (manual): start the server and run Playwright in the **same shell command** so the server process stays alive:
  - `python3 -m http.server 8000 & SERVER_PID=$!; ...; kill $SERVER_PID`
- Option 2 (setup-script smoke check): run the scripted capture block added at the end of setup, which starts/stops the server automatically and writes `artifacts/embroideryWorkbook-default.png`.
- Use this primary target page for checks: `http://127.0.0.1:8000/homepage/embroideryWorkbook.html`.
- Save screenshots to `artifacts/` only when required by the task/review and avoid committing binaries unless explicitly requested.
