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

The root sbt project aggregates client, server and worker. Client and worker depend on core JS; server depends on core JVM. Run the core test projects explicitly to verify both platforms.

## Finding the code

Start with [MainApp.scala](modules/client/src/main/scala/MainApp.scala) and [HomepageStartupLogic.scala](modules/client/src/main/scala/it/evadid/homepage/control/startup/HomepageStartupLogic.scala) for page mounting.

Shared workbook abstractions live in [WorkbookElement.scala](modules/core/shared/src/main/scala/it/evadid/workbook/abstractions/WorkbookElement.scala): `WorkbookElement`, `WorkbookStructureElement[T]` and `WorkbookInteractionElement[T]`. The latter owns an `InteractionVariable[T]`, default value and content serializer. The structure models are [Workbook](modules/core/shared/src/main/scala/it/evadid/workbook/elements/structureElements/Workbook.scala), [WorkbookSection](modules/core/shared/src/main/scala/it/evadid/workbook/elements/structureElements/WorkbookSection.scala) and [ExerciseContainer](modules/core/shared/src/main/scala/it/evadid/workbook/elements/structureElements/ExerciseContainer.scala).

[HtmlRenderFactory.scala](modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/HtmlRenderFactory.scala) maps shared elements to browser renderers. [CreateTestWorkbook.scala](modules/client/src/main/scala/it/evadid/homepage/workbook/content/CreateTestWorkbook.scala) provides small authoring examples, including QR and email interactions. Larger factories are in the same content directory. Runtime context and language/storage controls live under `modules/client/src/main/scala/it/evadid/homepage/control/`.

## Workbook entry pages

| Page | Content |
| --- | --- |
| [homepage/index.html](homepage/index.html) | Landing page |
| [workbookDesign](homepage/workbookDesign/index.html) | Test workbook and interaction examples |
| [embroideryWorkbook](homepage/embroideryWorkbook/index.html) | Embroidery workbook |
| [compressionWorkbook](homepage/compressionWorkbook/index.html) | Data compression workbook |
| [plantWorkshopWorkbook](homepage/plantWorkshopWorkbook/index.html) | Plant workshop on the workbook architecture |
| [imageRecognitionWorkbook](homepage/imageRecognitionWorkbook/index.html) | Partial image-recognition workbook with binary tables, threshold neuron and pixel feature experiments |
| [phishingWorkbook](homepage/phishingWorkbook/index.html) | Email sorting simulator and local writing practice |
| [plantWorkshop](homepage/plantWorkshop/index.html) | Separate legacy plant application |
| [feedback-demo](homepage/feedback-demo/index.html) | Feedback demonstration |

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

- [Digital workbook migration](docs/digital-workbook-migration.md): analysis of the linked PDF/ZIP exercises, missing editors and the partial online image-recognition adaptation.
- [Interaction architecture](docs/workbook-interaction-architecture.md): dependency boundaries between shared models, renderers and optional editors; state and serialization flow.
- [Email simulator](docs/email-simulator.md): state, grading, content handling and verification.
- [QR interaction](docs/qr-code-interaction.md): encoding model, requirements, region colors and reference tests.
- [Plant workshop](docs/PlantWorkshop_Quickstart.md): implemented sections and remaining migration work.
- [Eva programming state](docs/eva-editor-programming-state.md) and [Snap editor](docs/snap-editor.md): representations and editor lifecycle.
- [Snap command execution service](docs/snap-turtle-command-execution-overview.md): Pyodide boundary and current integration status.
- [Python parsing](docs/python-parsing-architecture.md): normalization and parser responsibilities.
- [Core test audit](docs/core-package-unit-tests.md): historical coverage measurements and test commands.
- [Workbook interaction roadmap](WORKBOOK_INTERACTIONS.md): teaching-resource inventory and proposed future exercises.

Markdown under bundled third-party libraries documents those distributions. Imported teaching projects retain their conceptual and historical documents; their upstream setup/team instructions do not describe the EduSquirrel repository workflow. MathWorld's README and deployment guide explain its integration here.
