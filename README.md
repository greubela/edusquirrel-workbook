# EduSquirrel Workbook

EduSquirrel builds interactive, multilingual student workbooks with Scala.js and Laminar. Shared models describe sections, exercises and persistent interaction state; browser renderers provide the editors and feedback.

## Repository layout

| Directory | Responsibility |
| --- | --- |
| `modules/core/shared/` | Cross-platform domain models, VM/parser code, workbook elements, serialization and synchronization contracts |
| `modules/core/js/`, `modules/core/jvm/` | Platform-specific core implementations |
| `modules/client/` | Browser startup, Laminar renderers, editors and authored workbooks |
| `modules/worker/` | Scala.js browser worker and shared command execution |
| `modules/server/` | JVM backend and command handlers |
| `homepage/` | Entry pages, CSS and browser bootstrap scripts |
| `resources/` | Language maps, teaching materials and bundled programs/libraries |
| `tools/` | Site assembly, browser checks, fixture generators and optional LLM proxies |
| `artifacts/` | Generated bundles and server assemblies |
| `project/` | sbt build definitions, plugins and artifact tasks |
| `deployment/` | Deployment configuration and operational assets |
| `docs/` | Maintained architecture, migration status, authoring and development guides |
| `.github/` | CI workflows and repository tooling instructions |
| `target/` | Generated Scala build output and reports; ignored by Git |
| `node_modules/` | Installed Node development dependencies; generated and ignored by Git |
| `_site/`, `dist/` | Generated site/export output; ignored by Git |
| `tmp/` | Extracted workbook reference copies (`bitcoin/`, `evacuation/`, `phishing/`) and ignored local scratch files (`work/`) |

Keep temporary workbook extracts and development captures under `tmp/`. The reference copies are retained for migration work; duplicate extracts have been consolidated here. `tmp/work/` is ignored by Git. Published teaching materials and classic PDF/ZIP downloads remain under `resources/` and are independent of these reference copies. Neither site assembly includes `tmp/`.

### Choosing a location for new content

| Content | Location |
| --- | --- |
| Domain models, workbook elements/interactions, validation and serializers shared by JVM and JS | `modules/core/shared/src/main/scala/`; tests in its matching `src/test/scala/` package |
| Browser interaction renderers, editors and workbook factories | `modules/client/src/main/scala/`; tests in its matching `src/test/scala/` package |
| Platform-specific code, worker execution or backend behavior | The corresponding core JS/JVM, worker or server module |
| Workbook entry pages and browser bootstrap scripts | `homepage/<workbook>/index.html` and `homepage/js/` |
| Presentation styles and shared color/dimension tokens | Dedicated files in `homepage/css/`; static styles belong here rather than in Laminar |
| Translated workbook text | `resources/languageMaps/eva/<workbook-group>/` |
| Published classic PDF/ZIP editions | `resources/workbookpdfs/`; keep public catalogue links available during migration |
| Images, datasets and other published workbook assets | `resources/workbookresources/<workbook>/` |
| Bundled standalone teaching programs and libraries | `resources/programs/` |
| Browser/asset checks, importers and repeatable development utilities | `tools/dev/` |
| Architecture, migration decisions and maintained task inventories | `docs/`; link relevant guides from this README |
| Deliberately retained extracts used as migration references | An appropriate subject directory inside `tmp/` |
| Ad hoc ZIP extracts, logs, screenshots, analysis, drafts and disposable scripts | `tmp/work/`; do not commit these files |

Reuse these locations and existing subject/package directories. Do not create a new directory at the repository root unless no existing directory can serve the purpose and there is a strong architectural or tooling necessity. Explain that necessity and update this directory guide when such an exception is required. Agent instructions are in [AGENTS.md](AGENTS.md).

The root sbt project aggregates client, server and worker. Client and worker depend on core JS; server depends on core JVM. Run the core test projects explicitly to verify both platforms.

## Finding the code

Start with [MainApp.scala](modules/client/src/main/scala/MainApp.scala) and [HomepageStartupLogic.scala](modules/client/src/main/scala/it/evadid/homepage/control/startup/HomepageStartupLogic.scala) for page mounting.

Shared workbook abstractions live in [WorkbookElement.scala](modules/core/shared/src/main/scala/it/evadid/workbook/abstractions/WorkbookElement.scala): `WorkbookElement`, `WorkbookStructureElement[T]` and `WorkbookInteractionElement[T]`. The latter owns an `InteractionVariable[T]`, default value and content serializer. The structure models are [Workbook](modules/core/shared/src/main/scala/it/evadid/workbook/elements/structureElements/Workbook.scala), [WorkbookSection](modules/core/shared/src/main/scala/it/evadid/workbook/elements/structureElements/WorkbookSection.scala) and [ExerciseContainer](modules/core/shared/src/main/scala/it/evadid/workbook/elements/structureElements/ExerciseContainer.scala).

[HtmlRenderFactory.scala](modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/HtmlRenderFactory.scala) maps shared elements to browser renderers. [CreateTestWorkbook.scala](modules/client/src/main/scala/it/evadid/homepage/workbook/content/CreateTestWorkbook.scala) provides small authoring examples, including QR and email interactions. Larger factories are in the same content directory. Runtime context and language/storage controls live under `modules/client/src/main/scala/it/evadid/homepage/control/`.

## Workbook entry pages

| Page | Content |
| --- | --- |
| [homepage/index.html](homepage/index.html) | Classic catalogue, including PDF/ZIP editions |
| [workbooks](homepage/workbooks/index.html) | Laminar catalogue of available digital workbooks; browsing is public |
| [evacuationWorkbook](homepage/evacuationWorkbook/index.html) | Partial evacuation adaptation with local EVA2 playback, locker/door comparisons with saved graphs, a budget plan and model critique |
| [workbookDesign](homepage/workbookDesign/index.html) | Test workbook and interaction examples, including local EVA2 floor construction |
| [embroideryWorkbook](homepage/embroideryWorkbook/index.html) | Embroidery workbook |
| [embroideryPreviewWorkbook](homepage/embroideryPreviewWorkbook/index.html) | Separate embroidery edition with geometric turtle previews, conditionals and recursion (DE/EN) |
| [compressionWorkbook](homepage/compressionWorkbook/index.html) | Data compression workbook |
| [plantWorkshopWorkbook](homepage/plantWorkshopWorkbook/index.html) | Plant workshop on the workbook architecture |
| [blockchainWorkbook](homepage/blockchainWorkbook/index.html) | Blockchain learner activities, mining simulator and energy/final assessment; equivalence review pending |
| [imageRecognitionWorkbook](homepage/imageRecognitionWorkbook/index.html) | Partial image-recognition workbook with neuron/pixel experiments, robustness and school-chatbot assessment |
| [phishingWorkbook](homepage/phishingWorkbook/index.html) | Mail simulator, warning/domain/Unicode analysis, attachment risks and checklist; partial adaptation |
| [plantWorkshop](homepage/plantWorkshop/index.html) | Separate legacy plant application |
| [feedback-demo](homepage/feedback-demo/index.html) | Feedback demonstration |

See [catalogue and cache startup](docs/laminar-workbook-catalog-and-cache.md) for the public registry, IndexedDB lifecycle and startup checks.

## Language maps and styles

Default language maps live under `resources/languageMaps/eva/<group>/map-<language>.json`, with `map-universal.json` where needed. IDs such as `PlantWorkshop/workbookTitle` are resolved through the language sources. [LanguageMapStorageControl.scala](modules/client/src/main/scala/it/evadid/homepage/control/change/LanguageMapStorageControl.scala) registers the default EVA directories and Snap locale sources. Add a new default group there; adding translations to an existing group uses its existing source registration.

Static presentation belongs in dedicated files under `homepage/css/`, using `generic/colors.css` and `generic/dimensions.css`. Laminar binds semantic state and classes. Every workbook entry page explicitly links the mail simulator CSS. CSS imports supply other shared components and editors. Canvas rendering reads styles at the DOM boundary; computed drawing geometry and self-contained SVG/image exports still require values in code.

## Build and local preview

Use Java, sbt and Node.js/npm. CI uses Java 17 and Node 22; the Scala suites have also run on Java 21. Install the locked Node development dependencies before running browser/DOM tests:

```sh
npm ci
sbt buildJS
npm run assemble
npm run preview
```

This builds `artifacts/newest/client.js` and `worker.js`, assembles a local `_site/`, and serves it at `http://localhost:4173`. The npm `assemble` script uses [assemble-site.mjs](tools/dev/assemble-site.mjs), whose local preview layout differs from the deployment layout described below. The existing npm `build` wrapper invokes `fastOptJS`; use `sbt buildJS` when you need current published client and worker artifacts.

Useful artifact tasks:

| Task | Output |
| --- | --- |
| `sbt buildClientDev` | Fast-linked client in `artifacts/newest/client.js` |
| `sbt buildWorkerDev` | Fast-linked worker in `artifacts/newest/worker.js` |
| `sbt buildServerDev` | Server assembly in `artifacts/newest/server.jar` |
| `sbt buildAllDev` | All development artifacts, built sequentially |
| `sbt buildClientDeploy buildWorkerDeploy` | Optimized JS artifacts in newest/stable/history |
| `sbt buildAllDeploy` | All deployment artifacts |

Older `buildClientFast`, `buildWorkerFast`, `buildServerFast` and `deployAll` names remain deprecated aliases. Task definitions are in [BuildCommands.scala](project/BuildCommands.scala).

## Tests

Run all Scala suites, including both shared platforms:

```sh
sbt 'coreJVM/test' 'coreJS/test' 'client/test' 'server/test' 'worker/test'
```

Useful asset and browser checks:

```sh
node --test tools/dev/assemble-pages.test.mjs
node --test tools/dev/workbook-stylesheets.test.mjs
node --test tools/dev/editor-layout.test.mjs
node --test tools/dev/turtle-css.test.mjs
# These interaction checks need the current sbt buildJS artifacts:
node --test tools/dev/mail-simulator.test.mjs
node --test tools/dev/qr-interaction.test.mjs
```

Browser tests use system Chromium when available. Otherwise install it with `npx playwright install chromium`, or set `CHROMIUM_PATH`. Restricted environments can run Node tests without test-worker isolation, for example `node --test --test-isolation=none tools/dev/workbook-stylesheets.test.mjs`.

## Deployment

[scala.yml](.github/workflows/scala.yml) builds optimized client/worker artifacts and MathWorld on pushes to main. It runs [assemble-pages.mjs](tools/dev/assemble-pages.mjs), which publishes the homepage directory, resources, newest worker/client artifacts and root-level page redirects. It requires the MathWorld `dist/index.html` build too:

```sh
sbt buildClientDeploy buildWorkerDeploy
(cd resources/programs/20260907MathworldMain && npm ci && npm run build -- --base=./)
node tools/dev/assemble-pages.mjs
```

Both assembly scripts replace `_site/`. Generated outputs should not be edited as source.

LLM feedback uses the endpoint configured in [config.js](homepage/js/config.js). The deployed configuration points to the existing PyTutorAI Cloudflare worker. For a separate deployment or local development, see the [Cloudflare proxy guide](tools/cloudflare-proxy/README.md) and [FastAPI proxy guide](tools/openai-proxy/README.md). Real email transport is not used by the mailbox simulator.

## Documentation

- [Digital workbook migration](docs/digital-workbook-migration.md): analysis of linked PDF/ZIP exercises, missing editors and the Blockchain, image-recognition and Phishing adaptations.
- [Interaction architecture](docs/workbook-interaction-architecture.md): dependency boundaries between shared models, renderers and optional editors; state and serialization flow.
- [Native compression workbook](docs/compression-workbook.md): complete seven-chapter native copy, saved interactions and the retained standalone version.
- [Email simulator](docs/email-simulator.md): state, grading, content handling and verification.
- [QR interaction](docs/qr-code-interaction.md): encoding model, requirements, region colors and reference tests.
- [Plant workshop](docs/PlantWorkshop_Quickstart.md): implemented sections and remaining migration work.
- [Eva programming state](docs/eva-editor-programming-state.md) and [Snap editor](docs/snap-editor.md): representations and editor lifecycle.
- [Snap command execution service](docs/snap-turtle-command-execution-overview.md): Pyodide boundary and current integration status.
- [Python parsing](docs/python-parsing-architecture.md): normalization and parser responsibilities.
- [Core test audit](docs/core-package-unit-tests.md): historical coverage measurements and test commands.
- [Workbook interaction roadmap](WORKBOOK_INTERACTIONS.md): teaching-resource inventory and proposed future exercises.

Markdown under bundled third-party libraries documents those distributions. Imported teaching projects retain their conceptual and historical documents; their upstream setup/team instructions do not describe the EduSquirrel repository workflow. MathWorld's README and deployment guide explain its integration here.

Classic PDF/ZIP downloads intentionally coexist with their partial digital versions. Digital migrations are listed under “Digitale Workbooks (In Arbeit)” until full content equivalence has been reviewed; see the [migration policy](docs/digital-workbook-migration.md).
