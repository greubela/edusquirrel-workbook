# Agent Collaboration Notes

## Screenshots (important for visual changes)
- This project includes visual UI behavior; when making user-visible UI changes, agents should provide a screenshot whenever possible.
- Preferred tooling: Playwright (already part of the setup) for deterministic screenshots in local/devcontainer and CI contexts.
- Store screenshots under a predictable artifact path (for example `artifacts/screenshots/`) and reference them in summaries/PR notes.
- If screenshots cannot be produced due to environment/tool limitations, state that explicitly and include the attempted command.

## Encourage shared practice across agents
- If you are writing or updating agent guidance files, include a short screenshot policy section so future agents follow a consistent visual-review workflow.
- Keep screenshot guidance minimal and practical: one recommended tool, one storage location, and one fallback behavior.
