package content.plantworkshop

import com.raquo.laminar.api.L.*
import interactionPlugins.blockEnvironment.programming.editor.elements.{EditorState, HtmlBlockLibraryTab}
import org.scalajs.dom
import util.web.DownloadHelper

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
  val currentTask: Var[Int] = Var(0) // 0-5: Motivation, Checklist, Pumpe, Moisture, Combined, Test
  val isAdvancedMode: Var[Boolean] = Var(false)

  private var beforeUnloadInstalled: Boolean = false

  private def installReloadConfirmation(): Unit = {
    if (beforeUnloadInstalled) return
    beforeUnloadInstalled = true

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
          case 2 => Task3_PumpControl.render(isAdvancedMode.signal)
          case 3 => Task2_MoistureSensor.render(isAdvancedMode.signal)
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
      "2. Pumpe steuern",
      "3. Feuchtigkeit messen",
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
          "Keine Sorge, wenn ihr noch nie programmiert habt, wir fangen ganz von vorne an! ",
          "Ihr habt die Wahl zwischen einem Anfängermodus mit Drag-and-Drop Codebausteinen und einem Fortgeschrittenenmodus, in dem ihr Codelückentext selbst ausfüllen könnt. ",
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
  private val buildSteps = (1 to 11).map(i => s"../resources/img/plantworkshop/schaltkreis/Plant%20conv%20$i.png").toList
  private val currentBuildStep: Var[Int] = Var(0)
  private val showWiringDetails = false

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
        div(
          className := "safety-warning",
          backgroundColor := "#ffe5e5",
          border := "3px solid #c62828",
          padding := "14px",
          marginBottom := "14px",
          h3("⚠️ WICHTIGE SICHERHEITSREGEL"),
          p(
            strong("Niemals Strom ohne Aufseher einschalten! "),
            "Die Stromversorgung darf nur zusammen mit einer betreuenden Person aktiviert werden."
          )
        )
      ),

      if (showWiringDetails)
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
              "Niemals die Pumpe direkt am Arduino anschließen! ",
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
      else
        emptyNode,

      div(
        className := "task-box",
        h3("Aufbauskizze / Schaltplan"),
        p("Schaut euch die Aufbauschritte an und klickt durch die Bilder:"),
        div(
          className := "wiring-diagram",
          div(
            display := "flex",
            flexDirection := "column",
            gap := "10px",
            alignItems := "center",
            div(
              display := "flex",
              gap := "10px",
              button(
                "← Zurück",
                className := "btn-nav",
                onClick --> (_ => currentBuildStep.update(i => (i - 1 + buildSteps.length) % buildSteps.length))
              ),
              span(
                fontWeight := "600",
                child.text <-- currentBuildStep.signal.map(i => s"Schritt ${i + 1} / ${buildSteps.length}")
              ),
              button(
                "Weiter →",
                className := "btn-nav",
                onClick --> (_ => currentBuildStep.update(i => (i + 1) % buildSteps.length))
              )
            ),
            img(
              src <-- currentBuildStep.signal.map(i => buildSteps(i)),
              alt <-- currentBuildStep.signal.map(i => s"Aufbau Schritt ${i + 1}"),
              maxWidth := "100%",
              width := "820px",
              border := "2px solid #d5dbe3",
              borderRadius := "8px"
            ),
            child <-- currentBuildStep.signal.map {
              case 0 =>
                div(
                  width := "820px",
                  div(
                    className := "info-box",
                    strong("Hinweis:"),
                    p(
                      "In den nächsten Schritten benötigt ihr Jumperkabel. ",
                      "Nehmt euch jetzt schon ein paar Jumperkabel und legt sie beiseite."
                    )
                  )
                )
              case 1 =>
                div(
                  width := "820px",
                  div(
                    className := "info-box",
                    strong("Hinweis:"),
                    p(
                      "Nehmt euch jetzt einen Arduino Nano, ein Relais und den Feuchtigkeitssensor. ",
                      "Diese Bauteile verbinden wir gleich miteinander."
                    )
                  )
                )
              case 2 =>
                div(
                  display := "flex",
                  gap := "12px",
                  width := "820px",
                  div(
                    className := "info-box",
                    flex := "1",
                    strong("Verkabelung:"),
                    ul(
                      li(" Sensor + __ Arduino D2"),
                      li(" Sensor - __ Arduino GND"),
                      li(" Sensor S __ Arduino A0")
                    )
                  ),
                  div(
                    className := "info-box",
                    flex := "2",
                    strong("Was macht diese Verkabelung?"),
                    p(
                      "Der Pin D2 versorgt den Sensor mit Strom, wenn wir Werte messen wollen. ",
                      "Er wird nicht permanent mit Strom versorgt, damit der Sensor länger hält. ",
                    ),
                    p(
                      "GND ist der Minuspol (Masse) der Schaltung. ",
                      "Ohne die Verbindung zu GND ist der Stromkreis nicht geschlossen und der Sensor kann keine stabilen Werte liefern."
                    ),
                    p(
                      "Über A0 liest der Arduino den analogen Messwert ein. ",
                      "Aus diesem Wert entscheidet euer Programm später, ob die Pumpe laufen soll oder nicht."
                    )
                  )
                )
              case 3 =>
                div(
                  display := "flex",
                  gap := "12px",
                  width := "820px",
                  div(
                    className := "info-box",
                    flex := "1",
                    strong("Verkabelung:"),
                    ul(
                      li(" Relais DC+ __ Arduino 5V"),
                      li(" Relais DC- __ Arduino GND"),
                      li(" Relais IN __ Arduino D8")
                    )
                  ),
                  div(
                    className := "info-box",
                    flex := "2",
                    strong("Was macht diese Verkabelung?"),
                    p(
                      "Das Relais wird mit 5V und GND vom Arduino versorgt. ",
                      "Über den Pin D8 steuert der Arduino das Relais an. "
                    ),
                    p(
                      "Das Relais funktioniert als Schalter zwischen Arduino und Pumpe. ",
                      "So kann der Arduino die Pumpe sicher ein- und ausschalten, obwohl die Pumpe mehr Strom braucht, als der Arduino liefern kann."
                    ),
                    p(
                      "Wichtig: Die Pumpe wird nicht direkt am Arduino angeschlossen, ",
                      "sondern über das Relais geschaltet."
                    )
                  )
                )
              case 4 =>
                div(
                  width := "820px",
                  div(
                    className := "info-box",
                    strong("Zwischenstand:"),
                    p(
                      "So sollte euer Aufbau bis hier aussehen. ",
                      "Vergleicht eure Verkabelung mit dem Bild und prüft kurz alle Verbindungen, bevor es weitergeht."
                    )
                  )
                )
              case 5 =>
                div(
                  width := "820px",
                  div(
                    className := "info-box",
                    strong("Hinweis:"),
                    p(
                      "Schneidet euch jetzt 3 Stücke Draht zurecht und legt sie bereit. ",
                      "Diese braucht ihr in den nächsten Schritten. Jumperkabel werden ab jetzt nicht mehr verwendet."
                    )
                  )
                )
              case 6 =>
                div(
                  width := "820px",
                  div(
                    className := "info-box",
                    strong("Hinweis:"),
                    p(
                      "Nehmt euch jetzt das Relais, die Pumpe und den Adapter. ",
                      "Den Adapter verbinden wir später mit dem Netzteil."
                    ),
                    p(
                      "Das Relais ist dasselbe wie vorher, ",
                      "aber wir arbeiten jetzt mit den Anschlüssen auf der anderen Seite."
                    )
                  )
                )
              case 7 =>
                div(
                  display := "flex",
                  gap := "12px",
                  width := "820px",
                  div(
                    className := "info-box",
                    flex := "1",
                    strong("Verkabelung:"),
                    ul(
                      li(" Pumpe + __ Relais NO (Normally Open)"),
                      li(" Pumpe - __ Adapter -"),
                      li(" Adapter + __ Relais COM (Common)")
                    )
                  ),
                  div(
                    className := "info-box",
                    flex := "2",
                    strong("Was macht diese Verkabelung?"),
                    p(
                      "Hier verbindet ihr Relais, Pumpe und Adapter mit den vorbereiteten Drähten. ",
                      "Das Relais schaltet später den Strom zur Pumpe."
                    ),
                    p(
                      "Der Pluspol vom Adapter geht über COM und NO zur Pumpe, ",
                      "der Minuspol vom Adapter wird direkt mit der Pumpe verbunden."
                    ),
                    p(
                      "Wichtig: Für diese Verbindung keine Jumperkabel verwenden, ",
                      "sondern die zugeschnittenen Drähte."
                    )
                  )
                )
              case 8 =>
                div(
                  width := "820px",
                  div(
                    className := "info-box",
                    strong("Hinweis:"),
                    p(
                      "Verbindet jetzt den Adapter mit dem Netzteil. ",
                      "Achtet dabei darauf, dass Plus und Minus richtig angeschlossen sind."
                    )
                  )
                )
              case 9 =>
                div(
                  width := "820px",
                  div(
                    className := "info-box",
                    strong("Hinweis:"),
                    p(
                      "Nehmt von der großen PVC-Schlauchrolle zwei Stücke mit jeweils 50 cm Länge. ",
                      "Diese beiden Schlauchstücke werden gleich für die Pumpe benötigt."
                    )
                  )
                )
              case 10 =>
                div(
                  width := "820px",
                  div(
                    className := "info-box",
                    strong("Hinweis:"),
                    p(
                      "Verbindet jetzt die beiden Schlauchstücke mit der Pumpe. ",
                      "Ein Schlauchstück kommt an den Eingang und das andere an den Ausgang der Pumpe."
                    )
                  )
                )
              case _ => emptyNode
            }
          )
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
// TASK 3: MOISTURE SENSOR
// ========================================
object Task2_MoistureSensor {
  // State für diese Aufgabe
  val advancedCodeState: Var[String] = Var(
    """// Lege den Grenzwert fest, ab dem der Boden als trocken gilt
      |int feuchtigkeitsGrenze = TODO_WERT;       
      |
      |// Sensor nur kurz aktivieren, messen, wieder ausschalten
      |digitalWrite(SENSOR_POWER_PIN, TODO_HIGH_LOW);     
      |delay(TODO_DELAY_MS);                        
      |int messwert = analogRead(TODO_SENSOR_PIN);  
      |digitalWrite(SENSOR_POWER_PIN, TODO_HIGH_LOW);    
      |
      |// Gib erst eine Beschriftung, dann den Messwert aus
      |Serial.print(TODO_TEXT);                    
      |Serial.println(TODO_WERT_AUSGABE);           
      |
      |// Entscheide anhand des Grenzwerts zwischen trocken und feucht
      |if (TODO_BEDINGUNG) {                        
      |  Serial.println(TODO_TEXT_TROCKEN);         
      |} else {
      |  Serial.println(TODO_TEXT_FEUCHT);          
        |}""".stripMargin
  )

  val snippets = List(
    CodeSnippet(1, "int feuchtigkeitsGrenze = 100;"),
    CodeSnippet(2, "digitalWrite(SENSOR_POWER_PIN, HIGH);"),
    CodeSnippet(3, "delay(10);"),
    CodeSnippet(4, "int messwert = analogRead(SENSOR_PIN);"),
    CodeSnippet(5, "digitalWrite(SENSOR_POWER_PIN, LOW);"),
    CodeSnippet(6, "if (messwert < feuchtigkeitsGrenze) {"),
    CodeSnippet(7, "  Serial.println(\"Boden ist TROCKEN!\");"),
    CodeSnippet(8, "} else {"),
    CodeSnippet(9, "  Serial.println(\"Boden ist FEUCHT\");"),
    CodeSnippet(10, "}")
  )

  val sourceSnippets: Var[List[CodeSnippet]] = Var(
    List(
      snippets(5),
      snippets(1),
      snippets(7),
      snippets(0),
      snippets(8),
      snippets(9),
      snippets(4),
      snippets(3),
      snippets(2),
      snippets(6)
    )
  )
  val targetSnippets: Var[List[CodeSnippet]] = Var(List.empty)
  val draggingSnippet: Var[Option[CodeSnippet]] = Var(None)
  val targetHoverIndex: Var[Option[Int]] = Var(None)

  def blockProgramIfComplete: Option[String] = {
    val current = targetSnippets.now()
    if (current.length == snippets.length) Some(current.map(_.text).mkString("\n").trim)
    else None
  }

  def render(modeSignal: Signal[Boolean]): HtmlElement = {
    div(
      h1("Feuchtigkeit im Boden messen"),
      div(
        className := "task-box",
        h3("Lernziel"),
        p("Ihr könnt einen analogen Sensor mit Arduino auslesen und selbst entscheiden, ab wann der Boden als trocken gilt."),
        h3("Aufgabe"),
        p("Der Feuchtigkeitssensor liefert einen analogen Messwert:"),
        ul(
          li(strong("kleiner Messwert:"), " Boden ist trocken"),
          li(strong("großer Messwert:"), " Boden ist feucht")
        ),
        p("Lest den Sensor aus und gebt das Ergebnis der Messung auf dem Serial Monitor aus."),
        div(
          className := "info-box",
          strong("Hinweis: "),
          "Mit ", code("analogRead(SENSOR_PIN)"), " liest man analoge Werte. ",
          "Um den Sensor zu schonen, wird er kurz über ", code("SENSOR_POWER_PIN"), " ein- und anschließend wieder ausgeschaltet. Der Sensor wird mit HIGH aktiviert und mit LOW deaktiviert. ",
          "Mit ", code("delay(10);"), " wartet ihr kurz, bis der Sensor stabil misst. ",
          "Setzt außerdem einen Grenzwert, z. B. ", code("int feuchtigkeitsGrenze = 100;"), "."
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
      "Setze die Code-Bausteine in die richtige Reihenfolge:",
      orderConstraints = List(
        2 -> 3,
        3 -> 4,
        4 -> 5,
        1 -> 6,
        5 -> 6,
        6 -> 7,
        7 -> 8,
        8 -> 9,
        9 -> 10
      )
    )
  }

  def advancedView(): HtmlElement = {
    CodeEditorHelper.createCodeEditor(
      advancedCodeState,
      "Vervollständige den Code:",
      code => {
        val hasThreshold = code.contains("feuchtigkeitsGrenze")
        val hasDelay = code.contains("delay(")
        val hasAnalogRead = code.contains("analogRead")
        val hasSensorPower = code.contains("digitalWrite(SENSOR_POWER_PIN, HIGH)") && code.contains("digitalWrite(SENSOR_POWER_PIN, LOW)")
        val hasIfStatement = code.contains("if")
        val hasSerial = code.contains("Serial.println")

        if (hasThreshold && hasDelay && hasAnalogRead && hasSensorPower && hasIfStatement && hasSerial) {
          "✅ Sehr gut! Du hast alle wichtigen Teile:\n- Grenzwert als Variable gesetzt\n- Sensor einschalten, kurz warten und analog auslesen\n- Bedingung prüfen (if-else)\n- Ausgabe auf Serial Monitor"
        } else {
          "⚠️ Noch nicht ganz:\n" +
          (if (!hasThreshold) "- feuchtigkeitsGrenze als Variable setzen\n" else "") +
          (if (!hasDelay) "- delay nach dem Einschalten fehlt\n" else "") +
          (if (!hasAnalogRead) "- analogRead() fehlt\n" else "") +
          (if (!hasSensorPower) "- Sensor ein-/ausschalten über SENSOR_POWER_PIN fehlt\n" else "") +
          (if (!hasIfStatement) "- if-else Bedingung fehlt\n" else "") +
          (if (!hasSerial) "- Serial.println() fehlt\n" else "")
        }
      }
    )
  }
}

// ========================================
// TASK 2: PUMP CONTROL
// ========================================
object Task3_PumpControl {
  val advancedCodeState: Var[String] = Var(
    """void loop() {
      |  // Schalte die Pumpe ein (Relais-Logik beachten)
      |  digitalWrite(TODO_PIN, TODO_HIGH_LOW);     
      |
      |  // Lass sie für die gewünschte Zeit laufen
      |  delay(TODO_GIESS_DAUER_MS);                
      |
      |  // Schalte die Pumpe wieder aus
      |}""".stripMargin
  )

  val snippets = List(
    CodeSnippet(1, "digitalWrite(PUMP_PIN, LOW);"),
    CodeSnippet(2, "delay(2000);"),
    CodeSnippet(3, "digitalWrite(PUMP_PIN, HIGH);")
  )

  val sourceSnippets: Var[List[CodeSnippet]] = Var(
    List(snippets(1), snippets(2), snippets(0))
  )
  val targetSnippets: Var[List[CodeSnippet]] = Var(List.empty)
  val draggingSnippet: Var[Option[CodeSnippet]] = Var(None)
  val targetHoverIndex: Var[Option[Int]] = Var(None)

  def blockProgramIfComplete: Option[String] = {
    val current = targetSnippets.now()
    if (current.length == snippets.length) Some(current.map(_.text).mkString("\n").trim)
    else None
  }

  def render(modeSignal: Signal[Boolean]): HtmlElement = {
    div(
      h1("Pumpe steuern"),
      div(
        className := "task-box",
        h3("Lernziel"),
        p("Ihr könnt digitale Ausgänge mit HIGH/LOW schalten und das Relais so steuern, dass die Pumpe sicher ein- und ausgeschaltet wird."),
        h3("Aufgabe"),
        p("Schreibt ein Programm, das die Pumpe für genau 2 Sekunden einschaltet."),
        p("Danach soll die Pumpe wieder ausgehen."),
        div(
          className := "info-box",
          strong("Hinweis: "),
          "In dieser Schaltung wird das Relais mit ", code("digitalWrite(PUMP_PIN, LOW)"), " eingeschaltet und mit ", code("HIGH"), " ausgeschaltet."
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
        val hasOn = code.contains("digitalWrite(PUMP_PIN, LOW)")
        val hasOff = code.contains("digitalWrite(PUMP_PIN, HIGH)")
        val hasShortDelay = code.contains("delay(2000)")

        if (hasOn && hasOff && hasShortDelay) {
          "✅ Perfekt! Die Pumpe wird richtig gesteuert!"
        } else {
          "⚠️ Code sieht noch nicht vollständig aus. Prüfe:\n" +
          (if (!hasOn) "- Pumpe einschalten (LOW)\n" else "") +
          (if (!hasShortDelay) "- 2 Sekunden warten\n" else "") +
          (if (!hasOff) "- Pumpe ausschalten (HIGH)\n" else "")
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
    """// Definiere den Grenzwert einmal außerhalb von loop()
      |int feuchtigkeitsGrenze = TODO_WERT;        
      |
      |void loop() {
      |  // 1) Messen
      |  digitalWrite(SENSOR_POWER_PIN, TODO_HIGH_LOW);       
      |  delay(TODO_STABILISIERUNG_MS);                 
      |  int messwert = analogRead(TODO_SENSOR_PIN);    
      |  digitalWrite(SENSOR_POWER_PIN, TODO_HIGH_LOW);      
      |  Serial.print(TODO_LABEL);                      
      |  Serial.println(TODO_AUSGABE_WERT);             
      |
      |  // 2) Entscheiden und handeln
      |  if (TODO_BEDINGUNG) {                          
      |    Serial.println(TODO_STARTTEXT);              
      |
      |    digitalWrite(TODO_PUMP_PIN, TODO_HIGH_LOW);  
      |    delay(TODO_GIESS_DAUER_MS);                  
      |    digitalWrite(TODO_PUMP_PIN, TODO_HIGH_LOW); 
      |
      |    Serial.println(TODO_ENDTEXT);                
      |  } else {
      |    Serial.println(TODO_FEUCHT_TEXT);            
      |  }
      |
      |  // 3) Wartezeit bis zur nächsten Messung
      |  delay(TODO_WARTEZEIT_MS);                      
      |}""".stripMargin
  )

  val snippets = List(
    CodeSnippet(1, "int feuchtigkeitsGrenze = 100;"),
    CodeSnippet(2, "digitalWrite(SENSOR_POWER_PIN, HIGH);"),
    CodeSnippet(3, "delay(10);"),
    CodeSnippet(4, "int messwert = analogRead(SENSOR_PIN);"),
    CodeSnippet(5, "digitalWrite(SENSOR_POWER_PIN, LOW);"),
    CodeSnippet(6, "Serial.print(\"Analoger Wert: \" );"),
    CodeSnippet(7, "Serial.println(messwert);"),
    CodeSnippet(8, "if (messwert < feuchtigkeitsGrenze) {"),
    CodeSnippet(9, "  digitalWrite(PUMP_PIN, LOW);"),
    CodeSnippet(10, "  delay(2000);"),
    CodeSnippet(11, "  digitalWrite(PUMP_PIN, HIGH);"),
    CodeSnippet(12, "} else {"),
    CodeSnippet(13, "  Serial.println(\"Boden feucht - keine Bewässerung nötig\");"),
    CodeSnippet(14, "}"),
    CodeSnippet(15, "delay(10000);")
  )

  val sourceSnippets: Var[List[CodeSnippet]] = Var(
    List(
      snippets(7),
      snippets(2),
      snippets(12),
      snippets(4),
      snippets(8),
      snippets(9),
      snippets(14),
      snippets(11),
      snippets(5),
      snippets(3),
      snippets(13),
      snippets(10),
      snippets(6),
      snippets(1),
      snippets(0)
    )
  )
  val targetSnippets: Var[List[CodeSnippet]] = Var(List.empty)
  val draggingSnippet: Var[Option[CodeSnippet]] = Var(None)
  val targetHoverIndex: Var[Option[Int]] = Var(None)

  def blockProgramIfComplete: Option[String] = {
    val current = targetSnippets.now()
    if (current.length == snippets.length) Some(current.map(_.text).mkString("\n").trim)
    else None
  }

  def programAsCpp: String = {
    val dndProgram = blockProgramIfComplete.getOrElse("")
    val advancedProgram = advancedCodeState.now().trim
    if (dndProgram.nonEmpty) dndProgram else advancedProgram
  }

  def render(modeSignal: Signal[Boolean]): HtmlElement = {
    div(
      h1("Messwerte mit Pumpensteuerung verbinden"),
      div(
        className := "task-box",
        h3("Lernziel"),
        p("Ihr könnt alle Schritte zu einem vollständigen Bewässerungsprogramm verbinden: messen, entscheiden und gezielt gießen."),
        h3("Aufgabe"),
        p("Jetzt setzt ihr den kompletten Ablauf um. Das Programm soll:"),
        ol(
          li("Den Sensor kurz mit \"SENSOR_POWER_PIN\" einschalten und den Messwert per \"analogRead(SENSOR_PIN)\" auslesen"),
          li("Den Messwert auf dem Serial Monitor ausgeben (\"Analoger Wert: ...\")"),
          li("Prüfen, ob \"messwert < feuchtigkeitsGrenze\""),
          li("Wenn ja: Pumpe einschalten, 2 Sekunden gießen, wieder ausschalten"),
          li("Danach 10 Sekunden warten")
        ),
        p(strong("Wichtig: "), "Das gesamte Programm läuft in einer Endlosschleife."),
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
      "Setze die Bausteine zur automatischen Bewässerung zusammen:",
      orderConstraints = List(
        2 -> 3,
        3 -> 4,
        4 -> 5,
        1 -> 8,
        5 -> 6,
        5 -> 7,
        6 -> 8,
        7 -> 8,
        8 -> 9,
        9 -> 10,
        10 -> 11,
        11 -> 12,
        12 -> 13,
        13 -> 14,
        14 -> 15
      )
    )
  }

  def advancedView(): HtmlElement = {
    CodeEditorHelper.createCodeEditor(
      advancedCodeState,
      "Vervollständige das Gesamtsystem:",
      code => {
        val hasThresholdVar = code.contains("feuchtigkeitsGrenze")
        val hasMeasurement = code.contains("analogRead")
        val hasSensorPower = code.contains("digitalWrite(SENSOR_POWER_PIN, HIGH)") && code.contains("digitalWrite(SENSOR_POWER_PIN, LOW)")
        val hasCondition = code.contains("if") && (
          code.contains("messwert < feuchtigkeitsGrenze") ||
          code.contains("messwert > feuchtigkeitsGrenze")
        )
        val hasPumpControl = code.contains("digitalWrite(PUMP_PIN, LOW)") && code.contains("digitalWrite(PUMP_PIN, HIGH)")
        val hasSerial = code.contains("Serial.print") || code.contains("Serial.println")
        val hasLoopDelay = code.contains("delay(10000)")

        if (hasThresholdVar && hasMeasurement && hasSensorPower && hasCondition && hasPumpControl && hasSerial && hasLoopDelay) {
          "🎉 Hervorragend! Das System ist komplett:\n✅ Grenzwert als Variable gesetzt\n✅ Sensor einschalten und analog auslesen\n✅ Bedingung mit Grenzwert prüfen\n✅ Pumpe korrekt steuern\n✅ Messwert ausgeben und erneut messen"
        } else {
          "⚠️ Noch ein paar Kleinigkeiten:\n" +
          (if (!hasThresholdVar) "- feuchtigkeitsGrenze als Variable setzen\n" else "") +
          (if (!hasMeasurement) "- Sensor-Messung mit analogRead fehlt\n" else "") +
          (if (!hasSensorPower) "- Sensor über SENSOR_POWER_PIN ein-/ausschalten\n" else "") +
          (if (!hasCondition) "- if-Bedingung mit messwert < feuchtigkeitsGrenze oder messwert > feuchtigkeitsGrenze fehlt\n" else "") +
          (if (!hasPumpControl) "- Pumpensteuerung (LOW/HIGH) unvollständig\n" else "") +
          (if (!hasSerial) "- Ausgabe des Messwerts fehlt\n" else "") +
          (if (!hasLoopDelay) "- Wartezeit delay(10000) fehlt\n" else "")
        }
      }
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

  private def buildLoopBodyFromTask2AndTask3Blocks(): Option[String] = {
    val task2Complete = Task2_MoistureSensor.targetSnippets.now().length == Task2_MoistureSensor.snippets.length
    val task3Complete = Task3_PumpControl.targetSnippets.now().length == Task3_PumpControl.snippets.length

    if (!task2Complete || !task3Complete) None
    else {
      val pumpBlock = Task3_PumpControl.targetSnippets.now().map(_.text).mkString("\n").trim
      val pumpBlockIndented = indentBlock(pumpBlock, 2)

      Some(
        s"""digitalWrite(SENSOR_POWER_PIN, HIGH);
           |delay(10);
           |int messwert = analogRead(SENSOR_PIN);
           |digitalWrite(SENSOR_POWER_PIN, LOW);
           |
           |Serial.print(\"Analoger Wert: \" );
           |Serial.println(messwert);
           |
           |if (messwert < feuchtigkeitsGrenze) {
           |  Serial.println(\"Boden ist TROCKEN!\");
           |$pumpBlockIndented
           |} else {
           |  Serial.println(\"Boden ist FEUCHT\");
           |}
           |
           |delay(10000);""".stripMargin
      )
    }
  }

  private def buildFinalArduinoSketch(): String = {
    val loopBodyRaw = Task4_Combined.blockProgramIfComplete
      .orElse(buildLoopBodyFromTask2AndTask3Blocks())
      .getOrElse(Task4_Combined.programAsCpp.trim)

    val loopBody =
      if (loopBodyRaw.nonEmpty) indentBlock(loopBodyRaw)
      else "  // (Kein Code aus Modul 4 oder den Block-Aufgaben vorhanden)"

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
    DownloadHelper.downloadFile("plantworkshop.ino", code)
  }

  def render(): HtmlElement = {
    div(
      h1("Test & Fertigstellung"),

      div(
        className := "task-box",
        h3("Schritt-für-Schritt: .ino auf den Arduino Nano laden"),
        div(
          className := "safety-warning",
          backgroundColor := "#ffe5e5",
          border := "3px solid #c62828",
          padding := "14px",
          marginBottom := "14px",
          h3("⚠️ STOPP VOR DEM HOCHLADEN"),
          p(
            strong("Niemals Strom ohne Aufseher einschalten! "),
            "Bevor ihr hochladet, testet oder die Pumpe startet, muss eine Aufsichtsperson dabei sein."
          )
        ),
        ol(
          li("Klickt auf den Button ", strong("Arduino-Code herunterladen (.ino)"), " und speichert die Datei ", code("plantworkshop.ino"), "."),
          li("Öffnet die Datei in der Arduino IDE (Doppelklick oder über ", code("Datei > Öffnen"), ")."),
          li("Verbindet den Arduino Nano per USB mit dem Computer."),
          li("Wählt in der Arduino IDE unter ", code("Werkzeuge > Board"), " den Eintrag ", strong("Arduino Nano"), "."),
          li("Wählt unter ", code("Werkzeuge > Port"), " den passenden COM/USB-Port eures Nano aus."),
          li("Klickt auf ", strong("Hochladen"), " (Pfeil-Symbol) und wartet bis ", code("Hochladen abgeschlossen"), " angezeigt wird."),
          li("Öffnet danach den Serial Monitor (", code("Werkzeuge > Serieller Monitor"), "), um die Messwerte zu sehen und den Aufbau zu testen.")
        )
      ),

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
          checklistItem("Feuchten Finger auf Sensor halten - zeigt er hohe Werte?"),
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
            li("Klickt das Relais, wenn es schalten soll?")
          ),
          h4("Problem: Werte ändern sich nicht"),
          ul(
            li("Sind die Kabel am Sensor richtig? (Analoge und Digitale Pins nicht vertauschen)"),
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
    label(
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

// ========================================
// HELPER: DRAG AND DROP
// ========================================
object DragAndDropHelper {
  private def snippetExplanation(snippetText: String): String = {
    val normalized = snippetText.trim

    if (normalized.contains("digitalWrite(SENSOR_POWER_PIN, HIGH)")) "Setze SENSOR_POWER_PIN auf HIGH (Sensor an)."
    else if (normalized.contains("digitalWrite(SENSOR_POWER_PIN, LOW)")) "Setze SENSOR_POWER_PIN auf LOW (Sensor aus)."
    else if (normalized.contains("analogRead(SENSOR_PIN)")) "Lies den analogen Wert von SENSOR_PIN und speichere ihn als messwert."
    else if (normalized.contains("messwert < feuchtigkeitsGrenze")) "Prüfe: Ist messwert kleiner als feuchtigkeitsGrenze?"
    else if (normalized.contains("digitalWrite(PUMP_PIN, LOW)")) "Setze PUMP_PIN auf LOW (Pumpe an)."
    else if (normalized.contains("digitalWrite(PUMP_PIN, HIGH)")) "Setze PUMP_PIN auf HIGH (Pumpe aus)."
    else if (normalized.contains("Serial.print")) "Gib Text im Serial Monitor ohne Zeilenumbruch aus."
    else if (normalized.contains("Serial.println")) "Gib Text oder Wert im Serial Monitor mit Zeilenumbruch aus."
    else if (normalized.contains("delay(2000)")) "Warte 2000 ms."
    else if (normalized.contains("int feuchtigkeitsGrenze")) "Erstelle eine Ganzzahlvariable für die Feuchtigkeitsgrenze."
    else if (normalized.contains("delay(10000)")) "Warte 10000 ms."
    else if (normalized.startsWith("if")) "Starte einen if-Block."
    else if (normalized.startsWith("} else {")) "Beende den if-Block und starte den else-Zweig."
    else if (normalized.contains("delay(10)")) "Warte 10 ms."
    else if (normalized == "}") "Schließe den Block."
    else "Erklärung für diese Zeile ist nicht hinterlegt."
  }

  def createDndArea(
    allSnippets: List[CodeSnippet],
    sourceSnippets: Var[List[CodeSnippet]],
    targetSnippets: Var[List[CodeSnippet]],
    draggingSnippet: Var[Option[CodeSnippet]],
    targetHoverIndex: Var[Option[Int]],
    title: String,
    orderConstraints: List[(Int, Int)] = Nil
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
          val required = allSnippets.map(_.id)

          val allBlocksUsed = current.length == required.length && current.toSet == required.toSet
          val constraints =
            if (orderConstraints.nonEmpty) orderConstraints
            else required.sliding(2).collect { case List(a, b) => (a, b) }.toList

          val positions = current.zipWithIndex.toMap
          val orderIsValid = constraints.forall { case (first, second) =>
            positions.get(first).exists(firstIdx => positions.get(second).exists(secondIdx => firstIdx < secondIdx))
          }

          if (!allBlocksUsed) {
            dom.window.alert("⚠️ Es fehlen noch Bausteine oder ein Baustein wurde doppelt verwendet.")
          } else if (orderIsValid) {
            dom.window.alert("✅ Sehr gut! Dein Ablauf funktioniert.")
          } else {
            dom.window.alert("⚠️ Fast! Die Logik passt noch nicht ganz. Prüfe, welche Schritte zwingend vor anderen kommen müssen.")
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
      span(
        className := "code-tooltip",
        snippetExplanation(snippet.text)
      ),

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
  private var todoHighlightInstalled: Boolean = false
  private val todoPattern = "([A-Za-z_]*TODO[A-Za-z0-9_]*)".r

  private def markTodoTextNodes(root: dom.Element): Unit = {
    val walker = dom.document.createTreeWalker(
      root,
      dom.NodeFilter.SHOW_TEXT,
      null.asInstanceOf[dom.NodeFilter],
      false
    )
    var node = walker.nextNode()
    var textNodes = List.empty[dom.Text]

    while (node != null) {
      textNodes = node.asInstanceOf[dom.Text] :: textNodes
      node = walker.nextNode()
    }

    textNodes.reverse.foreach { txtNode =>
      val value = Option(txtNode.data).getOrElse("")
      if (value.contains("TODO")) {
        val parent = txtNode.parentNode
        if (parent != null) {
          val frag = dom.document.createDocumentFragment()
          var lastIdx = 0

          todoPattern.findAllMatchIn(value).foreach { m =>
            if (m.start > lastIdx) {
              frag.appendChild(dom.document.createTextNode(value.substring(lastIdx, m.start)))
            }

            val span = dom.document.createElement("span")
            span.setAttribute("class", "todo-token-inline")
            span.textContent = m.matched
            frag.appendChild(span)
            lastIdx = m.end
          }

          if (lastIdx < value.length) {
            frag.appendChild(dom.document.createTextNode(value.substring(lastIdx)))
          }

          parent.replaceChild(frag, txtNode)
        }
      }
    }
  }

  private def installTodoHighlighting(): Unit = {
    if (todoHighlightInstalled) return

    if (dom.document.getElementById("todo-token-style") == null) {
      val styleEl = dom.document.createElement("style")
      styleEl.id = "todo-token-style"
      styleEl.textContent = ".editor-container .token.todo-token, .editor-container .todo-token-inline { color: #c62828 !important; font-weight: 700; }"
      dom.document.head.appendChild(styleEl)
    }

    todoHighlightInstalled = true
  }

  def createCodeEditor(
    codeState: Var[String],
    title: String,
    validator: String => String
  ): HtmlElement = {
    var editorInstance: Option[CodeJar] = None

    div(
      h4(title),

      div(
        className := "info-box small-info",
        span(color := "#c62828", fontWeight := "700", "🟥 TODO"),
        span(" = diese Stellen müsst ihr selbst ausfüllen.")
      ),

      div(
        className := "editor-container language-cpp",

        onMountUnmountCallback(
          mount = { nodeCtx =>
            val element = nodeCtx.thisNode.ref.asInstanceOf[dom.Element]

            installTodoHighlighting()

            val highlightFn: js.Function1[dom.Element, Unit] = { el =>
              Prism.highlightElement(el)

              val tokens = el.querySelectorAll("span.token")
              var i = 0
              while (i < tokens.length) {
                val token = tokens.item(i).asInstanceOf[dom.Element]
                if (token.textContent != null && token.textContent.contains("TODO")) {
                  token.classList.add("todo-token")
                }
                i += 1
              }

              markTodoTextNodes(el)
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
