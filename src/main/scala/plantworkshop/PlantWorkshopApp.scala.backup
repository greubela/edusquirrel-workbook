package plantworkshop

import com.raquo.laminar.api.L._
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.annotation._
import scala.scalajs.js.timers._

// JAVASCRIPT ZEUG
@js.native
@JSGlobal("CodeJar")
class CodeJar(element: dom.Element, highlight: js.Function1[dom.Element, Unit]) extends js.Object {
  def updateCode(code: String): Unit = js.native
  def onUpdate(callback: js.Function1[String, Unit]): Unit = js.native
  def destroy(): Unit = js.native
}

@js.native
@JSGlobal("Prism")
object Prism extends js.Object {
  def highlightElement(element: dom.Element): Unit = js.native
}

case class CodeSnippet(id: Int, text: String)

object PlantWorkshopApp {

  // --- GLOBAL STATE ---
  val currentTask: Var[Int] = Var(0) // 0-5: Motivation, Checklist, Moisture, Pumpe, Combined, Test
  val isAdvancedMode: Var[Boolean] = Var(false)
  val completedTasks: Var[Set[Int]] = Var(Set.empty)

  /** Entry point to render the workshop into a given container */
  def render(container: dom.Element): Unit = {
    com.raquo.laminar.api.L.render(container, appElement)
  }

  // --- MAIN UI ---
  def appElement: HtmlElement = {
    div(
      className := "container",
      
      // Navigation & Progress
      navigationBar(),
      
      // Task Content (dynamisch basierend auf currentTask)
      div(
        className := "task-content",
        child <-- currentTask.signal.map {
          case 0 => Task0_Motivation.render()
          case 1 => Task1_ComponentChecklist.render()
          case 2 => Task2_MoistureSensor.render(isAdvancedMode.signal)
          case 3 => Task3_PumpControl.render(isAdvancedMode.signal)
          case 4 => Task4_Combined.render(isAdvancedMode.signal)
          case 5 => Task5_Test.render()
          case _ => div("Unbekannte Aufgabe")
        }
      ),
      
      // Navigation Buttons
      navigationButtons()
    )
  }

  // --- NAVIGATION BAR ---
  def navigationBar(): HtmlElement = {
    val taskTitles = List(
      "0. Motivation",
      "1. Bauteile & Aufbau",
      "2. Feuchtigkeit messen",
      "3. Pumpe steuern",
      "4. Messwerte & Pumpe",
      "5. Test & Fertig"
    )

    div(
      className := "navigation-bar",
      h1("Girls Day - Automatische Pflanzen-Bewässerung"),
      div(
        className := "progress-steps",
        taskTitles.zipWithIndex.map { case (title, idx) =>
          div(
            className <-- currentTask.signal.combineWith(completedTasks.signal).map { case (current, completed) =>
              val base = "progress-step"
              val active = if (current == idx) " active" else ""
              val done = if (completed.contains(idx)) " completed" else ""
              base + active + done
            },
            onClick --> { _ => currentTask.set(idx) },
            title
          )
        }
      )
    )
  }

  // --- NAVIGATION BUTTONS ---
  def navigationButtons(): HtmlElement = {
    div(
      className := "navigation-buttons",
      button(
        "← Zurück",
        className := "btn-nav",
        disabled <-- currentTask.signal.map(_ == 0),
        onClick --> { _ => currentTask.update(t => Math.max(0, t - 1)) }
      ),
      button(
        "Fertig →",
        className := "btn-nav btn-primary",
        disabled <-- currentTask.signal.map(_ == 5),
        onClick --> { _ =>
          completedTasks.update(_ + currentTask.now())
          currentTask.update(t => Math.min(5, t + 1))
          dom.window.scrollTo(0, 0)
        }
      )
    )
  }
}

// ========================================
// TASK 0: MOTIVATION
// ========================================
object Task0_Motivation {
  def render(): HtmlElement = {
    div(
      h1("Was machen wir heute?"),
      div(
        className := "task-box motivation-box",
        h3("🌱 Willkommen zum Arduino Pflanzenworkshop!"),
        p(
          "Heute lernen wir, wie man eine automatische Bewässerungsanlage mithilfe von Arduino baut. ",
          "Mit diesem Mikrocontroller werden wir einen Feuchtigkeitssensor auslesen und eine Pumpe steuern, ",
          "damit unsere Pflanzen immer genau zum richtigen Zeitpunkt Wasser bekommen."
        ),
        p(strong("Das werdet ihr lernen:")),
        ul(
          li("Wie man Sensoren ausliest (Feuchtigkeit im Boden messen)"),
          li("Wie man elektrische Geräte steuert (Pumpe an/aus schalten)"),
          li("Wie man beides verbindet (automatische Bewässerung)")
        ),
        div(
          className := "info-box",
          strong("Hinweis: "),
          "Wir arbeiten mit Arduino, einer Plattform, welche es einfach macht, Hardware mit Code zu steuern. ",
          "Keine Sorge, wenn ihr noch nie programmiert habt, wir fangen ganz von vorne an!"
        )
      ),
      div(
        className := "safety-warning",
        h3("Sicherheitshinweise"),
        ul(
          li(strong("Wasser und Strom:"), " Achtet darauf, dass keine elektrischen Teile (außer dem Sensor) mit Wasser in Berührung kommen."),
          li(strong("Stromversorgung:"), " Verwendet nur die vorgesehenen Netzteile und Spannungen."),
          li(strong("Bei Problemen oder Unsicherheiten:"), " Fragt ruhig immer nach.")
        )
      )
    )
  }
}

// ========================================
// TASK 1: COMPONENT CHECKLIST
// ========================================
object Task1_ComponentChecklist {
  def render(): HtmlElement = {
    div(
      h1("Bauteile & Aufbau"),
      div(
        className := "task-box",
        h3("Checkliste für die Bauteile"),
        p("Überprüft, ob ihr alle Teile habt:"),
        div(
          className := "component-checklist",
          checklistItem("Arduino Board", "Über dieses Mikrocontroller-Board werden wir mithilfe von Code die Sensoren und Pumpe steuern"),
          checklistItem("Feuchtigkeitssensor", "Misst, wie nass die Erde ist"),
          checklistItem("Wasserpumpe", "Pumpt Wasser zur Pflanze"),
          checklistItem("Relais-Modul", "Schaltet die Pumpe an/aus"),
          checklistItem("Netzteil", "Stromversorgung für die Pumpe"),
          checklistItem("Schläuche & Wasserbehälter", "Zum Anschließen an die Pumpe"),
          checklistItem("Verbindungskabel", "Jumperkabel für die Verbindungen zwischen Arduino, Sensor und Relais"),
          checklistItem("USB-Kabel", "Zum Übertragen des Codes auf den Arduino und zur Stromversorgung")
        )
      ),

      div(
        className := "task-box",
        h3("🔧 Aufbau & Verkabelung"),
        p("So werden die Komponenten verbunden:"),
        div(
          className := "wiring-diagram",
          h4("Feuchtigkeitssensor:"),
          ul(
            li("VCC → Arduino 3.5V"),
            li("GND → Arduino GND"),
            li("DO → Arduino Pin (digitaler Eingang)")
          ),
          div(
            className := "info-box small-info",
            strong("Hinweis: "),
            "DO steht für Digital Output. Der Sensor gibt ein digitales Signal (HIGH/LOW) aus, ",
            "wenn die Feuchtigkeit unter einen Schwellwert fällt."
          ),
          h4("Relais-Modul:"),
          ul(
            li("VCC → Arduino 5V"),
            li("GND → Arduino GND"),
            li("IN → Arduino Pin (digitaler Eingang)")
          ),
          h4("Pumpe:"),
          ul(
            li("Pumpe + → Relais NO (Normally Open)"),
            li("Pumpe - → Netzteil -"),
            li("Netzteil + → Relais COM (Common)")
          ),
          div(
            className := "info-box small-info",
            strong("Wichtig: "),
            "Das Relais trennt die Pumpe vom Arduino-Stromkreis. ",
            "Niemals die Pumpe direkt am Arduino anschließen!"
          )
        ),
        div(
          className := "info-box",
          strong("Warum ein Relais? "),
          "Das Arduino kann nur kleine Ströme direkt schalten, aber die Pumpe braucht mehr Strom. ",
          "Der Arduino steuert das Relais und das Relais schaltet die Pumpe an/aus."
        )
      )
    )
  }

  def checklistItem(name: String, description: String): HtmlElement = {
    div(
      className := "checklist-item",
      input(typ := "checkbox"),
      div(
        className := "checklist-content",
        strong(name),
        br(),
        span(className := "description", description)
      )
    )
  }
}

// ========================================
// TASK 2: MOISTURE SENSOR
// ========================================
object Task2_MoistureSensor {
  // State für diese Aufgabe
  val advancedCodeState: Var[String] = Var(
    """// Sensor-Wert auslesen (HIGH = trocken, LOW = nass)
      |int sensorValue = digitalRead(MOISTURE_PIN);
      |
      |if (sensorValue == HIGH) {
      |  Serial.println("Boden ist TROCKEN!");
      |} else {
      |  Serial.println("Boden ist FEUCHT");
      |}
      |
      |delay(____);  // TODO: Wie lange warten? (z.B. 2000 = 2 Sekunden)""".stripMargin
  )

  val snippets = List(
    CodeSnippet(1, "int sensorValue = digitalRead(MOISTURE_PIN);"),
    CodeSnippet(2, "if (sensorValue == HIGH) {"),
    CodeSnippet(3, "  Serial.println(\"Boden ist TROCKEN!\");"),
    CodeSnippet(4, "} else {"),
    CodeSnippet(5, "  Serial.println(\"Boden ist FEUCHT\");"),
    CodeSnippet(6, "}"),
    CodeSnippet(7, "delay(2000);")
  )

  val sourceSnippets: Var[List[CodeSnippet]] = Var(snippets)
  val targetSnippets: Var[List[CodeSnippet]] = Var(List.empty)
  val draggingSnippet: Var[Option[CodeSnippet]] = Var(None)
  val targetHoverIndex: Var[Option[Int]] = Var(None)

  def render(modeSignal: Signal[Boolean]): HtmlElement = {
    div(
      h1("Feuchtigkeit im Boden messen"),
      div(
        className := "task-box",
        h3("Lernziel"),
        p("Verstehen, wie digitale Sensoren im Arduino ausgelesen werden."),
        h3("Aufgabe"),
        p("Der Feuchtigkeitssensor hat einen digitalen Ausgang (DO):"),
        ul(
          li(strong("HIGH:"), " Boden ist trocken (Sensor erkennt keine Feuchtigkeit)"),
          li(strong("LOW:"), " Boden ist feucht (Sensor erkennt Feuchtigkeit)")
        ),
        p("Lest den Sensor aus und gebt auf dem Serial Monitor aus, ob der Boden trocken oder feucht ist."),
        div(
          className := "info-box",
          strong("Hinweis: "),
          "Mit ", code("digitalRead(PIN)"), " liest man digitale Werte (HIGH oder LOW). ",
          "Mit einer ", code("if-else"), " Bedingung kann man darauf reagieren."
        )
      ),

      // Mode Toggle
      div(
        className := "controls",
        label(
          className := "switch",
          input(
            typ := "checkbox",
            controlled(
              checked <-- modeSignal,
              onInput.mapToChecked --> PlantWorkshopApp.isAdvancedMode
            )
          ),
          span(className := "slider")
        ),
        span(
          className := "mode-text",
          child.text <-- modeSignal.map(if (_) "Modus: Fortgeschritten (Code)" else "Modus: Anfänger (Puzzle)")
        )
      ),

      // Workspace
      div(
        className := "workspace",
        child <-- modeSignal.map {
          case true => advancedView()
          case false => beginnerView()
        }
      )
    )
  }

  def beginnerView(): HtmlElement = {
    DragAndDropHelper.createDndArea(
      snippets, sourceSnippets, targetSnippets,
      draggingSnippet, targetHoverIndex,
      "Setze die Code-Bausteine in die richtige Reihenfolge:"
    )
  }

  def advancedView(): HtmlElement = {
    CodeEditorHelper.createCodeEditor(
      advancedCodeState,
      "Vervollständige den Code (ersetze die _____ Lücken):",
      code => {
        val hasDigitalRead = code.contains("digitalRead")
        val hasIfStatement = code.contains("if")
        val hasDelay = code.contains("delay") && !code.contains("____")
        val hasSerial = code.contains("Serial.println")

        if (hasDigitalRead && hasIfStatement && hasDelay && hasSerial) {
          "✅ Sehr gut! Du hast alle wichtigen Teile:\n- Sensor auslesen (digitalRead)\n- Bedingung prüfen (if-else)\n- Ausgabe auf Serial Monitor\n- Wartezeit eingefügt"
        } else {
          "⚠️ Noch nicht ganz:\n" +
          (if (!hasDigitalRead) "- digitalRead() fehlt\n" else "") +
          (if (!hasIfStatement) "- if-else Bedingung fehlt\n" else "") +
          (if (!hasSerial) "- Serial.println() fehlt\n" else "") +
          (if (!hasDelay) "- delay() Wert eintragen\n" else "")
        }
      }
    )
  }
}

// ========================================
// TASK 3: PUMP CONTROL
// ========================================
object Task3_PumpControl {
  val advancedCodeState: Var[String] = Var(
    """void loop() {
      |  // 1. Pumpe einschalten
      |
      |
      |  // 2. 1 Sekunde warten
      |
      |
      |  // 3. Pumpe ausschalten
      |
      |
      |  // 4. Lange Pause
      |
      |}""".stripMargin
  )

  val snippets = List(
    CodeSnippet(1, "digitalWrite(PUMP_PIN, HIGH);"),
    CodeSnippet(2, "delay(1000);"),
    CodeSnippet(3, "digitalWrite(PUMP_PIN, LOW);"),
    CodeSnippet(4, "delay(60000);")
  )

  val sourceSnippets: Var[List[CodeSnippet]] = Var(snippets)
  val targetSnippets: Var[List[CodeSnippet]] = Var(List.empty)
  val draggingSnippet: Var[Option[CodeSnippet]] = Var(None)
  val targetHoverIndex: Var[Option[Int]] = Var(None)

  def render(modeSignal: Signal[Boolean]): HtmlElement = {
    div(
      h1("Pumpe steuern"),
      div(
        className := "task-box",
        h3("Lernziel"),
        p("Digitale Ausgänge schalten (HIGH/LOW) und verstehen, warum ein Relais nötig ist."),
        h3("Aufgabe"),
        p("Schreibt ein Programm, das die Pumpe für genau 1 Sekunde einschaltet."),
        p("Danach soll die Pumpe wieder ausgehen und das Programm soll 60 Sekunden warten."),
        div(
          className := "info-box",
          strong("Hinweis: "),
          "Die Arduino ", code("loop()"), " wiederholt sich unendlich oft. ",
          "Ziel ist also, nur einmal den richtigen Ablauf zu programmieren."
        )
      ),

      // Mode Toggle
      div(
        className := "controls",
        label(
          className := "switch",
          input(
            typ := "checkbox",
            controlled(
              checked <-- modeSignal,
              onInput.mapToChecked --> PlantWorkshopApp.isAdvancedMode
            )
          ),
          span(className := "slider")
        ),
        span(
          className := "mode-text",
          child.text <-- modeSignal.map(if (_) "Modus: Fortgeschritten (Code)" else "Modus: Anfänger (Puzzle)")
        )
      ),

      div(
        className := "workspace",
        child <-- modeSignal.map {
          case true => advancedView()
          case false => beginnerView()
        }
      )
    )
  }

  def beginnerView(): HtmlElement = {
    DragAndDropHelper.createDndArea(
      snippets, sourceSnippets, targetSnippets,
      draggingSnippet, targetHoverIndex,
      "Setze die Bausteine in die richtige Reihenfolge:"
    )
  }

  def advancedView(): HtmlElement = {
    CodeEditorHelper.createCodeEditor(
      advancedCodeState,
      "Schreibt den Code für die Pumpensteuerung:",
      code => {
        val hasOn = code.contains("digitalWrite") && code.contains("HIGH")
        val hasOff = code.contains("digitalWrite") && code.contains("LOW")
        val hasShortDelay = code.contains("delay(1000)")
        val hasLongDelay = code.contains("delay(60000)")

        if (hasOn && hasOff && hasShortDelay && hasLongDelay) {
          "✅ Perfekt! Die Pumpe wird richtig gesteuert!"
        } else {
          "⚠️ Code sieht noch nicht vollständig aus. Prüfe:\n" +
          (if (!hasOn) "- Pumpe einschalten (HIGH)\n" else "") +
          (if (!hasShortDelay) "- 1 Sekunde warten\n" else "") +
          (if (!hasOff) "- Pumpe ausschalten (LOW)\n" else "") +
          (if (!hasLongDelay) "- 60 Sekunden Pause\n" else "")
        }
      }
    )
  }
}

// ========================================
// TASK 4: COMBINED SYSTEM
// ========================================
object Task4_Combined {
  val advancedCodeState: Var[String] = Var(
    """void loop() {
      |  // 1. Feuchtigkeit prüfen
      |  int sensorValue = digitalRead(MOISTURE_PIN);
      |
      |  // 2. Entscheiden: Gießen oder nicht?
      |  if (sensorValue == ____) {  // TODO: HIGH oder LOW? (HIGH = trocken)
      |    // Zu trocken! Gießen!
      |    Serial.println("Boden trocken - Bewässerung startet...");
      |    
      |    digitalWrite(PUMP_PIN, HIGH);
      |    delay(____);  // TODO: Wie lange gießen? (z.B. 2000)
      |    digitalWrite(PUMP_PIN, LOW);
      |    
      |    Serial.println("Bewässerung abgeschlossen!");
      |  } else {
      |    Serial.println("Boden feucht - keine Bewässerung nötig");
      |  }
      |
      |  // 3. Warten bis zur nächsten Messung
      |  delay(10000);  // 10 Sekunden (zum Testen)
      |}""".stripMargin
  )

  val snippets = List(
    CodeSnippet(1, "int sensorValue = digitalRead(MOISTURE_PIN);"),
    CodeSnippet(2, "if (sensorValue == HIGH) {"),
    CodeSnippet(3, "  Serial.println(\"Boden trocken - starte Pumpe...\");"),
    CodeSnippet(4, "  digitalWrite(PUMP_PIN, HIGH);"),
    CodeSnippet(5, "  delay(2000);"),
    CodeSnippet(6, "  digitalWrite(PUMP_PIN, LOW);"),
    CodeSnippet(7, "} else {"),
    CodeSnippet(8, "  Serial.println(\"Boden feucht - OK\");"),
    CodeSnippet(9, "}"),
    CodeSnippet(10, "delay(10000);")
  )

  val sourceSnippets: Var[List[CodeSnippet]] = Var(snippets)
  val targetSnippets: Var[List[CodeSnippet]] = Var(List.empty)
  val draggingSnippet: Var[Option[CodeSnippet]] = Var(None)
  val targetHoverIndex: Var[Option[Int]] = Var(None)

  def render(modeSignal: Signal[Boolean]): HtmlElement = {
    div(
      h1("Messwerte mit Pumpensteuerung verbinden"),
      div(
        className := "task-box",
        h3("Lernziel"),
        p("Die vorherigen Aufgaben verbinden und die Funktionsweise des Systems verstehen!"),
        h3("Aufgabe"),
        p("Jetzt verbinden wir die Ergebnisse der letzten Aufgaben. Schreibt ein Programm, das:"),
        ol(
          li("Den Feuchtigkeitssensor ausliest (digitalRead)"),
          li("Prüft, ob der Boden trocken ist (sensorValue == HIGH)"),
          li("Wenn ja: Pumpe für 2 Sekunden einschalten"),
          li("Wartet und dann wieder misst (z.B. alle 10 Sekunden)")
        ),
        div(
          className := "info-box",
          strong("Hinweis: "),
          "Denkt an eure Ergebnisse aus den vorherigen Aufgaben. ",
          "Nun müsst ihr alles in der richtigen Reihenfolge zusammenfügen, überlegt euch dazu wann der Sensor gelesen werden muss und in welchen Fällen die Pumpe aktiviert bzw. deaktiviert werden soll."
        )
      ),

      div(
        className := "controls",
        label(
          className := "switch",
          input(
            typ := "checkbox",
            controlled(
              checked <-- modeSignal,
              onInput.mapToChecked --> PlantWorkshopApp.isAdvancedMode
            )
          ),
          span(className := "slider")
        ),
        span(
          className := "mode-text",
          child.text <-- modeSignal.map(if (_) "Modus: Fortgeschritten (Code)" else "Modus: Anfänger (Puzzle)")
        )
      ),

      div(
        className := "workspace",
        child <-- modeSignal.map {
          case true => advancedView()
          case false => beginnerView()
        }
      )
    )
  }

  def beginnerView(): HtmlElement = {
    DragAndDropHelper.createDndArea(
      snippets, sourceSnippets, targetSnippets,
      draggingSnippet, targetHoverIndex,
      "Setze die Bausteine zur automatischen Bewässerung zusammen:"
    )
  }

  def advancedView(): HtmlElement = {
    CodeEditorHelper.createCodeEditor(
      advancedCodeState,
      "Vervollständige das Gesamtsystem:",
      code => {
        val hasMeasurement = code.contains("digitalRead")
        val hasCondition = code.contains("if") && code.contains("HIGH")
        val hasPumpControl = code.contains("digitalWrite") && code.contains("HIGH") && code.contains("LOW")
        val hasThreshold = !code.contains("____")

        if (hasMeasurement && hasCondition && hasPumpControl && hasThreshold) {
          "🎉 Hervorragend! Das System ist komplett:\n✅ Sensor auslesen (digitalRead)\n✅ Bedingung prüfen (if HIGH)\n✅ Pumpe steuern\n✅ Alle Werte eingetragen"
        } else {
          "⚠️ Noch ein paar Kleinigkeiten:\n" +
          (if (!hasMeasurement) "- Sensor-Messung mit digitalRead fehlt\n" else "") +
          (if (!hasCondition) "- if-Bedingung mit HIGH fehlt\n" else "") +
          (if (!hasPumpControl) "- Pumpensteuerung unvollständig\n" else "") +
          (if (!hasThreshold) "- Fülle die Lücken aus\n" else "")
        }
      }
    )
  }
}

// ========================================
// TASK 5: TEST
// ========================================
object Task5_Test {
  def render(): HtmlElement = {
    div(
      h1("Test & Fertigstellung"),
      div(
        className := "task-box success-box",
        h3("Geschafft!"),
        p("Jetzt ist es Zeit, euer System zu testen!"),
        h3("Test-Checkliste:"),
        div(
          className := "test-checklist",
          checklistItem("Sensor in trockene Erde halten - zeigt er niedrige Werte?"),
          checklistItem("Sensor in Wasser halten - zeigt er hohe Werte?"),
          checklistItem("Pumpt die Pumpe Wasser, wenn die Erde trocken ist?"),
          checklistItem("Hört die Pumpe nach kurzer Zeit wieder auf?"),
          checklistItem("Passiert nichts, wenn die Erde feucht genug ist?")
        )
      ),

      div(
        className := "task-box",
        h3("Fehlersuche-Guide"),
        div(
          className := "troubleshooting",
          h4("Problem: Pumpe läuft nicht"),
          ul(
            li("Sind Relais, Netzteil und Pumpe richtig verbunden?"),
            li("Ist das Netzteil eingesteckt?"),
            li("Leuchtet das Relais, wenn es schalten soll?")
          ),
          h4("Problem: Werte ändern sich nicht"),
          ul(
            li("Sind die Kabel am Sensor richtig? (Analoge und Digitale Pins nicht vertauschen)"),
            li("Ist der Sensor mit 5V und GND verbunden?")
          ),
          h4("Problem: Pumpe läuft ständig / gar nicht"),
          ul(
            li("Ist der Grenzwert richtig gesetzt?"),
            li("Prüft der Code wirklich die Bedingung?")
          )
        )
      ),

      div(
        className := "task-box bonus-box",
        h3("Bonus-Aufgaben (für Schnelle)"),
        p("Wenn ihr fertig seid und noch Zeit habt:"),
        ul(
          li("Lasst eine LED blinken, während gepumpt wird"),
          li("Verwendet mehrere LEDs in verschiedenen Farben für verschiedene Feuchtigkeitsstufen"),
          li("Falls ihr eigene Ideen habt, probiert sie gerne aus!")
        )
      ),

      div(
        className := "congratulations",
        h2("🌟 Herzlichen Glückwunsch!"),
        p("Ihr habt eine automatische Bewässerungsanlage gebaut und programmiert!")
      )
    )
  }

  def checklistItem(text: String): HtmlElement = {
    div(
      className := "checklist-item",
      input(typ := "checkbox"),
      span(text)
    )
  }
}

// ========================================
// HELPER: DRAG AND DROP
// ========================================
object DragAndDropHelper {
  def createDndArea(
    allSnippets: List[CodeSnippet],
    sourceSnippets: Var[List[CodeSnippet]],
    targetSnippets: Var[List[CodeSnippet]],
    draggingSnippet: Var[Option[CodeSnippet]],
    targetHoverIndex: Var[Option[Int]],
    title: String
  ): HtmlElement = {
    div(
      h4(title),
      div(
        className := "dnd-area",

        // SOURCE CONTAINER
        div(
          className := "snippet-container source",
          h5("Verfügbare Bausteine"),

          onDragOver.preventDefault --> { _ => targetHoverIndex.set(None) },

          onDrop.preventDefault --> { _ =>
            draggingSnippet.now().foreach { snippet =>
              targetSnippets.update(_.filterNot(_.id == snippet.id))
              sourceSnippets.update { list =>
                val clean = list.filterNot(_.id == snippet.id)
                (clean :+ snippet).sortBy(_.id)
              }
            }
            draggingSnippet.set(None)
            targetHoverIndex.set(None)
          },

          children <-- sourceSnippets.signal.combineWith(draggingSnippet.signal).map {
            case (list, dragging) =>
              list.filterNot(s => dragging.exists(_.id == s.id))
                .map(s => renderDraggableItem(s, draggingSnippet))
          }
        ),

        // TARGET CONTAINER
        div(
          className <-- targetHoverIndex.signal.map(opt =>
            if (opt.isDefined) "snippet-container target drag-over"
            else "snippet-container target"
          ),
          h5("Dein Programmablauf"),

          onDragOver.preventDefault --> { e =>
            val container = e.currentTarget.asInstanceOf[dom.html.Div]
            val items = container.querySelectorAll(".sortable-item")
            val mouseY = e.clientY

            var newIndex = items.length
            var found = false
            var i = 0
            while (i < items.length && !found) {
              val rect = items.item(i).asInstanceOf[dom.html.Div].getBoundingClientRect()
              val middleY = rect.top + (rect.height / 2)
              if (mouseY < middleY) { newIndex = i; found = true }
              i += 1
            }
            targetHoverIndex.set(Some(newIndex))
          },

          onDrop.preventDefault --> { _ =>
            val snippetOpt = draggingSnippet.now()
            val indexOpt = targetHoverIndex.now()

            (snippetOpt, indexOpt) match {
              case (Some(snippet), Some(idx)) =>
                sourceSnippets.update(_.filterNot(_.id == snippet.id))
                targetSnippets.update { list =>
                  val clean = list.filterNot(_.id == snippet.id)
                  val safeIdx = Math.min(idx, clean.length)
                  val (front, back) = clean.splitAt(safeIdx)
                  front ++ List(snippet) ++ back
                }
              case _ =>
            }
            draggingSnippet.set(None)
            targetHoverIndex.set(None)
          },

          children <-- targetSnippets.signal
            .combineWith(draggingSnippet.signal, targetHoverIndex.signal)
            .map { case (snippets, dragging, hoverIdx) =>
              val visible = snippets.filterNot(s => dragging.exists(_.id == s.id))
              val elements = visible.map(s => renderDraggableItem(s, draggingSnippet))
              hoverIdx match {
                case Some(idx) =>
                  val safe = Math.min(idx, elements.length)
                  val (f, b) = elements.splitAt(safe)
                  f ++ List(div(className := "drop-placeholder")) ++ b
                case None => elements
              }
            }
        )
      ),

      // Check button
      button(
        "Lösung prüfen",
        className := "btn-check",
        onClick --> { _ =>
          val current = targetSnippets.now().map(_.id)
          val correct = allSnippets.map(_.id)
          if (current == correct) {
            dom.window.alert("✅ Perfekt! Die Reihenfolge ist richtig!")
          } else {
            dom.window.alert("⚠️ Noch nicht ganz richtig. Überlege nochmal, welche Schritte in welcher Reihenfolge ausgeführt werden müssen.")
          }
        }
      )
    )
  }

  def renderDraggableItem(snippet: CodeSnippet, draggingSnippet: Var[Option[CodeSnippet]]): HtmlElement = {
    div(
      className := "code-block sortable-item",
      draggable := true,
      snippet.text,

      onDragStart --> { _ => setTimeout(0) { draggingSnippet.set(Some(snippet)) } },

      onDragEnd --> { _ =>
        draggingSnippet.set(None)
      }
    )
  }
}

// ========================================
// HELPER: CODE EDITOR
// ========================================
object CodeEditorHelper {
  def createCodeEditor(
    codeState: Var[String],
    title: String,
    validator: String => String
  ): HtmlElement = {
    var editorInstance: Option[CodeJar] = None

    div(
      h4(title),

      div(
        className := "editor-container language-cpp",

        onMountUnmountCallback(
          mount = { nodeCtx =>
            val element = nodeCtx.thisNode.ref.asInstanceOf[dom.Element]

            val highlightFn: js.Function1[dom.Element, Unit] = { el =>
              Prism.highlightElement(el)
            }

            val jar = new CodeJar(element, highlightFn)
            jar.updateCode(codeState.now())
            jar.onUpdate((code: String) => codeState.set(code))

            editorInstance = Some(jar)
          },
          unmount = { _ =>
            editorInstance.foreach(_.destroy())
            editorInstance = None
          }
        )
      ),

      button(
        "Code prüfen",
        className := "btn-check",
        onClick --> { _ =>
          val code = codeState.now()
          val result = validator(code)
          dom.window.alert(result)
        }
      )
    )
  }
}
