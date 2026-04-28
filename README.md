# EduSquirrel Workbook

EduSquirrel Workbook is a Scala.js/Laminar project for building **interactive workbooks for students**.

Instead of presenting a fixed page, a workbook is assembled from reusable UI elements and interactions (text prompts, coding tasks, exploratory downloads, etc.). The goal is to let teachers/authors compose guided learning paths while learners work through sections in the browser.

## 1) Project goal (for humans)

At a high level, this repository exists to support:

- **Student-facing digital workbooks** with multiple sections and exercises.
- **Multilingual presentation** (for labels, instructions, and titles).
- **Interactive learning tasks** (e.g., text input, programming-related exercises, plugin-based activities).
- **Reusable content structure** so new workbooks can be built by combining existing components.

If you are trying to understand “what this app does”: it renders one or more workbook experiences into target DOM containers (for example test workbook, embroidery workbook, plant workshop app), and each workbook is composed from sections and exercise containers.

## 2) Programmer starting points (where to read first)

If you are new to the codebase, start with these files in order:

1. `src/main/scala/MainApp.scala`
   - Entry point (`mainApp`) and DOM mounting.
   - Shows which workbook/app variants are created and inserted into the page.
2. `src/main/scala/workbook/model/Workbook.scala`
   - Core workbook renderer (header + active section body).
3. `src/main/scala/workbook/model/WorkbookSection.scala`
   - Defines a section and how section content is rendered.
4. `src/main/scala/content/TestWorkbook/TestWorkbook.scala`
   - A compact example of constructing workbook content programmatically.
5. `src/main/scala/content/EmbroideryWorkbook/CreateEmbroideryWorkbook.scala`
   - A more realistic workbook assembly flow.

## 3) Core abstractions you will search for often

When implementing features, these are the most useful anchors:

- **`HtmlWorkbookElement`** (`workbook/model/abstractions/HtmlWorkbookElement.scala`)
  - Base trait for workbook-renderable components.
  - If a class should appear inside workbook content, it usually implements this.
- **`WorkbookInteraction[T]`** (same file)
  - Base trait for interactive elements with an `interactionVariable`.
- **`Workbook`** (`workbook/model/Workbook.scala`)
  - Top-level composition for header and active section rendering.
- **`WorkbookSection`** (`workbook/model/WorkbookSection.scala`)
  - Groups exercise containers and dependency metadata.
- **`HtmlExerciseContainer`** (`workbook/htmlElements/container/HtmlExerciseContainer.scala`)
  - Wraps a list/signal of `HtmlWorkbookElement` children.
- **`WorkbookInfo` + `WorkbookConfig`** (`workbook/model/info/...`)
  - Runtime context (language, active section, user, fullscreen container).

A practical search pattern is:

1. Find the user-visible widget/class.
2. Check whether it extends `HtmlWorkbookElement` or `WorkbookInteraction`.
3. Follow where it is inserted into a `HtmlExerciseContainer`.
4. Follow container usage into `WorkbookSection`, then into `Workbook`.

## 4) Where workbook content is authored

Workbook content (titles, instructions, exercise composition) is primarily authored under:

- `src/main/scala/content/`
  - `TestWorkbook/` for simple examples.
  - `EmbroideryWorkbook/` for a larger workbook.
  - `plantworkshop/` for plant workshop application content.

Reusable UI/building blocks are mostly under:

- `src/main/scala/workbook/htmlElements/`
- `src/main/scala/workbook/model/`
- `src/main/scala/interactionPlugins/`

## 5) Front-end and static assets

Supporting assets live in:

- `resources/` (images, language maps, workbook resources, fonts)
- `homepage/` (landing/demo pages and CSS/JS)
- `docs/` (project notes and workshop-specific docs)

## 6) Minimal mental model

- `MainApp` mounts workbook app roots.
- A `Workbook` chooses and renders one active `WorkbookSection`.
- A section contains `HtmlExerciseContainer` instances.
- A container renders multiple `HtmlWorkbookElement` implementations.
- Interactions are specialized `WorkbookInteraction[T]` elements with state.

If you keep that chain in mind, most feature and bug-fix navigation becomes straightforward.


## 7) Language map files (dynamic labels)

Language maps are loaded from `resources/languageMaps` at runtime. File naming is:

- `[group]-[language].[json|csv]`
- examples: `basic-en.json`, `TurtleStitch-de.json`

Each file contributes entries for one `group` and one language. In JSON, entries are key/value pairs. In CSV, each row is:

- `[entryName];[actualLabelText]`

The effective language map id used in code is:

- `group/entryName`
- example: `basic/imageLoadingMap`

Use language-map IDs in code via `AllWorkbookInfo.stringSignalFromLanguageMapId(...)` or helpers like `HtmlContainerTitle(workbookInfo, "group/entry")`.

To add a new language file, add the file and register it in `WorkbookLanguageInfo.languageMapFiles`.


## 8) GitHub Pages deployment

The site is deployed by [`.github/workflows/scala.yml`](.github/workflows/scala.yml) on every push to `main`. Layout:

```
homepage/
├── index.html              ← landing page (entry point)
├── workbook/index.html     ← Block/Python editor
├── plant/index.html        ← Plant workshop
├── feedback/index.html     ← Feedback demo
├── embroidery/index.html   ← Embroidery workbook
├── css/
└── js/
    ├── config.js           ← LLM_PROXY_URL pointing at the Cloudflare worker
    ├── app-loader.js       ← finds the Scala.js bundle (with fallbacks)
    └── ...                 ← supporting scripts
```

The workflow runs `sbt fastOptJS`, copies `homepage/` into `_site/`, then drops the Scala.js bundle into `_site/js/app/` and copies `resources/` next to the pages. The result is published to GitHub Pages.

### Local preview of the deployed layout

```bash
npm run dev          # sbt fastOptJS + assemble _site/ + serve on http://localhost:4173
# or, if you already built:
npm run preview
```

## 9) LLM proxy (Cloudflare Worker)

LLM calls go through a Cloudflare Worker so the OpenAI key never reaches the browser. The frontend reads `window.LLM_PROXY_URL` from [`homepage/js/config.js`](homepage/js/config.js).

By default the deployed site reuses the existing **`pytutorai-proxy`** worker (same code, same `/api/llm/complete` contract). If you want a dedicated worker, deploy [`tools/cloudflare-proxy/`](tools/cloudflare-proxy/README.md) and update the URL in `config.js`.

Local FastAPI proxy still works for offline development — see [`tools/openai-proxy/README.md`](tools/openai-proxy/README.md).
