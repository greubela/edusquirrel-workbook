package content.plantworkshop.tasks

import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.L.Signal
import content.plantworkshop.PlantWorkshopApp
import content.plantworkshop.helpers.{CodeEditorHelper, CodeSnippet, DragAndDropHelper}

/** Combined sensor and pump task. */
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
    CodeSnippet(1, "int feuchtigkeitsGrenze = 400;"),
    CodeSnippet(2, "digitalWrite(SENSOR_POWER_PIN, HIGH);"),
    CodeSnippet(3, "delay(10);"),
    CodeSnippet(4, "int messwert = analogRead(SENSOR_PIN);"),
    CodeSnippet(5, "digitalWrite(SENSOR_POWER_PIN, LOW);"),
    CodeSnippet(6, "Serial.print(\"Analoger Wert: \" );"),
    CodeSnippet(7, "Serial.println(messwert);"),
    CodeSnippet(8, "if (messwert < feuchtigkeitsGrenze) {"),
    CodeSnippet(9, "  digitalWrite(PUMP_PIN, HIGH);"),
    CodeSnippet(10, "  delay(2000);"),
    CodeSnippet(11, "  digitalWrite(PUMP_PIN, LOW);"),
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
          (if (!hasPumpControl) "- Pumpensteuerung (HIGH/LOW) unvollständig\n" else "") +
          (if (!hasSerial) "- Ausgabe des Messwerts fehlt\n" else "") +
          (if (!hasLoopDelay) "- Wartezeit delay(10000) fehlt\n" else "")
        }
      }
    )
  }
}
