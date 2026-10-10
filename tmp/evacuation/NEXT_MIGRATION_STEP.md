# Next evacuation workbook migration step

## Goal

Replace the authored comparison-room examples with a verified adaptation of the original sports-hall scenario, preserving the workbook's saved answers and reusing EVA2. This is a migration handoff plan; the source-equivalent layout has not yet been implemented or verified.

## Sources and current state

- The evacuation source is [20211110EvakuierungGitterautomat.pdf](../../resources/workbookpdfs/20211110EvakuierungGitterautomat.pdf). Page 7 asks learners to load a sports-hall scenario from a separate WueCampus text document. The PDF alone does not provide that load code.
- Candidate native scenarios already exist in [DefaultFloors.scala](../../modules/client/src/main/scala/it/evadid/evacuation/eva2/model/DefaultFloors.scala), including `dfSportsHall40`, `dfSportsHall70`, `dfSportsHall120` and `tdSportsHall40`. Their names do not establish equivalence with the PDF scenario.
- The digital workbook already includes local floor construction, simulation playback, immutable run records, model-time conversion, locker/door comparisons, saved prediction/observation plots, budget choices and model critique. Its 11 × 9 locker room is an authored example, not the original sports hall.
- `Workbook_Teil1.pdf` and `Workbook_Teil2.pdf` currently stored in this directory are image-recognition workbook materials, despite their inherited directory name. Do not use them as evacuation sources.

## Implementation sequence

1. Verify the source geometry. Compare the PDF sports-hall illustrations and tasks with the existing native scenarios. Record dimensions, exits, locker positions, initial occupants, sprite movement properties and any unavailable information. Obtain or locate the original load code if the illustrations do not establish these details. Keep the current example explicitly labelled as an example until equivalence can be demonstrated.
2. Check representation before importing. `EvacuationFloorPlan` and `EvacuationFloorAdapter` currently use a small palette and bounded dimensions. Audit which source sprites and dimensions they cannot represent. Extend the shared saved model only for required behavior; validate serialized answers and preserve previously saved floors. Do not silently flatten source obstacles, widen doors or change movement properties.
3. Introduce verified presets in `CreateEvacuationWorkbook`, reusing `EvacuationSimulationInteraction` → its renderer → `EvacuationSimulationEditor`. Continue delegating movement and routing to EVA2. Preserve existing interaction/answer IDs and independent prediction, observation and run histories. Changing an exercise default must not overwrite a learner's existing saved answer.
4. Bring the relevant source illustrations and instructions into the digital locker and door tasks. Put published images in `resources/workbookresources/evacuation/`, bilingual text in the existing language maps, and presentation in dedicated CSS. Keep investigation extracts and captures in ignored `tmp/work/`. Explain the 0.5 m grid limitation and the PDF's 1.2 m graph reference; do not invent a reference evacuation time.
5. Verify source parity and persistence before marking this step complete. Document any deliberate adaptation or unresolved source ambiguity.

## Acceptance and validation

- Geometry tests verify the confirmed dimensions, occupant count, exits, locker positions and required movement properties; adapter round trips must preserve them.
- Regression tests cover serialized old/new floor answers, immutable measurements, deterministic playback and blocked scenarios. Keep shared-core code independent of browser-only EVA2 classes.
- Workbook tests verify stable activity IDs, bilingual content and complete workbook serialization. Browser checks cover editing, comparison runs, independent graphs and reload persistence.
- Run the relevant core JVM/JS and client suites, `sbt buildJS`, and the existing evacuation editor, simulation, source-activity, coordinate-plot, content and stylesheet checks under `tools/dev/`.
- Retain the classic PDF link and the separate **Digitale Workbooks (In Arbeit)** entry until the whole workbook passes content-equivalence review. This step alone does not establish full equivalence or real-world evacuation safety.

## Later migration work

After source geometry and associated activities are verified, finish the source newspaper/diagram material, any needed scenario import or palette controls, and a page-by-page content-equivalence audit. Existing implementation history and architectural notes remain in [the migration guide](../../docs/digital-workbook-migration.md) and [the interaction architecture guide](../../docs/workbook-interaction-architecture.md).
