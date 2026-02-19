package content.plantworkshop

import com.raquo.laminar.api.L.*
import content.plantworkshop
import interactionPlugins.blockEnvironment.programming.editor.elements.{EditorState, HtmlBlockLibraryTab}
import org.scalajs.dom

import scala.scalajs.js

object PlantWorkshopApp {

  // --- GLOBAL STATE ---
  val currentTask: Var[Int] = Var(0) // 0-5: Motivation, Checklist, Moisture, Pumpe, Combined, Test
  val isAdvancedMode: Var[Boolean] = Var(false)

  private var beforeUnloadInstalled: Boolean = false

  private def installReloadConfirmation(): Unit = {
    if (beforeUnloadInstalled) return
    beforeUnloadInstalled = true

    // Standard pattern: cancel the event and set returnValue to trigger confirmation dialog.
    dom.window.addEventListener(
      "beforeunload",
      (e: dom.BeforeUnloadEvent) => {
        e.preventDefault()
        e.returnValue = ""
      }
    )
  }

  /** Entry point to render the workshop into a given container */
  def render(container: dom.Element): Unit = {
    installReloadConfirmation()
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
            className <-- currentTask.signal.map { current =>
              val base = "progress-step"
              val active = if (current == idx) " active" else ""
              base + active
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
  private val checkedItems: Var[Set[String]] = Var(Set.empty)

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
          checklistItem("Drähte", "Für die Verbindung zwischen Relais, Netzteil und Pumpe"),
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
            li("+ → Arduino D2"),
            li("- → Arduino GND"),
            li("S (Signal) → Arduino A0 (analoger Eingang)")
          ),
          div(
            className := "info-box small-info",
            strong("Hinweis: "),
            "Wir schließen den Sensor an einen digitalen Pin (D2) für die Stromversorgung an, ",
            "damit wir ihn nur bei Bedarf einschalten können und so die Lebensdauer verlängern."
          ),
          h4("Relais-Modul:"),
          ul(
            li("DC+ → Arduino 5V"),
            li("DC- → Arduino GND"),
            li("IN → Arduino D8 (digitaler Eingang)")
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
            "Niemals die Pumpe direkt am Arduino anschließen! " ,
            "Zwischen Pumpe, Relais und Netzteil keine Jumperkabel verwenden!"
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
    label(
      className := "checklist-item",
      input(
        typ := "checkbox",
        controlled(
          checked <-- checkedItems.signal.map(_.contains(name)),
          onInput.mapToChecked --> { isChecked =>
            checkedItems.update { existing =>
              if (isChecked) existing + name else existing - name
            }
          }
        )
      ),
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
  private val editorState: EditorState = interactionPlugins.blockEnvironment.programming.editor.elements.EditorState.withInitExpression(
    PlantWorkshopTaskBlockLibraries.task2SuggestedStartProgram().fullProgram
  )

  def programAsCpp: String =
    editorState.treeToEdit.now().fullProgram.expressionIO.getInLanguage(
      contentmanagement.model.language.AppLanguage.Cpp,
      contentmanagement.model.language.AppLanguage.English
    )

  def render(modeSignal: Signal[Boolean]): HtmlElement = {
    div(
      h1("Feuchtigkeit im Boden messen"),
      div(
        className := "task-box",
        h3("Lernziel"),
        p("Verstehen, wie analoge Sensoren mit Arduino ausgelesen werden und wie man daraus eine Trockenheits-Logik ableitet."),
        h3("Aufgabe"),
        p("Der Feuchtigkeitssensor liefert einen analogen Messwert:"),
        ul(
          li(strong("kleiner Messwert:"), " Boden ist trocken"),
          li(strong("großer Messwert:"), " Boden ist feucht")
        ),
        p("Lest den Sensor aus und gebt den Messwert auf dem Serial Monitor aus."),
        div(
          className := "info-box",
          strong("Hinweis: "),
          "Mit ", code("analogRead(SENSOR_PIN)"), " liest man analoge Werte. ",
          "Um den Sensor zu schonen, wird er kurz über ", code("SENSOR_POWER_PIN"), " eingeschaltet."
        )
      ),

      // Workbook Block-Editor (Block View <-> C++ View)
      div(
        className := "task-editor",
        renderWorkbookEditor()
      )
    )
  }

  private def renderWorkbookEditor(): Element = {
    val blockLibraryDom: Element = div(
      cls := "be-fullscreen-panel block-library",
      h2(
        cls := "be-fullscreen-panel-label",
        "Block Library"
      ),
      div(
        cls := "be-fullscreen-panel-content",
        HtmlBlockLibraryTab(
          editorState,
          PlantWorkshopTaskBlockLibraries.task2LibraryPrograms()
        ).getDomElement()
      )
    )

    div(
      cls := "be-fullscreen-editor",
      div(
        cls := "info-box",
        strong("Hinweis: "),
        "Workbook-Editor (Block View + C++ View) mit einer Arduino-Block-Library für diese Aufgabe."
      ),
      blockLibraryDom,
      interactionPlugins.blockEnvironment.programming.editor.elements.HtmlBeProgramEditor(
        editorState,
        textLanguage = contentmanagement.model.language.AppLanguage.Cpp
      ).getDomElement(),
      interactionPlugins.blockEnvironment.programming.editor.elements.HtmlEditorConfigPanel(editorState).getDomElement()
    )
  }
}

// ========================================
// TASK 3: PUMP CONTROL
// ========================================
object Task3_PumpControl {
  private val editorState = interactionPlugins.blockEnvironment.programming.editor.elements.EditorState.withInitExpression(
    plantworkshop.PlantWorkshopTaskBlockLibraries.task3SuggestedStartProgram().fullProgram
  )

  def programAsCpp: String =
    editorState.treeToEdit.now().fullProgram.expressionIO.getInLanguage(
      contentmanagement.model.language.AppLanguage.Cpp,
      contentmanagement.model.language.AppLanguage.English
    )

  def render(modeSignal: Signal[Boolean]): HtmlElement = {
    div(
      h1("Pumpe steuern"),
      div(
        className := "task-box",
        h3("Lernziel"),
        p("Digitale Ausgänge schalten (HIGH/LOW) und das Relais korrekt ansteuern."),
        h3("Aufgabe"),
        p("Schreibt ein Programm, das die Pumpe für genau 2 Sekunden einschaltet."),
        p("Danach soll die Pumpe wieder ausgehen."),
        div(
          className := "info-box",
          strong("Hinweis: "),
          "In dieser Schaltung wird das Relais mit ", code("digitalWrite(PUMP_PIN, LOW)"), " eingeschaltet und mit ", code("HIGH"), " ausgeschaltet."
        )
      ),

      // Workbook Block-Editor (Block View <-> C++ View)
      div(
        className := "task-editor",
        renderWorkbookEditor()
      )
    )
  }

  private def renderWorkbookEditor(): Element = {
    val blockLibraryDom: Element = div(
      cls := "be-fullscreen-panel block-library",
      h2(
        cls := "be-fullscreen-panel-label",
        "Block Library"
      ),
      div(
        cls := "be-fullscreen-panel-content",
        HtmlBlockLibraryTab(
          editorState,
          PlantWorkshopTaskBlockLibraries.task3LibraryPrograms(),
        ).getDomElement()
      )
    )

    div(
      cls := "be-fullscreen-editor",
      div(
        cls := "info-box",
        strong("Hinweis: "),
        "Workbook-Editor (Block View + C++ View) mit einer Arduino-Block-Library für diese Aufgabe."
      ),
      blockLibraryDom,
      interactionPlugins.blockEnvironment.programming.editor.elements.HtmlBeProgramEditor(
        editorState,
        textLanguage = contentmanagement.model.language.AppLanguage.Cpp
      ).getDomElement(),
      interactionPlugins.blockEnvironment.programming.editor.elements.HtmlEditorConfigPanel(editorState).getDomElement()
    )
  }
}

// ========================================
// TASK 4: COMBINED SYSTEM
// ========================================
object Task4_Combined {
  private val editorState = interactionPlugins.blockEnvironment.programming.editor.elements.EditorState.withInitExpression(
    plantworkshop.PlantWorkshopTaskBlockLibraries.task4SuggestedStartProgram().fullProgram
  )

  def programAsCpp: String =
    editorState.treeToEdit.now().fullProgram.expressionIO.getInLanguage(
      contentmanagement.model.language.AppLanguage.Cpp,
      contentmanagement.model.language.AppLanguage.English
    )

  def render(modeSignal: Signal[Boolean]): HtmlElement = {
    div(
      h1("Messwerte mit Pumpensteuerung verbinden"),
      div(
        className := "task-box",
        h3("Lernziel"),
        p("Die vorherigen Aufgaben verbinden und den kompletten Ablauf umsetzen."),
        h3("Aufgabe"),
        p("Jetzt setzt ihr den kompletten Ablauf um. Das Programm soll:"),
        ol(
          li("Den Sensor kurz mit \"SENSOR_POWER_PIN\" einschalten und den Messwert per \"analogRead(SENSOR_PIN)\" auslesen"),
          li("Den Messwert auf dem Serial Monitor ausgeben (\"Analoger Wert: ...\")"),
          li("Prüfen, ob \"messwert < feuchtigkeitsGrenze\""),
          li("Wenn ja: Pumpe einschalten, 2 Sekunden gießen, wieder ausschalten"),
          li("Danach 10 Sekunden warten")
        ),
        div(
          className := "info-box",
          strong("Hinweis: "),
          "Denkt an eure Ergebnisse aus den vorherigen Aufgaben. ",
          "Nun müsst ihr alles in der richtigen Reihenfolge zusammenfügen, überlegt euch dazu wann der Sensor gelesen werden muss und in welchen Fällen die Pumpe aktiviert bzw. deaktiviert werden soll."
        )
      ),

      // Workbook Block-Editor (Block View <-> C++ View)
      div(
        className := "task-editor",
        renderWorkbookEditor()
      )
    )
  }
  private def renderWorkbookEditor(): Element = {
    val blockLibraryDom: Element = div(
      cls := "be-fullscreen-panel block-library",
      h2(
        cls := "be-fullscreen-panel-label",
        "Block Library"
      ),
      div(
        cls := "be-fullscreen-panel-content",
        HtmlBlockLibraryTab(
          editorState,
          PlantWorkshopTaskBlockLibraries.task4LibraryPrograms(),
        ).getDomElement()
      )
    )

    div(
      cls := "be-fullscreen-editor",
      div(
        cls := "info-box",
        strong("Hinweis: "),
        "Workbook-Editor (Block View + C++ View) mit einer Arduino-Block-Library für diese Aufgabe."
      ),
      blockLibraryDom,
      interactionPlugins.blockEnvironment.programming.editor.elements.HtmlBeProgramEditor(
        editorState,
        textLanguage = contentmanagement.model.language.AppLanguage.Cpp
      ).getDomElement(),
      interactionPlugins.blockEnvironment.programming.editor.elements.HtmlEditorConfigPanel(editorState).getDomElement()
    )
  }
}

// ========================================
// TASK 5: TEST
// ========================================
object Task5_Test {
  private val checkedItems: Var[Set[String]] = Var(Set.empty)

  private def indentBlock(code: String, spaces: Int = 2): String = {
    val pad = " " * spaces
    code.linesIterator.map { line =>
      if (line.trim.isEmpty) line else pad + line
    }.mkString("\n")
  }

  private def buildFinalArduinoSketch(): String = {
    val task4 = Task4_Combined.programAsCpp.trim
    val loopBody = if (task4.nonEmpty) indentBlock(task4) else "  // (Kein Code aus Modul 4 vorhanden)"

    s"""/*
       | * Girls Day - Automatische Pflanzen-Bewässerung
       | * Kompletter Arduino Sketch (aus dem Workbook generiert)
       | */
       |
       |const int SENSOR_PIN = A0;  // Analog Pin 0 zu DO
       |const int SENSOR_POWER_PIN = 2; // Pin, damit der Sensor nicht so schnell rostet
       |const int PUMP_PIN = 8; // Pin fürs relais für die pumpe
       |
       |int feuchtigkeitsGrenze = 100;
       |
       |void setup() {
       |  Serial.begin(9600);
       |
       |  pinMode(PUMP_PIN, OUTPUT);
       |  pinMode(SENSOR_POWER_PIN, OUTPUT);
       |
       |  digitalWrite(PUMP_PIN, HIGH);
       |  digitalWrite(SENSOR_POWER_PIN, LOW);
       |}
       |
       |void loop() {
       |$loopBody
       |}
       |""".stripMargin
  }

  private def downloadArduinoSketch(): Unit = {
    val code = buildFinalArduinoSketch()
    val blob = new dom.Blob(
      js.Array(code),
      dom.BlobPropertyBag(`type` = "text/plain;charset=utf-8")
    )
    val url = dom.URL.createObjectURL(blob)
    val anchor = dom.document.createElement("a").asInstanceOf[dom.html.Anchor]
    anchor.href = url
    anchor.download = "plantworkshop.ino"
    anchor.click()
    dom.URL.revokeObjectURL(url)
  }

  def render(): HtmlElement = {
    div(
      h1("Test & Fertigstellung"),
      div(
        className := "task-box success-box",
        h3("Geschafft!"),
        p("Jetzt ist es Zeit, euer System zu testen!"),
        button(
          "Arduino-Code herunterladen (.ino)",
          className := "btn-nav btn-primary",
          marginBottom := "18px",
          onClick --> (_ => downloadArduinoSketch())
        ),
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
      input(
        typ := "checkbox",
        controlled(
          checked <-- checkedItems.signal.map(_.contains(text)),
          onInput.mapToChecked --> { isChecked =>
            checkedItems.update { existing =>
              if (isChecked) existing + text else existing - text
            }
          }
        )
      ),
      span(text)
    )
  }
}
