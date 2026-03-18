# Python Exercises Interaction Plugin: Design Document

## 1) Overview

Provide a Laminar/Scala.js interaction plugin that lets workbook authors embed client-side Python programming exercises. Students write code in a browser-based editor, execute it inside a sandboxed Pyodide runtime, and receive automated feedback based on author-defined unit tests. The plugin mirrors the architecture of existing interaction plugins (e.g., GPT and Turtle) by exposing a `HtmlPythonExercise` that can be embedded in sections and that orchestrates editor state, grading, and visualization.

---

## 2) Goals & Non-Goals

**Goals**

* Browser-only execution of pure-Python programs using Pyodide, initiated from Scala.js.
* Deterministic, sandboxed evaluation with configurable timeouts.
 * Unit test-based grading with visible and hidden test sets and partial credit.
 * Seamless integration with the workbook interaction framework (states, graders, Laminar components).
 * Separation of editor UI, runtime service, and grading orchestration for extensibility.
 * A professional editor experience via CodeMirror with Python syntax highlighting and line numbers.

**Non-Goals**

* Running CPython extensions that require native binaries unavailable in Pyodide.
* Persisting user results or analytics—host applications remain responsible.
* Providing full filesystem or unrestricted network access; Pyodide sandbox limitations apply.
* Managing long-term package caching beyond browser-level caching.

---

## 3) Primary Use Cases

1. **Practice with Immediate Feedback** – Learner writes a function, runs visible tests, and inspects stdout/stderr and per-test status.
2. **Autograded Assignments** – Exercises define weighted visible and hidden tests; grading results return aggregated scores to the host.
3. **Library-Backed Tasks** – Authors request additional pure-Python packages (installed through Pyodide) for exercises.
4. **Data-Driven Problems** – Exercises ship small fixtures (JSON/CSV) mounted in the Pyodide virtual filesystem before execution.

---

## 4) Key Design Decisions

1. **Runtime Choice: Pyodide** – Use the browser-hosted CPython runtime. Loaded lazily via the global `loadPyodide` function; error handling surfaces missing runtime issues gracefully.
2. **Isolation Strategy** – Execute Python inside a dedicated `PythonRuntime` service that encapsulates Pyodide interactions, allowing future Worker migration without impacting UI code.
3. **Editor Implementation** – Provide a Laminar-wired CodeMirror editor (loaded on demand from CDN assets) with Python syntax highlighting, indentation control, and read-only toggling aligned with interaction states.
4. **Grading Model** – `PythonGrader` orchestrates test execution via the runtime, builds structured grading results, and maps partial passes to `GradingGrade` enums.
5. **Visualization** – UI components follow the interaction framework contracts (`InteractionComponent`, `HtmlFullInteractionExercise`) to enable consistent rendering alongside other plugins.

---

## 5) High-Level Architecture

```
┌────────────────────────────┐      commands/results      ┌────────────────────────────┐
│ HtmlPythonExercise         │ ───────────────────────►   │ PythonRuntime (Pyodide)     │
│ • Laminar editor & buttons │                           │ • Manages Pyodide lifecycle │
│ • Binds workbook states    │   ◄──────────────────────  │ • Executes code + tests     │
│ • Shows results            │        structured report   │ • Collects stdout/stderr    │
└────────────────────────────┘                           └────────────────────────────┘
```

**Message Flow**

* `gradeState` writes learner code and tests into the runtime, triggers execution, and returns JSON summaries.
* Runtime reports per-test details (status, message, duration, visibility flags) plus stdout/stderr aggregates.
* UI reacts to grading results via Laminar signals and updates the editor/result panels.

---

## 6) Data & Domain Model

* **PythonExerciseContent**
  * `id: String`
  * `titleMap: Map[AppLanguage, String]`
  * `instructionMap: Map[AppLanguage, String]`
  * `estimatedTimeInMinutes: Double`
  * `starterCode: String`
  * `visibleTests: Seq[PythonUnitTest]`
  * `hiddenTests: Seq[PythonUnitTest]`
  * `packages: Seq[String]`
  * `fixtures: Seq[PythonFixture]`
  * `timeoutMs: Int`
  * `memoryLimitMb: Int`

* **PythonUnitTest**
  * `name: String`
  * `code: String`
  * `weight: Double`
  * `hint: Option[String]`

* **PythonFixture**
  * `path: String`
  * `content: String`
  * `isBinary: Boolean`

* **PythonRunRequest** – Bundles code, tests, fixture definitions, package list, and limits for the runtime.
* **PythonRunResult** – Aggregated runtime status, per-test outcomes, stdout/stderr, score, and error metadata.
* **PythonGradingResult** – Wraps run results in workbook feedback structures (`FeedbackStatus`, `GradingGrade`).

---

## 7) Public API (Host ↔ Plugin)

Scala host applications interact with the plugin via the existing workbook abstractions:

* `HtmlPythonExercise` – Entry point for embedding the exercise; exposes DOM element.
* `PythonExerciseContent` – Author-supplied metadata, code template, tests, fixtures, and runtime requirements.
* `PythonGrader.gradeState` – Called when learners run tests; returns `PythonGradingResult` asynchronously.
* `PythonScaffolder.generateFeedback` – Placeholder scaffolding hook (currently returns informational text).

Within the plugin, `PythonRuntime.run(request)` hides Pyodide specifics and returns `Future[PythonRunResult]`.

---

## 8) Implementation Structure

```
interactionPlugins/pythonExercises/
├── PythonExercisesDesign.md          (this document)
├── PythonExerciseContent.scala       (exercise metadata + helper domain classes)
├── PythonExerciseStates.scala        (editor/grading states + result models)
├── PythonRuntimeService.scala        (Pyodide bridge + execution harness)
├── PythonGrader.scala                (grader + scaffolder wiring)
├── HtmlPythonInteractionModel.scala  (connects model/controller/visualizer)
├── HtmlPythonInteractionContainer.scala
├── HtmlPythonEditorComponent.scala   (editor + result Laminar components)
└── HtmlPythonExercise.scala          (exercise facade)
```

Each file remains small and focused, easing future maintenance and testing.

---

## 9) Grading Model

* Each `PythonUnitTest` contributes `weight` points; total score is normalized to 1.0.
* Passed tests earn full weight; failed tests yield zero for their weight.
* Hidden tests run silently but still contribute to scores; UI surfaces only aggregated impact and optional hints when permitted.
* Overall grade mapping:
  * Score == 1.0 → `GradingGrade.CORRECT`
  * 0.0 < Score < 1.0 → `GradingGrade.PARTIALLY_CORRECT`
  * Score == 0.0 → `GradingGrade.INCORRECT`
  * Runtime/setup failures → `GradingGrade.GRADING_ERROR`

---

## 10) Performance Strategy

* Lazy-load Pyodide and reuse the runtime for subsequent executions.
* Pre-install requested packages only once per session.
* Streamline execution script to minimize string concatenation overhead.
* Optionally migrate to a Web Worker-backed runtime in future iterations without UI changes (thanks to `PythonRuntime` abstraction).
* Load CodeMirror assets only once per session via `CodeMirrorLoader` and reuse the instantiated editor.

---

## 11) Security & Sandboxing

* Delegate execution to Pyodide, inheriting browser sandbox constraints.
* Block filesystem/network operations not explicitly mounted or enabled.
* Clear fixture files between runs to avoid state leakage.
* Enforce wall-clock timeouts by cancelling the underlying Pyodide task when necessary (to be refined in future iterations).

---

## 12) Error Handling & Diagnostics

* Distinguish between compilation/runtime errors, assertion failures, and infrastructure issues (e.g., missing Pyodide).
* Surface stdout/stderr plus structured test messages to the UI.
* Preserve hidden test secrecy by redacting code while still flagging failures.
* Provide actionable hints when authors supply them for visible tests.

---

## 13) Internationalization & Accessibility

* Exercise titles and instructions originate from localized `LanguageMap` inputs on `PythonExerciseContent`.
* Editor interactions remain keyboard-friendly thanks to CodeMirror's native accessibility features; results are rendered with semantic lists and headings for assistive technologies.
* Further localization of static labels (e.g., "Python code") can be layered on later through shared workbook utilities.

---

## 14) Telemetry (Optional)

* Expose grading results through existing interaction callbacks; future telemetry hooks can listen to these updates without altering the runtime/editor separation.

---

## 15) Limitations & Risks

* Initial Pyodide load size can impact perceived responsiveness on slow networks.
* Package availability is limited to WebAssembly-compatible distributions.
* Long-running CPU-bound code may block the main thread until a Worker migration is implemented.

---

## 16) Fit Check & Iteration

After mapping the initial concept onto the existing workbook framework, we validated two critical aspects:

1. **Structural Fit** – The plugin mirrors the GPT/Turtle directory layout and uses the shared interaction abstractions (`HtmlFullInteractionExercise`, `FullInteractionExerciseModel`, `InteractionComponent`). Runtime access is isolated in `PythonRuntimeService`, ensuring future Worker adoption or caching changes stay local.
2. **Use Case Coverage** – Each primary use case is backed by explicit hooks: practice feedback via `PythonGrader`, autograding via weighted tests, package/fixture support in `PythonRunRequest`, and data tasks via fixture mounting. Hidden tests are executed but redacted in UI summaries to protect assessment integrity. Future iterations can extend the layout with additional panels (e.g., a console) without disrupting the core workflow.

This iteration confirms that the design aligns with technical constraints and fulfills the targeted learner and author experiences while remaining consistent with other interaction plugins.
