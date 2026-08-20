# Branch `snap_editor-yanneck`
---

## Ausgangslage auf `main`

Der Snap-Editor existierte schon: `SnapCodeEditor` mountet eine eingebettete
TurtleStitch/Snap!-IDE in ein Canvas und kann ein `BeProgram` als Blöcke darstellen.
Nicht vorhanden war alles darum herum:

- `ProgrammingExercise.deserialize` gab `println("does not work yet!")` aus, nichts wurde
  über einen Reload hinweg gespeichert.
- Die Palette war eine Demo mit drei Tabs („One block“, „Three blocks“, „drawing“).
- Es gab keinen Weg von den Blöcken zu Python und zurück.
- Das Programm ließ sich nicht ausführen.

---

## zentrale Designentscheidung

**Snap-XML ist Source of Truth für den Editorzustand. `BeProgram` und Python sind abgeleitete
Sichten für Tests, Feedback und das Python-Overlay.**

```
Snap-IDE
   │  getProjectXML / rawOpenProjectString
   ▼
ProgrammingExerciseState(snapXml)
   │  persist SNAP_XML_V1
   ▼
LocalStorage / Sync
   │
   ├─ TurtleStitchWorker (Ausführung direkt mit XML)
   └─ TurtleStitchToBeExpressionParser → BeProgram → Python / Tests / Vorschau
```

Live-Edits speichern `ide.getProjectXML()` unverändert. Damit bleiben auch Blöcke erhalten, die
der Python-Roundtrip nicht kennt. `BeProgram` wird nur noch dort gebaut, wo das Workbook Semantik
braucht.

Alte Python-Payloads werden beim Laden einmalig nach XML migriert
(`BeProgram.fromPythonString` + `SnapProjectXml`). Canvas-Positionen leben in `<script x y>`.

---

## 1. Persistenz: versioniertes Snap-XML

`ProgrammingExercise` ist `WorkbookInteractionElement[ProgrammingExerciseState]` mit
`snapXml: String`.

Serialisiert wird:

```
SNAP_XML_V1
<project …>…</project>
```

Beim Lesen werden weiterhin akzeptiert: `SNAP_XML_V1`, rohes `<project>`-XML und reines Python.
Geschrieben wird nur noch XML.

---

## 2. Wann gilt ein Programm als geändert?

Der Editor schreibt in beide Richtungen: User ändert Blöcke → XML wird persistiert → der State
aktualisiert sich. Ohne Vergleich würde derselbe State den Editor sofort wieder neu laden
(Edit → speichern → Reload → Edit-Event).

Deshalb gelten zwei Zustände als gleich, wenn ihr XML-String gleich ist
(`ProgrammingExerciseState.fingerprint`). Eigene Speichervorgänge werden so nicht als fremde
Updates behandelt. Ein Restore aus Sync/LocalStorage hat einen anderen String und wird geladen.

Das XML wird dafür nicht über den AST umgeschrieben und neu serialisiert, sonst gingen unbekannte
Blöcke verloren (die aber eigentlich aktuell gar nicht auftreten können sollten). 
Snap darf das XML beim ersten Öffnen umsortieren; gespeichert wird danach das von Snap gelieferte XML.

---

## 3. Der Übersetzungs-Layer Snap ↔ AST

### 3.1 XML-Parsing: string-first

Für die Python- und AST-Ansicht muss das Snap-XML gelesen werden. Dafür existieren ein DOM-basierter und ein direkter textbasierter Parser. Da der DOM-Pfad bei XML aus der laufenden Snap-IDE teilweise abstürzt oder keine Blöcke findet, wird für Blockprojekte bevorzugt der textbasierte Parser verwendet. Die Persistenz ist davon nicht betroffen, gespeichert wird das ursprüngliche XML.

Der String-Parser ist inzwischen der verlässlichere Pfad, auch wenn das vielleicht unschön ist(?)

Beide Parser-Pfade liegen in:

`modules/client/src/main/scala/it/evadid/homepage/workbook/legacy/interactionPlugins/fileSubmission/turtleStitch/TurtleStitchXmlParser.scala`

Die Auswahl und der erneute String-Parser-Versuch passieren in:

`modules/client/src/main/scala/it/evadid/homepage/workbook/legacy/interactionPlugins/fileSubmission/turtleStitch/TurtleStitchXmlLoader.scala`

### 3.2 Was übersetzt wird

| Snap | AST | Python |
|------|-----|--------|
| `receiveGo`, `forward`, `turn`, `gotoXY`, `setHeading`, `clear`, `down`, `up` | `BeFunctionCall` | `receive_go()`, `forward(n)`, `goto_x_y(x, y)`, … |
| `doRepeat` | `BeRepeatNr` | `for _ in range(n):` |
| `doIf` / `doIfElse` | `BeIfElse` | `if c:` / `else:` |
| `doUntil` | `BeWhile` (Bedingung invertiert) | `while not c:` |
| `doSetVar` / `doChangeVar` / `<block var>` | `BeAssignVariable` / `BeUseValue` | `x = v` / `x = x + n` / `x` |
| `reportVariadicLessThan` etc., `reportNot`, `reportTrue/False` | Operator-`BeFunctionCall` | `a < b`, `not a`, `True` |

Snap hat keinen `while`-Block, nur `doUntil` — die Negation passiert deshalb genau an der
Snap-Grenze und wird beim Zurückschreiben wieder eingekürzt, damit `while not X` als `doUntil X`
landet und nicht als `doUntil not not X`. Analog erkennt der Serializer das Muster `x = x + n`
und macht daraus wieder einen `doChangeVar`-Block statt eines `doSetVar` mit Additions-Reporter.

### 3.3 `SnapControlFlow`

`SnapControlFlow` bündelt die gemeinsamen Übersetzungs- und Validierungsregeln für Kontrollstrukturen, Variablen und Operatoren. So verwenden XML-Parser und Python-Bridge dieselben Regeln. `SnapProjectXml` erzeugt nur bei der Migration alter Python-Programme und beim Anwenden von Python neues Snap-XML. Änderungen im Blockeditor speichern dagegen direkt das von Snap erzeugte XML.

---

## 4. Python als abgeleitete Ansicht

Im Fullscreen gibt es ein Overlay mit editierbarem Python. Laden geht XML → AST → Printer
(`SnapProgramDerivation`). „Apply to blocks“ parst den Text, prüft das Subset und schreibt neues
Snap-XML. Enthält das aktuelle XML nicht übersetzbare Blöcke, zeigt das Overlay den ableitbaren
Stand mit Warnung und **deaktiviert Apply**, damit kein vollständiges Snap-Projekt durch ein
verlustbehaftetes Subset ersetzt wird.

---

## 5. Palette: konfigurierbar, mit einer Python-sicheren Variante

`LibraryTab` kann jetzt entweder eine eigene Blockliste zeigen oder per `useNativeCategory` an
Snaps eigene `blockTemplates` delegieren; `includeVariableControls` blendet zusätzlich Snaps
„Make a variable“-Steuerung ein.

Darauf aufbauend gibt es zwei Paletten:

- **`StandardSnapCategories`** — die acht nativen Snap-Tabs. Ersetzt die alten Demo-Tabs.
- **`PythonCompatibleSnapCategories`** — explizite Allow-List: Motion (`forward`, `turn`,
  `gotoXY`, `setHeading`), Pen (`clear`, `down`, `up`), Control (`receiveGo`, `doRepeat`, `doIf`,
  `doIfElse`, `doUntil`), Operators (Vergleiche, `and`/`or`/`not`, `reportTrue/False`),
  Variables (`doSetVar`, `doChangeVar` + Controls). Looks, Sound und Sensing fehlen bewusst.

Ausgewählt wird das **pro Übung**, über ein Core-Enum `ProgrammingEditorPalette`
(`Default` | `PythonCompatibleSnap`) an `ProgrammingExercise`. Der Renderer übersetzt es in eine
`SnapCodeEditorConfig`.

Warum die Zweiteilung? 

Die native Palette bietet mehr Blöcke, aber nur das Subset aus
Abschnitt 3.2 überlebt den Python-Roundtrip (aktuell). Aufgaben, bei denen der Roundtrip genutzt werden soll, nehmen die
eingeschränkte Palette.

---

## 6. Sync-Protokoll zwischen `Var` und der lebenden IDE

Snap ist eine lebende JS-Anwendung mit eigenem Zustand; ein naives „bei jeder State-Änderung neu
laden“ führt zu Reload-Schleifen und verlorenen Edits. Das Interface `SnapCodeEditorImpl` hat
deshalb vier explizit getrennte Operationen:

| Methode | Bedeutung |
|---------|-----------|
| `loadProgramIfChanged` | Nur laden, wenn das Ziel-XML wirklich anders ist |
| `forceLoadProgram` | Immer laden — für Fullscreen-Open, wo „acknowledged“ nicht heißt, dass Snap es auch anzeigt |
| `acknowledgeProgramFromEditor` | Nur markieren „das steht schon im Editor“, ohne Reload |
| `flushPendingProjectChanges` | Ausstehende Edits sofort publizieren (vor Ausführen, vor Unmount) |

Dazu: Die Observer hängen an `state.signal.changes`, nicht am Initialwert, weil beim Mount ohnehin
schon geladen wird. Der Editor wird zwischen Fullscreen-Öffnungen behalten statt neu gebaut
(nur die Morphic-Cycles pausieren).

---

## 7. Eingriffe außerhalb des Snap-Editors

Das ist der Teil, der bei einem Merge nach `main` Aufmerksamkeit braucht, weil er geteilten Code
betrifft (mergen habe ich schon zu einem Großteil gemacht, aber trotzdem relevant):

| Ort | Änderung | Warum |
|-----|----------|-------|
| `BeStartProgram` | `structureInfo` war `???`, ist jetzt implementiert (`withReplacedChildren`, `toJavaStyleLines`, `getChildrenAndExtension`) | Ohne das lässt sich ein Programm gar nicht drucken |
| `BeExpressionToPythonString` | Operator-Calls werden anders gedruckt (`a < b` statt `<(a, b)`); `for _ in repeat(n)` → `for _ in range(n)` | Vorher war die Ausgabe kein gültiges Python (zumindest nach meinem Wissen) |
| `TurtleStitchWorkerFacade` | Auskommentierter Worker-Code reaktiviert, plus `getExecutedStageSnapshotDataSrc` über `simulateGreenFlag` | Für die Ausführung (Abschnitt 8) |
| `turtlestitchsrc/gui.js` | Patch in `IDE_Morph.prototype.createCategories` | TurtleStitch hat mehr als Snaps acht Standardkategorien; `noDefaultCat` versteckte nur `children[0..7]`, der Rest blieb sichtbar und die Höhenformel schnitt eigene Tabs ab |

Der `gui.js`-Patch ist eine Änderung an vendored Fremdcode und muss bei einem TurtleStitch-Update
neu angewendet werden.

Dazu kommen neue Language-Map-Keys (`openEditor`, `canvas`, `runProgram`, `turtleOutput`,
`staticPreviewProgram`) in allen Sprachen, auch wenn die Blöcke bisher nur auf Englisch sind.

---

## 8. zwei getrennte Ausführungsmöglichkeiten

| Pfad | Mechanismus | Zweck |
|------|-------------|-------|
| Run-Card im Workbook | gespeichertes `snapXml` → `TurtleStitchWorker.simulateGreenFlag` | Endergebnis, ohne die IDE zu brauchen |
| Execute im Fullscreen | Live-IDE `runScripts` + Frame-Mirror der Stage | Scratch-artige Animation beim Nachvollziehen |

Getrennt, weil die Karte auch ohne geöffneten Editor funktionieren muss (der Worker kennt nur XML),
die Animation aber zwingend die IDE braucht.

---

## 9. Einschränkungen / offene Punkte

- **Nur ein Subset ist Python-roundtrip-fähig.** Kommentare, Snap-`elseif`-Ketten, Arithmetik
  außerhalb `x = x + n` und alle übrigen nativen Snap-Blöcke bleiben im XML erhalten, blockieren
  aber Python-Apply.
- **Lose Blöcke** sind im XML echte `<script>`-Stacks und überleben Speichern/Reload. Im
  abgeleiteten `BeProgram` sind sie weiterhin eine flache Statement-Liste, 
  TODO: am Besten den Nutzer beim Schließen darauf hinweisen und dann lose Blöcke beim Schließen entfernen oder andere Lösung finden
