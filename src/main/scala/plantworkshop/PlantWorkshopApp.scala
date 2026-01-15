package plantworkshop

import com.raquo.laminar.api.L._
import org.scalajs.dom

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
  private val editorState = interactionPlugins.blockEnvironment.programming.editor.elements.EditorState.withInitExpression(
    plantworkshop.PlantWorkshopTaskBlockLibraries.task2SuggestedStartProgram().fullProgram
  )

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
        child <-- interactionPlugins.blockEnvironment.programming.editor.elements.HtmlBlockLibraryTab(
          editorState,
          plantworkshop.PlantWorkshopTaskBlockLibraries.task2LibraryPrograms,
          Var(interactionPlugins.blockEnvironment.config.BeTreeControllerConfig.libraryTreeConfig(editorState))
        ).toDomSignal
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
        child <-- interactionPlugins.blockEnvironment.programming.editor.elements.HtmlBlockLibraryTab(
          editorState,
          plantworkshop.PlantWorkshopTaskBlockLibraries.task3LibraryPrograms,
          Var(interactionPlugins.blockEnvironment.config.BeTreeControllerConfig.libraryTreeConfig(editorState))
        ).toDomSignal
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
        child <-- interactionPlugins.blockEnvironment.programming.editor.elements.HtmlBlockLibraryTab(
          editorState,
          plantworkshop.PlantWorkshopTaskBlockLibraries.task4LibraryPrograms,
          Var(interactionPlugins.blockEnvironment.config.BeTreeControllerConfig.libraryTreeConfig(editorState))
        ).toDomSignal
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
