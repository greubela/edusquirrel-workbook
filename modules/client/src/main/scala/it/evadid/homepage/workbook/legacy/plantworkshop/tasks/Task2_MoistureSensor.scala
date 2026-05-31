package it.evadid.homepage.workbook.legacy.plantworkshop.tasks

import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.L.Signal
import it.evadid.homepage.workbook.legacy.plantworkshop.PlantWorkshopApp
import it.evadid.homepage.workbook.legacy.plantworkshop.helpers.{CodeEditorHelper, CodeSnippet, DragAndDropHelper}

/** Moisture sensor task. */
object Task2_MoistureSensor {
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
    CodeSnippet(1, "int feuchtigkeitsGrenze = 400;"),
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
          "Setzt außerdem einen Grenzwert, z. B. ", code("int feuchtigkeitsGrenze = 400;"), "."
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
