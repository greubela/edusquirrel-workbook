package it.evadid.homepage.workbook.legacy.plantworkshop.tasks

import com.raquo.laminar.api.L.*
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import org.scalajs.dom

/** Final test and export. */
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
       |int feuchtigkeitsGrenze = 400;
       |
       |void setup() {
       |  Serial.begin(9600);
       |
       |  pinMode(PUMP_PIN, OUTPUT);
       |  pinMode(SENSOR_POWER_PIN, OUTPUT);
       |
       |  digitalWrite(PUMP_PIN, LOW);
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
    HtmlFullWorkbookApp.fullInfo.contentControl.downloadToDisc.downloadFile("plantworkshop.ino", code)
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
          className := "btn-primary",
          onClick --> { _ => downloadArduinoSketch() }
        )
      ),

      div(
        className := "task-box",
        h3("Test-Checkliste"),
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
        h3("Häufige Fehler"),
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
            li("Sind die Kabel am Sensor richtig? (Analoge und Digitale Pins nicht vertauschen)")
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
