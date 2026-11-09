# EV3 robot workbook with Pybricks: discovery and concept

Status: phase 0 (discovery and concept, no product code). Branch `Roberta`.
Owner: Stephan. Target: a Roberta/Girls'Day workshop with ten LEGO MINDSTORMS
Education EV3 sets running the Pybricks firmware.

Learners program the robot in the regular Eva editor with a Roberta-filtered
Snap palette. The browser runs the program only as a simulation with a graphical
representation; Python and Java remain the usual alternative views of the same
`ProgrammingState`. Before a program is transferred to a brick, it is converted
to Pybricks MicroPython, compiled and checked again. The same checks are meant
to run during editing as scaffolding.

## Decisions

| Id | Decision | Reason |
| --- | --- | --- |
| D1 | The host is the existing Eva/Snap editor, not a separate Blockly app. A new `ProgrammingEditorPalette` case selects a Roberta block set; the editor, state model and language switching stay shared. | One programming surface for all workbooks; the earlier Blockly plan (August 2026 hand-over in `Arbeit Uni\Roberta\Codes Work`) is superseded. |
| D2 | Generated code targets the public Pybricks Python API only. The official block editor module `pybricks-blocks` is proprietary and is never copied, decompiled or bypassed. | License boundary. Firmware, API, `pybricksdev`, `pybricks-code` and `mpy-cross` are MIT. |
| D3 | Hardware facts (motor ports and directions, wheel diameter, axle track, sensor ports) live in a typed hardware profile owned by the workbook content, never in block defaults or saved answers. | Ten builds may differ; the generator and the simulation read the same profile. |
| D4 | Phase 1 ships motor-level motion (degrees, rotations) plus display, light, sound, wait and console blocks. Distance/turn blocks in cm and degrees follow once wheel diameter and axle track are measured. | No assumed geometry; the connection test from the Roberta folder is the first reproducible target program. |
| D5 | In the browser the program runs in the Pyodide worker against a `pybricks` stub package written in Python. The stub records events through the existing callback channel, keeps a virtual clock and answers sensor reads from a scenario. The worker contract does not change. | Reuses the worker, transport and timeout handling; the stub follows the `java_turtle` installation-source pattern; keeps shared core free of hardware code. See "Contract changes in detail". |
| D6 | Transfer to the brick is a later phase and runs in the browser over WebUSB with the open Pybricks protocol, compiled with the `@pybricks/mpy-cross-v6` WASM compiler. Until then the workbook exports `main.py` for the institute's `pybricksdev` setup. | `pybricks-code` shows the whole chain is open; the thin protocol client is about 200 lines. |
| D7 | Java stays a representation of the same program (the existing Java bridge pattern), executed only in the simulation. The brick receives Python. | Pybricks has no Java runtime; "no duplicated behavior" (EvaStructure). Scope is settled with Ultrich's Java/scaffolding work. |
| D8 | Language switching is a requirement. Block labels, palette tab names and panel texts come from Snap translation dictionaries and language maps; German and English are mandatory, other languages fall back to English. No learner-visible text is hard-coded. | The workbook switches languages at runtime; the embroidery blocks already follow this pattern. |
| D9 | One Python program, two parts: a generated Roberta prelude (Pybricks imports, device setup from the hardware profile, small helper functions) followed by the learner's program, which keeps using flat catalog functions. The same file is simulated, exported and transferred. | The VM parser round-trips flat function calls today; method calls on device objects would need a parser extension. The prelude makes the file valid Pybricks MicroPython without that extension. |

## Discovery results

### Pybricks on EV3

- The new embedded EV3 firmware appeared with Pybricks 4.0.0b1 (2025-11-25);
  4.1.0 reached the stable channel on 2026-10-12 and 4.1.3 is current
  (2026-11-06). The legacy pages at pybricks.com still describe the ev3dev-based
  2.0 and are outdated for 4.x. The workshop firmware version must be pinned and
  verified on the institute bricks; it must not float to "latest".
- Verified at the institute (August 2026, folder `Arbeit Uni\Roberta`): LEGO
  firmware 1.10E recovery; Pybricks 4.0.1 flashed and run with `pybricksdev`
  2.3.2 over USB; Pybricks 4.1.0b3 installed through the beta web site, which
  recognizes the brick over USB. `pybricksdev` 2.3.2 cannot flash 4.1 images
  (firmware metadata 3.0.0). Reference program:
  `Roberta\programme\01_verbindungstest\main.py` (display, status light,
  battery voltage, console output, `wait`).
- API surface for the block set (docs.pybricks.com, "latest"):

| Device | Import | Calls used by the block set |
| --- | --- | --- |
| Brick | `from pybricks.hubs import EV3Brick` | `ev3.screen.print(*args)`, `ev3.screen.clear()`, `ev3.light.on(Color.X)`, `ev3.light.off()`, `ev3.speaker.beep(frequency, duration_ms)`, `ev3.buttons.pressed()`, `ev3.battery.voltage()` |
| Motor | `from pybricks.ev3devices import Motor` | `Motor(Port.B, positive_direction=Direction.CLOCKWISE)`, `run(speed_deg_s)`, `run_angle(speed, angle_deg)`, `run_time(speed, time_ms)`, `stop()`, `brake()`, `hold()`, `angle()` |
| Drive base | `from pybricks.robotics import DriveBase` | `DriveBase(left, right, wheel_diameter_mm, axle_track_mm)`, `straight(distance_mm)`, `turn(angle_deg)` (positive = right), `drive(speed_mm_s, turn_rate)`, `stop()`, `settings(...)` |
| Sensors | `from pybricks.ev3devices import TouchSensor, ColorSensor, UltrasonicSensor, GyroSensor` | `pressed()`, `color()`, `reflection()`, `ambient()`, `distance()` in mm, `angle()` |
| Parameters, tools | `from pybricks.parameters import Port, Color, Direction, Stop`; `from pybricks.tools import wait` | `Port.A..D`, `Port.S1..S4`, `Color.RED` etc., `wait(time_ms)` |

  The docs write sensor reads with `await`; without `run_task` they behave as
  plain calls, which is what the generator emits.

### pybricks-code (MIT, commit ea9af98d, 2026-04-02)

Analysis of the public repository, read only:

- Compilation happens in the browser with `@pybricks/mpy-cross-v6`
  (`compile(fileName, source, options, wasmUrl)`). `src/mpy/sagas.ts` packs each
  module as `u32 LE size + module name + NUL + .mpy bytes` ("MultiMpy6"). The
  format is chosen from the hub capability flag `UserProgramMultiMpy6`.
- The EV3 is connected with **WebUSB** (`navigator.usb`, interface class
  `0xFF/0xC5/0xF5`), not WebSerial. `src/usb/sagas.ts` opens the device, reads
  firmware version, name and capabilities (`maxWriteSize`, flags,
  `maxUserProgramSize`, slots) with control transfers, then exchanges messages on
  bulk endpoints: OUT `[0x01, subscribe]` / `[0x02, command...]`, IN
  `[0x01, u32 status]` / `[0x02, event...]`.
- Commands (`src/ble-pybricks-service/protocol.ts`, pure functions):
  `StopUserProgram=0`, `StartUserProgram=1 [slot]`, `WriteUserProgramMeta=3
  [u32 size]`, `WriteUserRam=4 [u32 offset, data]`, `WriteStdin=6`. Run
  sequence: meta(0), RAM chunks of `maxWriteSize - 5`, meta(size), start(slot).
  Stdout arrives as event `WriteStdout=1`; status reports carry the
  "program running" bit.
- No proprietary module, license check or premium gate exists in the public
  code; all `@pybricks/*` packages resolve from the public npm registry.
  Orchestration is redux-saga; the protocol encoders/decoders are reusable
  as-is.
- Constraints for us: Chromium browsers over HTTPS only; Windows may need a
  WinUSB binding for the interface; commands wait up to one second for the
  USB response; the profile version (1.5.0 at that commit) changes command
  details, so the client must branch on the reported software revision.

### Workbook architecture hooks

| Concern | Existing contract | What the EV3 workbook adds |
| --- | --- | --- |
| Block catalog | [SnapTurtleCatalog.scala](../modules/core/shared/src/main/scala/it/evadid/workbook/elements/interactionElements/programming/state/snap/SnapTurtleCatalog.scala): `Primitive(snapSelector, pythonName, aliases, turtleCommand, example, tab, extraPrimitive, extraSpec, inputKinds)` drives parsing, validation, XML rendering and callback decoding | A robot catalog with new `PaletteTab` values. `Primitive` needs a block kind (command, reporter, predicate) and typed inputs for ports and colors; `turtleCommand` is turtle-specific and must become a general command name |
| Palette | [ProgrammingEditorPalette.scala](../modules/core/shared/src/main/scala/it/evadid/workbook/elements/interactionElements/programming/state/snap/ProgrammingEditorPalette.scala) → `PaletteLayout` → `SnapCodeEditorConfig.libraryTabs`; renderers match on the palette ([HtmlProgrammingExerciseRenderer.scala](../modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/interactionRenderer/basic/HtmlProgrammingExerciseRenderer.scala)) | `ProgrammingEditorPalette.Ev3Roberta`, a `SnapCategoryColor` role for robot blocks, and a renderer branch that supplies the EV3 panel |
| Block injection | `SnapCodeEditorImplDelegateToOriginal.injectExtraPrimitives` adds non-vanilla specs (embroidery pattern); `JavaTurtleSnapPrimitives` shows `Process.prototype` implementations | Robot blocks are injected the same way; reporters need the reporter block type |
| Python view | `SnapStateConversion.expressionFromXml` renders Snap XML to Python, `BeProgram.fromPythonString` parses it, [SnapTurtlePythonBridge.scala](../modules/core/shared/src/main/scala/it/evadid/workbook/elements/interactionElements/programming/state/snap/SnapTurtlePythonBridge.scala) prints and validates the subset | Unchanged for the learner's part. A prelude generator (shared core) emits Pybricks imports, device setup from the hardware profile and the helper functions the program uses; export, simulation and transfer use prelude plus program (D9) |
| Simulation | [SnapTurtleCommandExecution.scala](../modules/client/src/main/scala/it/evadid/homepage/webElements/editor/code/SnapEditor/execution/SnapTurtleCommandExecution.scala): Pyodide worker, synthetic `turtle` module, `PythonCallback` recorder, `TurtleExecutionProvider` panels | An EV3 panel with its own runner: the `pybricks` stub package records events; recorded calls become a timeline. Drive commands map onto `TurtleCommand`s so the robot path reuses `TurtlePathBuilder` and the existing SVG renderer |
| Worker | `RunPythonRequest(code, context, callbacks, ...)` in `WorkerCommands.scala`; recorder proxies return `undefined` | Unchanged. The scenario (sensor values over time) travels in `context`; events leave through one registered callback module |
| Block labels | `adjusted/lang-<code>.js` in `resources/programs/20260212TurtleStitch/` translate injected specs by their English text (`'running stitch by _ steps': 'Laufstich in _ er Schritten'`); `Snap.changeLanguage` relabels on switch | One dictionary entry per robot block and language; tab names and panel texts through `resources/languageMaps/eva/ev3workbook/` |
| Workbook wiring | `DigitalWorkbookCatalog.entries`, `HomepageStartupLogic.turtlePages`/`tryToLoad`, `homepage/<page>/index.html`, factory under `modules/client/.../workbook/content/`, `resources/languageMaps/eva/<group>/` | `ev3Workbook` page, factory and `ev3workbook` language group (DE, EN) |

Everything existing stays untouched for turtle workbooks. Where the catalog
must grow (block kind, input kinds, general command names), callers are updated
in place; no aliases are introduced.

## Contract changes in detail

One shared contract has to grow for robot blocks (section 1). It is small, but
it touches code every turtle workbook uses, so the team should know it before
phase 1 starts. Sensor values in the simulation (section 2) looked like a second
contract change but are purely additive.

### 1. Reporter and predicate primitives in the catalog

Today every catalog `Primitive` is a command block:

- `injectExtraPrimitives` in `SnapCodeEditorImplDelegateToOriginal` writes
  `type = "command"` for every injected spec, so Snap offers injected blocks only
  as statements. A sensor block such as "Taster gedrückt?" must be a predicate
  (hexagonal, usable in `wenn`/`warte bis`), "Abstand in cm" a reporter.
- `SnapControlFlow.isSupportedCondition` and `isSupportedValue` accept only
  operators, literals and variables. A call to a catalog primitive inside a
  condition is rejected, so `while not touch_pressed():` would not round-trip.
- The parts that already work: `SnapStateConversion.value` renders a catalog
  block in expression position as `python_name(args)`, and
  `SnapProjectXml.renderCondition`/`renderCall` write a primitive call back as
  `<block s="selector">` wherever it appears.

Change: `Primitive` gets a `kind` (command, reporter, predicate) and a reported
type; `ExtraBlockSpec` carries it to the injection; `isSupportedCondition` and
`isSupportedValue` accept catalog reporters and predicates with valid arguments.
`SnapInputKind` gains `Port`, and `turtleCommand` becomes a general command
name. Existing turtle primitives default to `kind = command`, so their behavior
does not change. Tests: catalog round-trip specs on JVM and JS for a predicate
in `doIf`, `doUntil` and `reportNot`.

### 2. Sensor values in the simulation (no contract change)

The worker recorder (`PyodideCommandRuntime`) registers one JS module per
callback entry whose functions log `PythonCallback(module, method, args)` and
return `undefined`. That is enough for turtle commands, which never return
anything. A robot program needs `touch_pressed()` to return a boolean and
`distance_cm()` a number, otherwise loops never end and comparisons fail.

The earlier idea of teaching the recorder to return values is not needed. The
`java_turtle` path already installs Python source into `sys.modules` before the
program runs (`JavaTurtlePythonHelpers.installationSource`). The EV3 simulation
does the same with a `pybricks` stub package written in Python:

- `EV3Brick`, `Motor`, `DriveBase`, sensors, `Port`, `Color`, `wait` exist as
  Python classes and functions, with the signatures of the real API.
- Every call appends an event (virtual time, device, method, arguments) by
  calling one registered callback module, so the existing recorder channel
  carries the result unchanged.
- `wait`, `run_angle`, `run_time`, `straight` and `turn` advance a virtual
  clock; nothing sleeps. A budget of virtual time and events ends `while True`
  programs with a reported stop instead of a worker timeout.
- Sensor reads return values from a scenario the panel passes in
  `RunPythonRequest.context`: scripted values over virtual time in phase 2, an
  environment model (walls for distance, lines for reflection) later.

Consequences: no change in `WorkerCommands`, the worker handler or the Pyodide
runtime; one new shared Python source constant beside the Java helpers; one
runner class in the client that builds the request and decodes the events. The
only convention other code must respect is the prelude marker of D9, and it is
read only by the EV3 workbook's own conversion.

## Simulation options

Robot Karol Online (karol.arrrg.de, MIT, React/Vite, Blockly) was evaluated as
the graphical simulation, as suggested in the team kickoff:

- World model: a grid with bricks, marks and walls; the robot steps one cell
  and turns by 90°. Conditions are `IstWand`, `IstZiegel`, `IstMarke` and the
  four compass directions. No distance, color/reflection or touch sensing.
- Inputs: Karol language, Blockly, a Python mode with an undocumented API, a
  Java mode. No library or `postMessage` API; the only external hook loads a
  quest JSON through the URL hash. UI and commands are German only.

Assessment: the grid model does not fit a differential-drive robot that moves
in centimeters, turns in degrees and reads analog sensors; adapting it would
replace its command model, sensors, language handling and embedding. Its quest
format (start world, target world, comparison) is a useful pattern for task
definitions.

Decision: the workbook gets its own simulation, in three layers that reuse the
turtle infrastructure:

1. Event timeline from the stub (display lines, light, sound, console, motor
   calls) rendered as a log beside the editor. Phase 2, no geometry needed.
2. Robot path: `straight`/`turn` and motor-pair moves become `TurtleCommand`s;
   `TurtlePathBuilder` and the SVG renderer draw the path on a top-down plan,
   with the robot as a marker. Needs wheel diameter and axle track.
3. Environment: obstacles and colored lines on the plan feed distance and
   reflection values back into the scenario; task targets compare the final
   pose or path, like the turtle recreation grader. Later phase, after the
   workshop tasks are fixed.

Karol remains an option for separate grid-based activities, not for the EV3
simulation.

## Block set draft

Labels below are the German dictionary entries; the English spec text is the
key in every translation file, and the Snap selector plus the Python name are
the contract. Ports come from the hardware profile unless a block names one.

| Tab | Block (German label) | Learner Python | Prelude maps to |
| --- | --- | --- | --- |
| Robot (phase 1) | Motor `%port` um `%n` Grad drehen (Geschwindigkeit `%n`) | `motor_run_angle("B", 90, 200)` | `motors["B"].run_angle(speed, angle)` |
| Robot (phase 1) | Motor `%port` `%n` Umdrehungen drehen | `motor_run_rotations("B", 2, 200)` | `run_angle(speed, rotations * 360)` |
| Robot (phase 1) | Motor `%port` starten mit `%n` Grad/s · anhalten | `motor_run("B", 200)` · `motor_stop("B")` | `run(speed)` · `stop()` |
| Robot (phase 2) | `%n` cm vorwärts fahren · `%n` Grad drehen · anhalten | `drive_straight(20)` · `turn_degrees(90)` · `drive_stop()` | `robot.straight(cm * 10)` · `robot.turn(deg)` · `robot.stop()` |
| Sensors (predicate, reporters) | Taster gedrückt? · Abstand in cm · Farbe · reflektiertes Licht | `touch_pressed()` · `distance_cm()` · `color_name()` · `reflection()` | `touch.pressed()` · `ultrasonic.distance() / 10` · `color.color()` · `color.reflection()` |
| Output | zeige `%s` · Display löschen · Ton `%n` Hz für `%n` ms · Licht `%color` an · Licht aus · in Konsole schreiben `%s` | `screen_print(text)` · `screen_clear()` · `beep(f, ms)` · `light_on("green")` · `light_off()` · `print(text)` | `ev3.screen.print` · `ev3.screen.clear` · `ev3.speaker.beep` · `ev3.light.on(Color.GREEN)` · `ev3.light.off` · `print` |
| Control | warte `%n` s (existing `doWait`) · wiederhole · wiederhole fortlaufend · wenn/sonst · warte bis | `wait_seconds(1)` · `for _ in range(n)` · `while True` · `if/else` · `while not cond:` | `wait(ms)` · native Python |

Port and color inputs are string slots with Snap dropdown menus so the Python
stays plain function calls; the prelude resolves them to `Port.B` and
`Color.GREEN`.

Generated program shape (D9), from the connection test. The prelude is
generated from the hardware profile and the devices the program uses; the
learner's part is the editing Python the Snap project already round-trips:

```python
# --- Roberta prelude (generated) ---
from pybricks.hubs import EV3Brick
from pybricks.parameters import Color
from pybricks.tools import wait

ev3 = EV3Brick()

def screen_print(text):
    ev3.screen.print(text)

def light_on(color):
    ev3.light.on(getattr(Color, color.upper()))

def wait_seconds(seconds):
    wait(int(seconds * 1000))
# --- program ---
screen_print("Pybricks laeuft")
light_on("green")
print("Verbindung zum EV3 hergestellt.")
wait_seconds(5)
ev3.light.off()
```

Drive and sensor helpers (`drive_straight(cm)`, `turn_degrees(deg)`,
`touch_pressed()`, `distance_cm()`) are defined the same way on top of
`DriveBase` and the sensor objects. Reading the file back into blocks strips
the prelude at its marker; the Python tab shows the learner's part.

## Verification chain before transfer

1. Subset validation of the Snap project (`validateSubset`, arity and block
   allow-list), as for turtle programs.
2. Python syntax check in the worker (`compile()` in Pyodide).
3. Bytecode compilation with `@pybricks/mpy-cross-v6` and a size check against
   the brick's `maxUserProgramSize`.
4. Only then transfer (phase 3) or export of `main.py`.

Steps 1 and 2 run during editing as automatic graders (`runningBehavior`),
step 3 on request. LLM hints attach through the shared scaffolding contract once
Ultrich's `ProgrammingState` scaffolding is available.

## Phases

| Phase | Deliverable | Done when |
| --- | --- | --- |
| 0 | This document; team alignment on D1–D7 | Decisions confirmed, open questions assigned |
| 1 | Robot catalog with reporter kinds, `Ev3Roberta` palette, prelude generator, hardware profile type, German and English dictionaries, `main.py` export; the connection test rebuilt from blocks | Golden-file tests for Snap → Python and prelude on JVM and JS; palette visible and switchable between German and English in the test workbook; no hardware involved |
| 2 | Simulation panel: timeline of calls, display/light/console log, robot path for drive blocks; scripted sensor values; DriveBase blocks after measurement | Browser checks for run/cancel/unmount; path matches `TurtlePathBuilder` expectations |
| 3 | WebUSB transfer client in `it.evadid.facades`, mpy-cross compilation, stdout panel | Real EV3 run logged with firmware version; failures documented, never marked passed without a brick |
| 4 | `ev3Workbook` content: workshop tasks, DE/EN language maps, catalogue entry | Workbook browser suite passes; tasks reviewed with the workshop team |

## Open questions

1. Hardware profile values: motor ports and directions, wheel diameter, axle
   track, sensor ports per build; whether all ten robots are identical.
2. Firmware pin for the workshop: 4.0.1 (verified with `pybricksdev`) or a 4.1.x
   stable build with WebUSB; who installs it and how recovery is documented.
3. Windows USB driver for the Pybricks interface on the workshop PCs, and
   whether those PCs may run Chrome or Edge over HTTPS against evadid.it.
4. Scope of the Java view for EV3 (facade class, checked runtime), aligned with
   Ultrich's Java editing and scaffolding work.
5. Sensor simulation depth: scripted values versus an environment model with
   obstacles and lines.
6. Known issue "SnapEditor: language switching and browser scaling" (Yanneck)
   affects D8; the relabeling of injected blocks on a switch must be verified
   with the embroidery palette first. Palette tab names are English constants
   today (`LibraryTab.name`) and need language-map IDs.

## Verification

Phase 1 adds shared tests next to `ProgrammingStateConversionSpec` for the
Pybricks printer and catalog, and client tests for the palette config:

```sh
sbt 'coreJVM/testOnly *Ev3*' 'coreJS/testOnly *Ev3*' 'client/testOnly *Ev3*'
```

Hardware steps are recorded only after they were executed on a brick, with the
firmware version and the transport used.
