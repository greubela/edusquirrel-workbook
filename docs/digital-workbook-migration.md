# PDF/ZIP workbook migration inventory

Reviewed on 2026-10-09 against the downloads linked in `homepage/index.html`, plus the embroidery PDF linked by its workbook. ZIP contents were inspected without running their programs; PDF text was extracted locally with `pdftotext`. Page references below are physical PDF pages, not exercise numbering. Figures, open-ended responses and external tools need an authoring/visual review before declaring a complete migration.

## Intentional classic/digital coexistence

Every existing classic PDF/ZIP remains downloadable under **Analoge Workbooks (PDF /ZIP)** on the central landing page. Partial native adaptations are listed separately in the [digital catalogue](../homepage/workbooks/index.html), which the central page links under **Digitale Workbooks (In Arbeit)**. A few digital cards also remain directly on the central page. `DigitalWorkbookCatalog` currently sets `inProgress` for every native entry, including editions with implemented editors and chapters. This is a catalogue/content-review label, not the implementation status of a component such as the generic worker backend.

Titles with classic and digital versions intentionally remain available in both forms until full content equivalence has been checked, including tasks, explanatory material, figures, hints and teacher resources. A working simulator or passing software tests alone does not establish content equivalence. Mons Komputarius has digital stories and recursive practice; its catalogue entry is also marked in progress. No classic PDF/ZIP for it is currently stored in this repository, so no download link is invented.

Do not replace classic links when adding a digital page or promote a migration to the finished list without that content review. `workbook-migration-links.test.mjs` protects the existing downloads, the link to the digital catalogue and native adaptation registrations; browser catalogue tests check the rendered cards. It does not require duplicating every digital card on the central landing page.

## Resource-to-interaction map

| Workbook / source | Observed activities | Reuse now | Missing specialized interactions/editors |
| --- | --- | --- | --- |
| [Bitcoin ZIP](../resources/workbookpdfs/20260908WorkbookBitcoin.zip): learner PDF, 29 pages; help cards and solutions | Claim/research table (pp. 1–2), third-party advantages/disadvantages (p. 5), balances and signed transaction ledger (pp. 6–13), square-middle hashing (pp. 14–15), SHA-256 experiments (pp. 16–17), linked blocks/mining/tampering (pp. 18–24), energy arithmetic and final argument (pp. 25–29) | Text inputs, choices, answer tables, collapsible hints, Hashq/SHA-256 interactions, fullscreen linked-block editor with bounded mining | Learner activities now adapted; supplementary-hint and pedagogical content-equivalence review remains outstanding. No real wallet, key management or transactions needed. |
| [Autonomous systems ZIP](../resources/workbookpdfs/20260907WorkbookAutonomeWaffensysteme.zip): learner PDF, 50 pages; teacher version | Train and fool a classification model (pp. 5–8); 2×2-pixel network configuration/training/test statistics (pp. 11–19); saliency/Clever Hans/overfitting (pp. 20–26); case analysis, criteria hierarchy and ethical argument (pp. 28–43) | Text, choices, reorder, sorting with reasons; threshold neuron and binary pixel canvases for introductory calculations | Small inspectable network/training simulator; training/test split controls and confusion matrix; annotation/saliency comparison; criteria hierarchy/argument map. The single-neuron editor does not implement multilayer training or image classification. |
| [Image recognition ZIP](../resources/workbookpdfs/20260925Bilderkennung.zip): `Workbook_Teil1.pdf`, 8 pages; `Workbook_Teil2.pdf`, 10 pages | Hazard marking (part 1 p. 2), technology comparison and searching images, flowchart/algorithm discussion; pool decision/binary encoding/weights and threshold (part 1 pp. 6–8); pixel/hidden-neuron recognition, grid game and network extension (part 2 pp. 1–5), MNIST/generalization and advisory argument (pp. 6–9) | Text, choices, answer tables, binary pixel canvases and threshold-neuron interaction; robustness counterfactuals, generalization reflections and school-chatbot research/advice | Image hotspots/annotation; multilayer network editor; local grid-game state. Full source figures, early chapters and detailed network-design activities still need authoring. |
| [Face recognition ZIP](../resources/workbookpdfs/20251117ArbeitsheftGesichtserkennung.zip): PDF, 29 pages; Python working material | Compare embeddings (p. 6), color clusters (p. 7), k-nearest-neighbor votes/ties (pp. 11–13), k-means and unknown faces (pp. 14–16), algorithm implementation (pp. 17–20), vary k on LFW examples (pp. 22–24), evidence and reflection (pp. 25–29) | Text, code exercises for supported languages, choices | Scatter/cluster editor with saved labels; deterministic k-NN/k-means step simulator; distance/vote table; explicit unknown-class policy. ZIP includes Python scripts and packaged support files: do not import their dependencies into shared core. Real face detection/model downloads are unnecessary for the coordinate exercises. |
| [Phishing ZIP](../resources/workbookpdfs/20260907WorkbookPhishing.zip): main workbook, warning/technical/deepening modules; Python mailbox | Inspect sender and hidden URLs, sort messages, compare attempts and explain judgments; domain spoofs/Unicode, network transmission, psychology, spearphishing, malware and espionage | Mail model/fullscreen simulator, warning-sign evidence, hostname/domain tables, local Unicode code-point comparison, attachment/malware exercises and final checklist | Main-workbook explanations/attempt comparison and further transmission/certificate, psychology, spearphishing and espionage modules; reusable screenshot/hotspot annotation. Current domain tables are authored examples, not a general URL parser or trust oracle. |
| [Evacuation PDF](../resources/workbookpdfs/20211110EvakuierungGitterautomat.pdf), 20 pages | Configure/run a grid automaton, record evacuation steps and convert to time (pp. 7–9); compare layouts, congestion and model realism (pp. 10–15) | Numeric/text inputs, saved floor construction and local simulation comparisons; locker/door experiments, budget selections and model critique | The complete EVA2 simulator already exists in `modules/client/src/main/scala/it/evadid/evacuation/eva2` and runs at [evadid.it/eva2](https://evadid.it/eva2/). Reuse its `EvaFloorMap`, `ScenarioEditorMode`, player, strategies and movement rules. Local workbook playback and saved comparisons now reuse the engine/player. Remaining work is exact source layouts, fuller palettes, source material and exact layout equivalence, not a new simulation engine. |
| [Visual Novel PDF](../resources/workbookpdfs/20250330ArbeitsheftVisualNovel.pdf), 19 pages | Scratch movement/variables/lists and code predictions (pp. 3–9); scene spreadsheet/CSV import with positions (pp. 10–11); data-structure comparison (pp. 12–14); nested scene tables/lists (pp. 15–16) | Text, single/multiple choices, existing programming editors for their supported subset | Structured scene-table editor with validated CSV import/export and scene preview; local project/file submission; list/nested-data inspector. Existing Snap turtle conversion does not support every Scratch story block or scene asset. |
| [Embroidery PDF](../resources/workbookpdfs/20250402StickmaschineArbeitsheft.pdf), 23 pages | Turtle primitives/coordinates (pp. 2–5), repetitions/pattern recreation (pp. 6–12), custom blocks/parameters (pp. 13–17), modulo table/conditions (pp. 18–20), recursion and creative design (pp. 21–23) | Existing turtle shape recreation, Eva/Snap editors, text/numeric inputs and answer tables | Further authored patterns and constraints; reliable execution/grading for recursion/custom blocks and design export. Existing visual previews do not imply complete automated pattern/command-limit grading. |

## Implementation structure

Native adaptations follow the [interaction architecture](workbook-interaction-architecture.md):
shared saved values and exercise configuration, a registered client renderer, and
an optional standalone editor. Inline questions reuse existing text, choice,
table and plotting interactions. Open research and judgments remain ungraded;
software tests do not establish pedagogical equivalence.

Workbook factories live under
`modules/client/src/main/scala/it/evadid/homepage/workbook/content/`.
Shared interaction implementations live under
`modules/core/shared/src/main/scala/it/evadid/workbook/elements/interactionElements/`;
their browser renderers live under
`modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/interactionRenderer/`.
Appearance belongs in `homepage/css/workbook/digital-workbooks.css` and other
dedicated component stylesheets.

## Image recognition

The partial native edition covers threshold-neuron experiments, binary-pixel
features, robustness/generalization reflections and school-chatbot research.
Neuron grading accepts any parameters matching the authored examples. These are
inspectable fixed-rule experiments, not network training or complete digit
recognition; matching the examples does not establish generalization.

Robustness variants are explicitly identified digital counterfactuals, not exact
reproductions of every source figure. The workbook distinguishes distribution
shift, adversarial examples and overfitting. It downloads no model or dataset.
Diagram annotation, original image activities, the grid game, multilayer network
design and full source-figure review remain outstanding.

## Phishing warning signs, domains and attachments

The edition combines the [mail simulator](email-simulator.md) with warning-sign
evidence, domain/Unicode analysis, attachment risks and the final checklist.
Domain and file-risk judgments require human reasoning. The domain examples do
not implement a public-suffix resolver or certify a URL as safe. Unicode analysis
preserves the original code points, including malformed UTF-16, without
normalizing away the evidence. Non-Latin characters are not inherently suspicious.

Main-workbook explanations, screenshots, attempt comparisons and further network,
certificate, psychology, spearphishing and espionage modules still need adaptation.
Keep the classic downloads available during that work.

## Blockchain

The first native adaptation covers the learner activities through source page 29:
trust and ledgers, pseudonyms, square-middle/SHA-256 experiments, linked blocks,
mining/tampering, energy and final judgments. Initial claims and final assessments
are saved separately. Supplementary hints and pedagogical changes still need
content equivalence review.

The shared models use exact arithmetic and explicit teaching assumptions. Mining
runs in bounded batches, yields to the UI and cancels on edits or close. The
simulator demonstrates proof of work; it does not implement a wallet, Bitcoin
header serialization, signature verification or network consensus. Research
questions record sources/dates rather than hard-code changing network facts.

Important source corrections are retained in the lesson:

- The square-middle solution sheet's 35/45 collision is incorrect; 35 and 65
  produce the same middle-digit hash under the stated rule.
- SHA-256 preimages are computationally infeasible, not mathematically impossible.
  Single-hash text experiments differ from Bitcoin block hashing.
- Longest-chain reasoning assumes equal difficulty; Bitcoin compares cumulative
  work. Addresses and signatures have different roles.
- The source's energy period is inconsistent. Classroom estimates state their
  assumptions, and allocated energy per transaction is not marginal energy cost.

## Evacuation: reuse the existing simulator

The EVA2 engine already exists in
`modules/client/src/main/scala/it/evadid/evacuation/eva2/` and at
<https://evadid.it/eva2/>. Workbook editors reuse its construction and playback
facilities with injected local state; they must not change the standalone
application's global state. Shared saved floor/experiment values are independent
of the client-only EVA2 model, connected through a client adapter.

The partial workbook includes construction, local playback, immutable saved run
comparisons, locker/door experiments, coordinate graphs, budget proposals and
model critique. Animation state is transient. Runs have finite budgets and retain
explicit evacuated, blocked or step-limit outcomes. A blocked run must not be
plotted as a successful evacuation. Later layout edits do not rewrite earlier
measurements or predictions.

Digital layouts are authored teaching examples, not exact reproductions of the
source sports hall. The PDF's 1.2 m door cannot be represented exactly by 0.5 m
cells. Time conversion follows the source cell-size/speed assumption, including
its simplified treatment of diagonal movement; animation speed is independent.
Construction criteria do not certify reachability or real-world safety. Original
geometry, source diagrams/newspaper material and full equivalence remain to be
reviewed. PDF page 15 repeats locker-gap instructions under a speed heading;
that apparent source error is not duplicated as another activity.

The retained [migration plan](../tmp/evacuation/NEXT_MIGRATION_STEP.md) contains
source-review references and acceptance criteria.

## Separate embroidery preview edition

The geometric-preview edition has its own workbook identity and saved answers;
the original raster edition and PDF remain available. It covers geometric
recreation, modulo/conditionals, recursion and open design. Drawing comparison
does not verify algorithm choice, command limits, physical stitch suitability or
machine export. See [edition notes](embroidery-preview-workbook.md).

## German and English native editions

Each native catalogue entry advertises German and English and supplies lesson
and editor translations. Language switches preserve the workbook and its saved
answers. Keep placeholders and download URLs intact, and make encoded examples
match the translated plaintext.

Original downloadable documents, figures and simulator sample data remain source
materials in their original languages. Older standalone/external offerings have
separate localization requirements. Unused imported language-map entries need
translation when a native component begins using them.

## Verification

Use the [test guide](core-package-unit-tests.md) for shared-platform, client and
browser checks. Migration checks belong in `tools/dev/`: link tests protect
classic/digital coexistence, content tests compare authored material, and browser
checks cover editing, cancellation, language switching and saved restoration.
These checks complement manual source and visual review.

```sh
npm run test:static
sbt buildClientDev buildWorkerDev
npm run test:workbooks
```
