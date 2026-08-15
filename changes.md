# Strukturelle Änderungen — Snap Editor Persistenz

## Architektur (unverändert im Großen)

`BeProgram` bleibt Source of Truth für Programmsemantik. Snap-Canvas-Layout liegt als Sidecar daneben. Snap ist nur View an der Editor-Grenze.

```
Snap IDE (XML)
    │  TurtleStitchToBeExpressionParser (+ Layout)
    ▼
ProgrammingExerciseState(BeProgram, SnapCanvasLayout)
    │  composite serialize
    ▼
LocalStorage / Sync
    │  deserialize
    ▼
State → TurtleStitchFromBeExpressionSerializer → Snap IDE (XML)
```

Kein Wechsel auf reine XML-Persistenz. Bestehende Bridge-/Validation-Dateien (`SnapExpressionBridge`, `TurtleStitchXmlValidation`) bleiben.

---

## 1. Persistenz-Schicht (`ProgrammingExercise`)

- Interaction-Typ: `WorkbookInteractionElement[ProgrammingExerciseState]`
- State: `program: BeProgram` + `canvasLayout: SnapCanvasLayout`
- Serialize: Composite `SNAP_LAYOUT_V1` + Layout-JSON + Python-Body (ohne Layout: reines Python für Alt-Daten)
- Deserialize: Composite oder Legacy-Python → `ProgrammingExerciseState`
- Leerer/ungültiger Payload → `defaultValue` (`mini`)

`deserialize` ist der „Restore“-Schritt: ohne ihn landet man nach Reload beim Default statt beim zuletzt editierten Inhalt.

---

## 2. Change-Detection über Fingerprints

`BeProgram.equals` ist für separat gebaute ASTs unzuverlässig, weil Teile der AST-Datenstruktur **Funktionen als Felder enthalten**. Ein konkretes Beispiel ist `BeDataTypeAtomic` (siehe `BeDataType.scala`): dort sind u. a. Felder vom Typ `String => ...` gespeichert. Solche Funktionsobjekte haben in Scala/JS keine strukturelle Equality, d. h. selbst zwei logisch identische Programme können durch referenz-/instanzbezogene Funktionswerte beim `equals`-Vergleich auseinanderfallen.

Deshalb verwenden wir stattdessen einen **Fingerprint** aus Python-Body **plus** Layout-JSON (`ProgrammingExerciseState.fingerprint`):

| Ort | Rolle |
|-----|--------|
| `HtmlProgrammingExerciseRenderer` | Sync↔Var und Persist nur bei geändertem Fingerprint |
| `SnapCodeEditor` | Snap→State-Echo vs. externe Var-Updates unterscheiden |

Damit werden weder Schreibvorgänge fälschlich verworfen noch Echo-Reloads die IDE thrashen; reine Positionsänderungen speichern ebenfalls.

---

## 3. Editor-Sync (`SnapCodeEditor` / Impl)

Neue/klarere Sync-Semantik:

- `loadProgramIfChanged` — nur laden, wenn state→XML sich geändert hat
- `forceLoadProgram` — immer laden (Fullscreen-Open), sonst bleibt Snap auf dem alten `rawOpen`-Projekt
- `acknowledgeProgramFromEditor` — markiert state→XML als „schon im Editor“, ohne `rawOpen`
- `publishProgramFromSnapXml` — blanke Parses (keine callable Blocks) schreiben nicht; gleiche Fingerprints nur acknowledge

Der Grund für diese Trennung ist ein bidirektionales Sync-Problem:  
Snap produziert nach einem Edit neues XML → daraus wird ein State → dieser fließt wieder in die Observer zurück. Ohne Guards würde das entweder zu unnötigen Reloads (Reload-Loop/Thrashing) führen oder beim Fullscreen-Open den falschen Snap-Zustand sichtbar lassen.

Darum gilt:
- **`loadProgramIfChanged`** macht nur dann einen `rawOpenProjectString`, wenn die Ziel-XML wirklich anders ist.
- **`forceLoadProgram`** umgeht genau diesen Skip beim Fullscreen-Open, weil „Acknowledge“ hier nur logisch markiert, aber nicht garantiert, dass Snap visuell neu „rawOpen“-t.

Die Observer hängen an `state.signal.changes` (nicht am Initialwert beim Mount), damit beim Mount nicht sofort ein redundantes Reload ausgelöst wird: initial wird ohnehin durch `renderEditorInto(state.now(), ...)` geladen.

---

## 4. Renderer-Anbindung (`HtmlProgrammingExerciseRenderer`)

- `Var[ProgrammingExerciseState]` an `interactionVariable` gebunden
- Edits über `onStateEdited` → `persistFromEditor` → `setStateFromUserInteraction(..., MAJOR)`
- Fingerprint-Guard verhindert No-Op-Writes

Auch hier geht es um bidirektionale Konsistenz:
- Wenn der persistierte State (z. B. aus LocalStorage) wiederhergestellt wird, soll die `Var`/UI aktualisiert werden.
- Wenn Snap selbst gerade einen Edit publisht, darf derselbe Inhalt nicht nochmal „als fremde Änderung“ persistiert werden.

---

## 5. XML-Parsing robuster (TurtleStitch-Pipeline)

### `TurtleStitchXmlParser`
- Bei `<block` / `<custom-block>`: String-Parser zuerst (DOM-Walk crasht oft auf live `getProjectXML`)
- Sonst DOM, bei Fehler String-Fallback
- `parseStringOnly` für erzwungenen String-Pfad
- String-Fallback erkennt nested Blocks/Literals korrekt; Scripts ohne Blocks werden ignoriert

Warum: Live Snap-XML kann im DOM-Pfad in dieser Runtime/Umgebung instabil sein (z. B. JSExceptions beim DOM-Walking).  
Deshalb wird für „typische Snap Block Projekte“ frühzeitig auf einen String-basierten Parser umgeschaltet.

### `TurtleStitchXmlLoader`
- Wenn Primary-Parse keine Blocks liefert, XML aber `<block` enthält → Retry mit `parseStringOnly`
- `TurtleStitchXmlValidation` wieder eingehängt (warnen, trotzdem mappen)

### `TurtleStitchToBeExpressionParser`
- Program body enthält nur Calls
- `hasCallableBlocks` als Leer-Kriterium
- `parseXmlWithLayout` / Recovery liefern zusätzlich `SnapCanvasLayout` (pro Top-Level-`<script>`)

---

## 6. Lose Blöcke / Multi-Script (Sidecar-Layout)

Snap speichert jeden Canvas-Stack (Hauptskript oder loser Block) als eigenes `<script x y>`. Der alte Roundtrip flatten’t alles in eine `BeSequence` und schreibt wieder **ein** Skript — lose Blöcke klebten deshalb ans Hauptskript.

**Ansatz:** Layout nicht im AST (keine Fake-Comments), sondern als Sidecar neben dem Python-Body.

- `SnapCanvasLayout` / `SnapCanvasScript(x, y, callCount)` — Script-Grenzen + Positionen
- `ProgrammingExerciseState(program, canvasLayout)` — Interaction-Typ
- Persistenz-Composite:

```
SNAP_LAYOUT_V1
[{"x":70,"y":80,"callCount":2},{"x":200,"y":150,"callCount":1}]
---
receiveGo()
forward(100)
turn(90)
```

- Alt-Payloads ohne Header bleiben reines Python → Layout leer → bisheriges 1-Skript-Verhalten
- Serializer splittet Calls nach `callCount` in N `<script>` (Orphans ohne synthetisches `receiveGo`)

Warum Sidecar: `BeProgram` bleibt semantisch sauber; Canvas-UI-Metadaten gehören nicht in den AST.

Offene Frage: Wie mit losen Blöcken umgehen? Aktuell werden diese gespeichert und können über CanvasLayout als solche gespeichert werden. In BeProgramm sind diese jedoch nicht vom vorhergehenden Skript trennbar. Man könnte sie stattdessen removen oder ganz anders mit ihnen umgehen. 
---

## Datenfluss nach den Fixes

```
Edit in Snap
  → getProjectXML
  → parse (+ layout)
  → ProgrammingExerciseState (nur wenn callable blocks)
  → Fingerprint ≠ last?
      → Var + InteractionVariable (MAJOR) + LocalStorage (composite)

Reopen Fullscreen
  → forceLoadProgram(state)
  → program+layout → XML → rawOpen + Libraries

Page Reload
  → LocalStorage composite
  → deserialize → ProgrammingExerciseState
  → Editor/Preview laden aus Var
```

---

## Betroffene Dateien

| Datei | Strukturelle Rolle |
|-------|-------------------|
| `ProgrammingExercise.scala` / `ProgrammingExerciseState.scala` / `SnapCanvasLayout.scala` | Composite Persist + State |
| `HtmlProgrammingExerciseRenderer.scala` | Fingerprint-Bindung + Persist-Callback; Run-Card → `SnapTurtleStage.run` |
| `SnapCodeEditor.scala` | Fingerprint-Echo-Skip, Fullscreen force-load; Fullscreen-Shell + Turtle-Panel |
| `SnapCodeEditorImplDelegateToOriginal.scala` | `force`/`acknowledge` inkl. Layout→XML; live Green-Flag + Stage-Mirror |
| `TurtleStitchXmlParser.scala` | string-first Parse für Block-Projekte |
| `TurtleStitchXmlLoader.scala` | Retry + Validation |
| `TurtleStitchToBeExpressionParser.scala` | Call-only Body + Recovery + Layout |
| `TurtleStitchFromBeExpressionSerializer.scala` | Multi-Script XML aus Layout |
| `SnapTurtlePythonBridge.scala` | Turtle-Subset Python apply + Layout-Reconcile |
| `SnapPythonPopup.scala` | Editierbares Python (Apply → Bridge → State) |

---

## 7. Fullscreen Turtle Panel + Execute

Rechts neben dem Snap-Canvas im Fullscreen: Turtle-Ausgabe mit Ausführen-Button. Die Workbook-Run-Card bleibt (finales PNG über Worker).

### UI

- `SnapCodeEditor.getDomElement()` → Flex-Shell `.be-program-snap-fullscreen` (Snap links, Panel rechts)
- `SnapTurtleStagePanel.chrome` — Titel (`basic/turtleOutput`), Run (`basic/runProgram`), Mirror-Canvas
- CSS: `workbook-structure.css` (Shell), `workbook-interactions.css` (`FEATURE: SnapTurtleStagePanel`)

### Zwei Run-Pfade

| Pfad | Mechanismus | UX |
|------|-------------|-----|
| Workbook Run-Card | `SnapTurtleStage.run` → Worker `simulateGreenFlag` → ein PNG | Sofortiges Endergebnis |
| Fullscreen Execute | Live-IDE `runScripts` + RAF-Mirror von `stage.fullImage()` | Scratch-artige Animation |

Fullscreen-Taktung: Snap Visible Stepping mit `flashTime = 0.05` s pro Block (nach Run/Stop wiederhergestellt).

Vor Execute: `flushPendingProjectChanges`, damit Slot-Text mitgenommen wird.

### Palette

- Tab **Three blocks**: zusätzlich `setHeading` (point in direction / Blickwinkel)

### Dateien (Turtle)

| Datei | Rolle |
|-------|--------|
| `SnapTurtleStage.scala` | Shared Worker-PNG-Helper (nur Run-Card) |
| `SnapTurtleStagePanel.scala` | Fullscreen-Panel-Chrome + Callbacks |
| `SnapCodeEditor.scala` | Shell-Verdrahtung |
| `SnapCodeEditorImplDelegateToOriginal.scala` | `runGreenFlagOnStage` / `stopGreenFlagOnStage` + Stepping |
| `SnapFacades.scala` | `StageMorph`, `runScripts`, `stopAllScripts`, … |
| `SnapCodeEditorConfig.scala` | `setHeading` in Three blocks |
| `HtmlProgrammingExerciseRenderer.scala` | Card nutzt `SnapTurtleStage.run` |

---

## 8. Dual-Mode: Python ↔ Blocks (Turtle-Subset)

Editierbares Python im Fullscreen (`Edit Python` Overlay) schreibt über denselben `onStateEdited`-Pfad wie Snap zurück.

### Bridge (`SnapTurtlePythonBridge`)

- Allow-List (Python snake_case): `receive_go`, `forward`, `turn`, `goto_x_y`, `clear`, `set_heading`, `down`, `up` (Snap: `receiveGo`, `gotoXY`, `setHeading`, …)
- Reject: Assignments, `if`/`while`, Comments, Unsupported/Unparsable (Warnung, Blocks bleiben)
- Layout: wenn `sum(callCount)` gleich bleibt → Layout behalten; sonst `SnapCanvasLayout.single`
- Leeres Python → leeres Layout

### UI

- CodeMirror im Overlay; **Apply to blocks** / **Reload from blocks** / Close
- Apply parst den aktuellen Text; Reload holt erneut von den Blocks (nach Flush)
- Nach Apply: `onStateEdited` → Fingerprint-Sync lädt Snap neu

### Später (nicht MVP)

Mapping von `doRepeat`/`doIf` ↔ Python-Kontrollstrukturen und größere Allow-List.
