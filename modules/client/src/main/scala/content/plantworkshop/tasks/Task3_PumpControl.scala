package content.plantworkshop.tasks

import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.L.Signal
import content.plantworkshop.PlantWorkshopApp
import content.plantworkshop.helpers.{CodeEditorHelper, CodeSnippet, DragAndDropHelper}

/** Pump control task. */
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
    CodeSnippet(1, "digitalWrite(PUMP_PIN, HIGH);"),
    CodeSnippet(2, "delay(2000);"),
    CodeSnippet(3, "digitalWrite(PUMP_PIN, LOW);")
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
          "In dieser Schaltung wird das Relais mit ", code("digitalWrite(PUMP_PIN, HIGH)"), " eingeschaltet und mit ", code("LOW"), " ausgeschaltet."
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
      "Setze die Bausteine in die richtige Reihenfolge:"
    )
  }

  def advancedView(): HtmlElement = {
    CodeEditorHelper.createCodeEditor(
      advancedCodeState,
      "Schreibt den Code für die Pumpensteuerung:",
      code => {
        val hasOn = code.contains("digitalWrite(PUMP_PIN, HIGH)")
        val hasOff = code.contains("digitalWrite(PUMP_PIN, LOW)")
        val hasShortDelay = code.contains("delay(2000)")

        if (hasOn && hasOff && hasShortDelay) {
          "✅ Perfekt! Die Pumpe wird richtig gesteuert!"
        } else {
          "⚠️ Code sieht noch nicht vollständig aus. Prüfe:\n" +
          (if (!hasOn) "- Pumpe einschalten (HIGH)\n" else "") +
          (if (!hasShortDelay) "- 2 Sekunden warten\n" else "") +
          (if (!hasOff) "- Pumpe ausschalten (LOW)\n" else "")
        }
      }
    )
  }
}
