# Branch `snap_editor-yanneck`
---

## Ausgangslage auf `main`

Der Snap-Editor existierte schon: `SnapCodeEditor` mountet eine eingebettete
TurtleStitch/Snap!-IDE in ein Canvas und kann ein `BeProgram` als Blöcke darstellen.
Nicht vorhanden war alles darum herum:

- `ProgrammingExercise.deserialize` gab `println("does not work yet!")` aus — nichts wurde
  über einen Reload hinweg gespeichert.
- Die Palette war eine Demo mit drei Fantasie-Tabs („One block“, „Three blocks“, „drawing“).
- Es gab keinen Weg von den Blöcken zu Python und zurück.
- Das Programm ließ sich nicht ausführen.
- `BeStartProgram.structureInfo` war `???`, das Drucken eines Programms flog also auf.

Der Branch macht daraus eine benutzbare Programmier-Interaktion.

---

## Die eine zentrale Designentscheidung

**`BeProgram` bleibt Source of Truth. Snap-XML ist nur Transportformat an der Editor-Grenze.
Canvas-Positionen liegen als Sidecar daneben. Persistiert wird Python-Text.**

```
Snap-IDE (Projekt-XML)
   │  TurtleStitchXmlParser / XmlLoader          XML → Modell
   │  TurtleStitchToBeExpressionParser           Modell → BeExpression (+ Layout)
   ▼
ProgrammingExerciseState(program: BeProgram, canvasLayout: SnapCanvasLayout)
   │  BeExpressionToPythonString / PythonParser  AST ↔ Python-Text
   ▼
LocalStorage / Sync   (Python-Text + Layout-Header)
   │
   ▼
TurtleStitchFromBeExpressionSerializer          BeExpression + Layout → XML → Snap-IDE
```

Die naheliegende Alternative wäre gewesen, einfach das Snap-XML zu persistieren. Dagegen sprach:
Das XML ist ein fremdes, breites Format, das wir weder validieren noch mit Tests, Feedback oder
dem restlichen Workbook-Stack verbinden können. Alles, was der Rest des Systems über
Programme weiß, hängt an `BeProgram`. Der Preis dieser Entscheidung ist die Übersetzungsschicht,
die den größten Teil dieses Branches ausmacht.

Zweite Entscheidung derselben Familie: **Canvas-Layout gehört nicht in den AST.** Snap speichert
jeden Stack auf dem Canvas als eigenes `<script x y>`. Diese Information ist reine UI-Metadatik.
Sie als Fake-Kommentare in den AST zu schreiben wäre möglich gewesen, hätte aber jedes
AST-Konsument (Tests, Feedback, Printer) mit Snap-Wissen verschmutzt. Stattdessen: Sidecar.

---

## 1. Persistenz: Composite-Format

`ProgrammingExercise` ist jetzt `WorkbookInteractionElement[ProgrammingExerciseState]` statt
`[BeProgram]`. Der State ist `(program, canvasLayout)`.

Serialisiert wird ein Composite aus Layout-JSON und Python-Body:

```
SNAP_LAYOUT_V1
[{"x":70,"y":80,"callCount":2},{"x":200,"y":150,"callCount":1}]
---
receive_go()
forward(100)
```

**Warum so:** Payloads ohne Header bleiben reines, lesbares Python und funktionieren als
Legacy-Format weiter. Das Layout ist optional — wer kein Layout hat, bekommt ein Skript, wie
vorher. `callCount` partitioniert die flache Statement-Liste des `BeProgram` wieder in die
einzelnen Snap-Skripte, inklusive loser Blöcke, die nicht am Hauptskript hängen.

Der Name `callCount` ist inzwischen irreführend — das Feld zählt Top-Level-**Statements**
(ein `doRepeat` mit fünf Calls darin ist `1`). Umbenennen würde das Persistenzformat brechen.

---

## 2. Change-Detection über Fingerprints statt `equals`

`BeProgram.equals` ist für getrennt gebaute ASTs unzuverlässig: Teile der AST-Datenstruktur
halten **Funktionen als Felder** (z. B. `BeDataTypeAtomic` mit `String => ...`). Funktionswerte
haben in Scala.js keine strukturelle Equality, zwei logisch identische Programme sind also
ungleich.

Deshalb vergleicht der ganze Branch über `ProgrammingExerciseState.fingerprint` =
Python-Body + Layout-JSON. Genutzt an zwei Stellen:

- `HtmlProgrammingExerciseRenderer` — nur bei geändertem Fingerprint in die `Var` schreiben bzw.
  persistieren.
- `SnapCodeEditor` — unterscheiden, ob ein `Var`-Update ein Echo der eigenen Snap-Edits ist oder
  eine echte externe Änderung (Sync-Restore).

Ohne diese Unterscheidung entsteht entweder eine Reload-Schleife (Snap-Edit → State → Observer →
Snap neu laden → Edit-Event → …) oder verworfene Schreibvorgänge.

---

## 3. Der Übersetzungs-Layer Snap ↔ AST

### 3.1 XML-Parsing: string-first

`TurtleStitchXmlParser` nimmt bei typischen Blockprojekten **zuerst den String-Parser**, nicht den
DOM-Walk. Grund: live aus der IDE geholtes `getProjectXML` bringt den DOM-Pfad in dieser Runtime
regelmäßig zum Absturz. DOM bleibt Fallback, und `TurtleStitchXmlLoader` versucht es zusätzlich
noch einmal mit erzwungenem String-Pfad, wenn das Ergebnis leer ist, das XML aber `<block`
enthält.

Das ist unschön, aber der String-Parser ist inzwischen der verlässlichere Pfad.

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

### 3.3 `SnapControlFlow` als geteilte Mitte

Parser, Serializer und die Python-Bridge brauchen **dieselbe** Antwort auf „welche Konstrukte sind
erlaubt und wie sehen sie im AST aus“. Vorher lag das verstreut. Jetzt liegt es in
`SnapControlFlow` (Core, shared): Selector-Mengen, Operator-Mapping in beide Richtungen,
rekursive Validierung, `invertCondition`, `changeVarAmount` — und der `VariableInterner`.

Der Interner ist nicht optional: Ohne ihn erzeugen `doSetVar x` und ein späterer Lesezugriff auf
`x` zwei verschiedene `BeDefineVariable`-Instanzen, und der Serializer verliert die Verbindung.
Er nutzt bewusst `BeEntityName.fromLiteral` statt `fromCodeString`, weil letzteres Namen
normalisiert und Snap den umbenannten Namen beim Zurückladen nicht wiedererkennt.

**Wenn du nur eine Datei aus diesem Layer lesen willst, dann diese.**

---

## 4. Python als zweite Sicht auf dasselbe Programm

Im Fullscreen gibt es ein Overlay mit editierbarem Python. „Apply to blocks“ parst den Text und
schreibt über denselben `onStateEdited`-Pfad zurück wie die Blöcke selbst. Damit sind Blöcke und
Python zwei gleichberechtigte Sichten auf ein `BeProgram`, nicht Quelle und generierte Ausgabe.

`SnapTurtlePythonBridge` ist der Eingang dieses Pfades: Python-Text → `BeProgram` → Validierung
gegen das Subset → State. Wird etwas Nicht-Unterstütztes gefunden, gibt es eine Meldung und die
Blöcke bleiben unverändert stehen — kein teilweiser Apply.

Damit dieser Roundtrip überhaupt möglich ist, mussten Printer und Parser der VM angepasst werden
(siehe Abschnitt 7): Operatoren infix drucken, `for _ in range(n)` statt `for _ in repeat(n)`,
und der `EvaParsingHint`-Kommentar entfällt in Python, weil die Bridge Kommentare ablehnt und
gedrucktes Repeat-Python sich sonst nicht selbst wieder anwenden ließe.

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

**Warum als Enum im Core und nicht als Config im Client:** Welche Blöcke eine Aufgabe anbietet, ist
eine didaktische Eigenschaft der Aufgabe und gehört ins Workbook-Modell. Die konkrete
Snap-Konfiguration bleibt Client-Detail.

Die Zweiteilung ist ehrlich gemeint: Die native Palette bietet mehr Blöcke, aber nur das Subset aus
Abschnitt 3.2 überlebt den Python-Roundtrip. Aufgaben, bei denen der Roundtrip zählt, nehmen die
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
schon geladen wird. Der Editor wird zwischen Fullscreen-Öffnungen **behalten** statt neu gebaut
(nur die Morphic-Cycles pausieren), weil ein Neuaufbau der Snap-Welt teuer ist und Zustand verliert.

---

## 7. Eingriffe außerhalb des Snap-Editors

Das ist der Teil, der bei einem Merge nach `main` Aufmerksamkeit braucht, weil er geteilten Code
betrifft:

| Ort | Änderung | Warum |
|-----|----------|-------|
| `BeStartProgram` | `structureInfo` war `???`, ist jetzt implementiert (`withReplacedChildren`, `toJavaStyleLines`, `getChildrenAndExtension`) | Ohne das lässt sich ein Programm gar nicht drucken |
| `BeExpressionToPythonString` | Operator-Calls werden **infix** gedruckt (`a < b` statt `<(a, b)`); `for _ in repeat(n)` → `for _ in range(n)` | Vorher war die Ausgabe kein gültiges Python |
| `GenericJavaLikeStringPrinter` | Neuer Hook `repetitionParsingHint`, Default unverändert, Python überschreibt auf leer | Python braucht den `EvaParsingHint`-Kommentar nicht mehr; Java & Co. bleiben gleich |
| `PythonParser` / `PythonStatementParser` | Neue Regel `for _ in range(n):` → `BeRepeatNr` | Ersatz für den entfallenen Parsing-Hint |
| `DisplayControl.setFullscreen` | State-Update jetzt **vor** dem Lifecycle-Callback | Ein behaltener Editor muss den Dialog ausmessen können, den es sonst noch nicht gibt |
| `TurtleStitchWorkerFacade` | Auskommentierter Worker-Code reaktiviert, plus `getExecutedStageSnapshotDataSrc` über `simulateGreenFlag` | Für die Ausführung (Abschnitt 8) |
| `turtlestitchsrc/gui.js` | Patch in `IDE_Morph.prototype.createCategories` | TurtleStitch hat mehr als Snaps acht Standardkategorien; `noDefaultCat` versteckte nur `children[0..7]`, der Rest blieb sichtbar und die Höhenformel schnitt eigene Tabs ab |

Der `gui.js`-Patch ist eine Änderung an vendored Fremdcode und muss bei einem TurtleStitch-Update
neu angewendet werden.

Dazu kommen neue Language-Map-Keys (`openEditor`, `canvas`, `runProgram`, `turtleOutput`,
`staticPreviewProgram`) in allen Sprachen.

---

## 8. Ausführung: zwei bewusst getrennte Pfade

| Pfad | Mechanismus | Zweck |
|------|-------------|-------|
| Run-Card im Workbook | `TurtleStitchWorker.simulateGreenFlag` im Worker → ein PNG | Endergebnis, ohne die IDE zu brauchen |
| Execute im Fullscreen | Live-IDE `runScripts` + Frame-Mirror der Stage | Scratch-artige Animation beim Nachvollziehen |

Getrennt, weil die Karte auch ohne geöffneten Editor funktionieren muss (der Worker kennt nur XML),
die Animation aber zwingend die lebende IDE braucht.

---

## 9. Bewusste Grenzen und offene Punkte

- **Nur ein Subset ist roundtrip-fähig.** Kommentare, Snap-`elseif`-Ketten, Arithmetik außerhalb
  `x = x + n`, negative oder nicht-ganzzahlige Repeat-Zähler und alle übrigen nativen Snap-Blöcke
  werden beim Python-Apply abgelehnt.
- **Nur globale Variablen.** Sprite-lokale Variablen und Listen laufen nicht durch.
- **Lose Blöcke** — offene Designfrage. Sie werden über `SnapCanvasLayout` positionsgetreu
  gespeichert, sind im `BeProgram` aber nicht vom vorhergehenden Skript trennbar. Alternativen:
  beim Speichern verwerfen, oder das Layout um echte Skriptgrenzen erweitern.
- **`callCount` heißt falsch** (siehe Abschnitt 1) und bleibt vorerst so, weil der Name im
  persistierten JSON steht.
- **String-first XML-Parsing** ist ein Workaround für DOM-Abstürze, keine saubere Lösung.
