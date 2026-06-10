# EduSquirrel Workbook

EduSquirrel Workbook is a Scala.js/Laminar project for building **interactive workbooks for students**.

Instead of presenting a fixed page, a workbook is assembled from reusable UI elements and interactions (text prompts, coding tasks, exploratory downloads, GPT-assisted feedback, TurtleStitch tasks, etc.). The goal is to let teachers/authors compose guided learning paths while learners work through sections in the browser.

The repository now uses an sbt **module-based structure**. The data model that can be shared between browser, worker, and JVM code lives in `modules/core`; browser rendering and authored workbook content live in `modules/client`; browser web-worker code lives in `modules/worker`; and the JVM backend lives in `modules/server`.

## 1) Project goal (for humans)

At a high level, this repository exists to support:

- **Student-facing digital workbooks** with multiple sections and exercises.
- **Multilingual presentation** (for labels, instructions, and titles).
- **Interactive learning tasks** (for example text input, programming-related exercises, plugin-based activities, GPT feedback, and TurtleStitch/embroidery exercises).
- **Reusable content structure** so new workbooks can be built by combining existing components.

If you are trying to understand “what this app does”: it renders one or more workbook experiences into target DOM containers (for example the embroidery workbook, plant workshop app, feedback demo, or other workbook roots), and each workbook is composed from sections and exercise containers.

## 2) Module layout

The most important change from the older flat `src/main/scala` layout is that source code is split by runtime and responsibility:

```text
modules/
├── core/
│   ├── shared/   # Cross-platform model, shared data structures, command contracts, workbook/plugin definitions
│   ├── js/       # Scala.js-specific core implementation
│   └── jvm/      # JVM-specific core implementation
├── client/       # Scala.js browser app: MainApp, Laminar renderers, workbook content, legacy browser-only UI
├── worker/       # Scala.js web-worker backend used by in-browser interactions
└── server/       # JVM backend and command handlers
```

How the modules relate to each other:

- `root` aggregates `server`, `client`, and `worker`.
- `core` is a Scala cross-project for both JS and JVM. Put platform-independent workbook/domain models in `modules/core/shared`.
- `client` depends on `core.js`. Put DOM/Laminar rendering, browser utilities, workbook assembly, and frontend-only legacy code here.
- `worker` depends on `core.js`. Put code that runs inside browser workers here.
- `server` depends on `core.jvm`. Put JVM backend code and server-side command handling here.

This means an old path like `src/main/scala/workbook/model/Workbook.scala` is now represented by the shared model under `modules/core/shared/src/main/scala/it/evadid/workbook/model/...`, while browser-specific workbook rendering lives under `modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/...`.

## 3) Programmer starting points (where to read first)

If you are new to the codebase, start with these files in order:

1. `modules/client/src/main/scala/MainApp.scala`
   - Browser entry point (`mainApp`) and DOM mounting.
   - Shows which app/workbook roots are detected and inserted into the page.
2. `modules/core/shared/src/main/scala/it/evadid/workbook/model/elements/Workbook.scala`
   - Shared top-level workbook data model.
3. `modules/core/shared/src/main/scala/it/evadid/workbook/model/elements/WorkbookSection.scala`
   - Shared section model and section dependency metadata.
4. `modules/core/shared/src/main/scala/it/evadid/workbook/model/elements/ExerciseContainer.scala`
   - Shared grouping model for exercise content.
5. `modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/HtmlRenderFactory.scala`
   - Browser-side bridge from shared workbook elements to Laminar HTML.
6. `modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/basicRenderer/HtmlWorkbookRenderer.scala`
   - Browser renderer for full workbooks and active sections.
7. `modules/client/src/main/scala/it/evadid/homepage/workbook/content/TestWorkbookFactory.scala`
   - Compact example of constructing workbook content programmatically.
8. `modules/client/src/main/scala/it/evadid/homepage/workbook/content/CreateEmbroideryWorkbook.scala`
   - A larger, realistic workbook assembly flow.

## 4) Core abstractions you will search for often

When implementing features, these are the most useful anchors in the new module layout:

- **`WorkbookElement`** (`modules/core/shared/src/main/scala/it/evadid/workbook/model/abstractions/WorkbookElement.scala`)
  - Base shared abstraction for model elements that can appear in a workbook.
- **`WorkbookElementGroup[T]`** (`modules/core/shared/src/main/scala/it/evadid/workbook/model/abstractions/WorkbookElementGroup.scala`)
  - Shared abstraction for grouped elements such as workbooks, sections, and exercise containers.
- **`WorkbookInteraction[T]`** (`modules/core/shared/src/main/scala/it/evadid/workbook/model/interaction/WorkbookInteraction.scala`)
  - Shared abstraction for interactive workbook elements with state/serialization.
- **`Workbook`** (`modules/core/shared/src/main/scala/it/evadid/workbook/model/elements/Workbook.scala`)
  - Top-level shared composition: workbook id, title, sections, and available languages.
- **`WorkbookSection`** (`modules/core/shared/src/main/scala/it/evadid/workbook/model/elements/WorkbookSection.scala`)
  - Shared grouping of exercise containers/elements plus dependency metadata.
- **`ExerciseContainer`** (`modules/core/shared/src/main/scala/it/evadid/workbook/model/elements/ExerciseContainer.scala`)
  - Shared container for a list of workbook elements.
- **`HtmlRenderFactory`** (`modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/HtmlRenderFactory.scala`)
  - Browser-side dispatch point for rendering shared elements to Laminar nodes.
- **`HtmlFullWorkbookApp` and workbook info/control classes** (`modules/client/src/main/scala/it/evadid/homepage/control/...`)
  - Runtime context for language maps, active workbook/section, storage, backend execution, and fullscreen/browser state.

A practical search pattern is:

1. Find the user-visible widget/class or shared model element.
2. If it is model/data, check `modules/core/shared/src/main/scala/it/evadid/workbook/...`.
3. If it touches DOM, Laminar, browser storage, or app state, check `modules/client/src/main/scala/it/evadid/homepage/...`.
4. Follow rendering through `HtmlRenderFactory` into the relevant renderer under `workbook/htmlRenderer/`.
5. Follow rendered content back to `ExerciseContainer`, then to `WorkbookSection`, then to `Workbook`.

## 5) Where workbook content is authored

Workbook content (titles, instructions, exercise composition) is primarily authored under:

- `modules/client/src/main/scala/it/evadid/homepage/workbook/content/`
  - `TestWorkbookFactory.scala` for simple examples.
  - `CreateEmbroideryWorkbook.scala` for the embroidery workbook.
  - `CreatePlantworkshopWorkbook.scala` and `legacy/plantworkshop/` for plant workshop content/application code.
  - `WorkbookFactory.scala` for JSON/config-driven workbook construction helpers.

Reusable/shared workbook models and plugin element definitions are mostly under:

- `modules/core/shared/src/main/scala/it/evadid/workbook/model/`
- `modules/core/shared/src/main/scala/it/evadid/workbook/plugins/`

Browser UI/building blocks and renderers are mostly under:

- `modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/`
- `modules/client/src/main/scala/it/evadid/homepage/workbook/legacy/htmlElements/`
- `modules/client/src/main/scala/it/evadid/homepage/workbook/legacy/interactionPlugins/`
- `modules/client/src/main/scala/it/evadid/homepage/webElements/`

## 6) Front-end pages, static assets, and generated artifacts

Supporting static files live in:

- `homepage/` — landing/demo pages, CSS, browser bootstrap scripts, and app-specific HTML containers.
- `resources/` — images, language maps, workbook resources, fonts, PDFs, and program resources.
- `docs/` — project notes and workshop-specific docs.
- `artifacts/` — generated Scala.js/JVM build outputs copied by sbt build tasks.

Important page/container examples:

- `homepage/index.html` — landing page.
- `homepage/embroideryWorkbook/index.html` — embroidery workbook page.
- `homepage/plantWorkshop/index.html` — plant workshop app page.
- `homepage/feedback-demo/index.html` — feedback demo page.
- `homepage/js/app-loader.js` — finds and loads the Scala.js bundle.
- `homepage/js/config.js` — configures the LLM proxy URL.

## 7) Minimal mental model

- `MainApp` runs in the `client` module and mounts one browser app root when it finds a matching DOM container.
- A shared `Workbook` from `core/shared` contains one or more `WorkbookSection` values.
- A section contains `ExerciseContainer` values or other `WorkbookElement` implementations.
- `HtmlRenderFactory` and the `htmlRenderer/` classes in `client` turn shared workbook elements into Laminar DOM.
- Interactions are shared `WorkbookInteraction[T]` elements, usually with browser renderers/editors in `client`.
- Worker-backed interactions use the `worker` module and shared command/model types from `core`.
- JVM/server-backed interactions use the `server` module and shared command/model types from `core`.

If you keep the chain `core model → client renderer → MainApp DOM mount` in mind, most feature and bug-fix navigation becomes straightforward.

## 8) Language map files (dynamic labels)

Language maps are loaded from `resources/languageMaps` at runtime. File naming is:

- `[group]-[language].[json|csv]`
- examples: `basic-en.json`, `TurtleStitch-de.json`

Each file contributes entries for one `group` and one language. In JSON, entries are key/value pairs. In CSV, each row is:

- `[entryName];[actualLabelText]`

The effective language map id used in code is:

- `group/entryName`
- example: `basic/imageLoadingMap`

Use language-map IDs in code via runtime workbook info helpers (for example methods on `AllWorkbookInfo`) or renderer/content helpers that accept `LanguageMapContentId` values.

To add a new default language file:

1. Add the file under `resources/languageMaps/`.
2. Register it in `WorkbookContentStorage.languageMapFiles` in `modules/client/src/main/scala/it/evadid/homepage/control/WorkbookContentStorage.scala`.

JSON-driven/custom workbook resources can also provide language maps under their own `resources/workbookresources/.../languageMaps` directories when loaded by the workbook factory/storage flow.

## 9) Building and running locally

The root `package.json` provides convenience wrappers around the sbt build and local static preview:

```bash
npm run build       # sbt -batch fastOptJS
npm run assemble    # node tools/dev/assemble-site.mjs
npm run dev         # build + assemble _site/ + serve on http://localhost:4173
npm run preview     # serve an already assembled _site/ on http://localhost:4173
```

Useful sbt tasks from `build.sbt` include:

```bash
sbt buildClientFast   # fastLinkJS for modules/client, copied to artifacts/newest/client.js
sbt buildWorkerFast   # fastLinkJS for modules/worker, copied to artifacts/newest/backend-worker.js
sbt buildServerFast   # assembly jar for modules/server, copied to artifacts/newest/server.jar
sbt deployAll         # full client + worker + server build and artifact copies
```

## 10) GitHub Pages deployment

The site is deployed by [`.github/workflows/scala.yml`](.github/workflows/scala.yml) on every push to `main`. The deployed layout is assembled from `homepage/`, `resources/`, and generated artifacts.

Current page layout includes:

```text
homepage/
├── index.html                         # landing page
├── embroideryWorkbook/index.html      # Embroidery workbook
├── plantWorkshop/index.html           # Plant workshop app
├── plantWorkshopWorkbook/index.html   # Plant workshop workbook container
├── feedback-demo/index.html           # Feedback demo
├── workbookDesign/index.html          # Workbook design page
├── css/
└── js/
    ├── config.js                      # LLM_PROXY_URL pointing at the Cloudflare worker/proxy
    ├── app-loader.js                  # finds the Scala.js bundle (with fallbacks)
    └── ...                            # supporting scripts
```

The workflow builds the Scala.js client and worker, then runs `tools/dev/assemble-site.mjs`. That script mirrors `homepage/` into `_site/`, copies the current client bundle to `_site/js/app/main.js`, publishes `artifacts/newest/` for worker-backed interactions, and copies `resources/` including images, PDFs, fonts, and workbook assets next to the pages. The result is checked and published to GitHub Pages.

### Local preview of the deployed layout

```bash
npm run dev
# or, if you already built:
npm run preview
```

## 11) LLM proxy (Cloudflare Worker)

LLM calls go through a Cloudflare Worker so the OpenAI key never reaches the browser. The frontend reads `window.LLM_PROXY_URL` from [`homepage/js/config.js`](homepage/js/config.js).

By default the deployed site reuses the existing **`pytutorai-proxy`** worker (same code, same `/api/llm/complete` contract). If you want a dedicated worker, deploy [`tools/cloudflare-proxy/`](tools/cloudflare-proxy/README.md) and update the URL in `config.js`.

Local FastAPI proxy still works for offline development — see [`tools/openai-proxy/README.md`](tools/openai-proxy/README.md).
