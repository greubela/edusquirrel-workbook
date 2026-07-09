package it.evadid.homepage.workbook.content

import com.raquo.laminar.api.L.*
import com.raquo.airstream.core.Signal
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.core.datastructures.state.StateHelper.InteractionVariableOnJS
import it.evadid.homepage.control.model.*
import it.evadid.homepage.workbook.htmlRenderer.pluginRenderer.reorderExercise.HtmlReorderInteractionRenderer
import it.evadid.homepage.workbook.legacy.htmlElements.HtmlEmbeddedDomInteraction
import it.evadid.homepage.util.web.DownloadHelper
import it.evadid.homepage.workbook.legacy.plantworkshop.helpers.CodeEditorHelper
import it.evadid.workbook.model.abstractions.WorkbookElement
import it.evadid.workbook.model.elements.*
import it.evadid.workbook.model.elements.ImageElement.FileBasedImageElement
import it.evadid.workbook.model.elements.LabeledInstructionElement.*
import it.evadid.workbook.model.interaction.basic.LabeledCheckboxInteraction
import it.evadid.workbook.model.interaction.plugins.slideshow.{Slideshow, SlideshowPanel}
import todomove.datastructures.web.file.FileFactory

case class CreatePlantworkshopWorkbook(override val fullInfo: FullInfo) extends WorkbookFactory {

  override val workbookId: String = "PlantWorkshop" // todo: Lang map should automatically prefix this

  private def checklist(keys: List[String], prefix: String): List[WorkbookElement] =
    keys.map { key =>
      LabeledCheckboxInteraction(
        nextId(prefix),
        LanguageMapContentId(s"PlantWorkshop/$key")
      )
    }


  private lazy val workbook: Workbook = {
    val sections = List(
      motivationSection,
      componentsSection,
      sensorExploreSection,
      moistureSection,
      pumpControlSection,
      combinedSection,
      testSection
    )

    Workbook(
      workbookId,
      LanguageMapContentId("PlantWorkshop/workbookTitle"),
      sections,
      availableLanguages
    )
  }

  override def createWorkbook: Workbook = workbook

  private def missingElementPlaceholder(contextKey: String): WorkbookElement =
    instructionPlaintext(s"PlantWorkshop/$contextKey")

  private def wiringSlideImage(slideIndex: Int): FileBasedImageElement =
    FileBasedImageElement(
      FileFactory.relativeToResourceFolder(s"img/plantworkshop/schaltkreis/Plant conv $slideIndex.png")
    )

  private def buildWiringPanel(i: Int): SlideshowPanel = {
    val image = wiringSlideImage(i)
    if (i == 3 || i == 4 || i == 8) {
      SlideshowPanel.TwoColumnImagePanel(
        image,
        LanguageMapContentId("PlantWorkshop/LLabel"),
        LanguageMapContentId("PlantWorkshop/RLabel"),
        LanguageMapContentId(s"PlantWorkshop/wiringSlideTextL${i}"),
        LanguageMapContentId(s"PlantWorkshop/wiringSlideTextR${i}")
      )
    } else if (i == 5) {
      SlideshowPanel.ImageSlide(
        image,
        LanguageMapContentId("PlantWorkshop/wiringSlideCurrentStatus"),
        LanguageMapContentId(s"PlantWorkshop/wiringSlideText${i}")
      )
    } else {
      SlideshowPanel.ImageSlide(
        image,
        LanguageMapContentId("PlantWorkshop/wiringSlideHelp"),
        LanguageMapContentId(s"PlantWorkshop/wiringSlideText${i}")
      )
    }
  }

  // Phase A: Arduino, Sensor, Relais verdrahten (Slides 1–5)
  private lazy val wiringSlideshowPhaseA: Slideshow =
    Slideshow("plant-wiring-slideshow-a", (1 to 5).toList.map(buildWiringPanel))

  // Phase B: Pumpe und Schläuche anschließen (Slides 6–11)
  private lazy val wiringSlideshowPhaseB: Slideshow =
    Slideshow("plant-wiring-slideshow-b", (6 to 11).toList.map(buildWiringPanel))

  private lazy val motivationSection: WorkbookSection = {
    val intro = container(
      "PlantWorkshop/section0Title",
      List(
        instructionMarkdown("PlantWorkshop/section0IntroMarkdown"),
        instructionMarkdown("PlantWorkshop/section0SafetyMarkdown")
      )
    )

    section("section0", "PlantWorkshop/section0Title", List(intro))
  }

  private lazy val componentsSection: WorkbookSection = {
    val componentChecklist = checklist(
      List(
        "componentArduino",
        "componentMoistureSensor",
        "componentPump",
        "componentRelay",
        "componentPowerSupply",
        "componentWires",
        "componentHoses",
        "componentConnectionCable",
        "componentUsbCable"
      ),
      "plant-components-check"
    )

    val container1 = container(
      "PlantWorkshop/section1Title",
      List(
        instructionLabeledPair("PlantWorkshop/section1ChecklistIntro", "PlantWorkshop/section1WiringHint", TaskLabel)
      ) ++ componentChecklist
    )

    val container2 = container(
      "PlantWorkshop/section1Subtitle1",
      List(
        instructionLabeledPair("PlantWorkshop/safetyTitle", "PlantWorkshop/section1SafetyText", SafetyLabel),
        wiringSlideshowPhaseA
      )
    )

    section("section1", "PlantWorkshop/section1Title", List(container1, container2))
  }

  private case class CodeTaskToggleResult(
    interaction: HtmlEmbeddedDomInteraction,
    isCorrectSignal: Signal[Boolean]
  )

  private def createCodeTaskToggle(
                                    reorderId: String,
                                    snippets: List[String],
                                    codeEditorTitle: String,
                                    advancedCodeTemplate: String,
                                    hints: List[LanguageMapContentId] = List.empty,
                                    orderConstraints: List[(Int, Int)] = Nil
                                  ): CodeTaskToggleResult = {
    val reorder = codeReorder(reorderId, snippets, AppLanguage.C, hints, orderConstraints)
    val reorderDom = HtmlReorderInteractionRenderer.render(reorder).getDomElement()

    val advancedCodeState = Var(advancedCodeTemplate)

    val codeEditor = CodeEditorHelper.createCodeEditor(
      advancedCodeState,
      codeEditorTitle,
      _ => "Die automatische Code-Prüfung ist noch nicht verfügbar."
    )

    val isBeginnerMode = Var(true)

    val toggleArea = div(
      cls := "task-box",
      div(
        cls := "mode-toggle",
        button(
          cls := "mode-toggle__btn",
          cls.toggle("mode-toggle__btn--active") <-- isBeginnerMode.signal,
          text <-- fullInfo.signals.stringFromLanguageMapId(LanguageMapContentId("basic/beginnerMode")),
          onClick.mapTo(true) --> isBeginnerMode
        ),
        button(
          cls := "mode-toggle__btn",
          cls.toggle("mode-toggle__btn--active") <-- isBeginnerMode.signal.map(!_),
          text <-- fullInfo.signals.stringFromLanguageMapId(LanguageMapContentId("basic/advancedMode")),
          onClick.mapTo(false) --> isBeginnerMode
        )
      ),
      div(
        cls.toggle("mode-hidden") <-- isBeginnerMode.signal.map(!_),
        reorderDom
      ),
      div(
        cls.toggle("mode-hidden") <-- isBeginnerMode.signal,
        codeEditor
      )
    )

    val interaction = HtmlEmbeddedDomInteraction(nextId(reorderId + "-toggle"), toggleArea)

    // Create a signal that tracks whether the reorder task is correctly solved
    val isCorrectSignal: Signal[Boolean] = reorder.interactionVariable.createInteractionSignal().map { state =>
      val current = state.currentOrder
      if (orderConstraints.nonEmpty) {
        val positions = current.zipWithIndex.toMap
        orderConstraints.forall { case (first, second) =>
          positions.get(first).exists(firstIdx => positions.get(second).exists(secondIdx => firstIdx < secondIdx))
        }
      } else {
        current == state.correctOrder
      }
    }

    CodeTaskToggleResult(interaction, isCorrectSignal)
  }

  private val sensorReadAdvancedCodeTemplate: String =
    """// Sensor kurz aktivieren, messen, wieder ausschalten
      |digitalWrite(SENSOR_POWER_PIN, TODO_HIGH_LOW);
      |delay(TODO_DELAY_MS);
      |int messwert = analogRead(TODO_SENSOR_PIN);
      |digitalWrite(SENSOR_POWER_PIN, TODO_HIGH_LOW);
      |
      |// Messwert im Serial Monitor ausgeben
      |Serial.print(TODO_TEXT);
      |Serial.println(TODO_WERT_AUSGABE);""".stripMargin

  private val moistureAdvancedCodeTemplate: String =
    """// Lege den Grenzwert fest, ab dem der Boden als trocken gilt
      |int feuchtigkeitsGrenze = TODO_WERT;
      |
      |// Sensor nur kurz aktivieren, messen, wieder ausschalten
      |digitalWrite(SENSOR_POWER_PIN, TODO_HIGH_LOW);
      |delay(TODO_DELAY_MS);
      |int messwert = analogRead(TODO_SENSOR_PIN);
      |digitalWrite(SENSOR_POWER_PIN, TODO_HIGH_LOW);
      |
      |// Entscheide anhand des Grenzwerts zwischen trocken und feucht
      |if (TODO_BEDINGUNG) {
      |  Serial.println(TODO_TEXT_TROCKEN);
      |} else {
      |  Serial.println(TODO_TEXT_FEUCHT);
      |}""".stripMargin

  private val pumpAdvancedCodeTemplate: String =
    """void loop() {
      |  // Schalte die Pumpe ein (Relais-Logik beachten)
      |  digitalWrite(TODO_PIN, TODO_HIGH_LOW);
      |
      |  // Lass sie für die gewünschte Zeit laufen
      |  delay(TODO_GIESS_DAUER_MS);
      |
      |  // Schalte die Pumpe wieder aus
      |  digitalWrite(TODO_PIN, TODO_HIGH_LOW);
      |}""".stripMargin

  private val combinedAdvancedCodeTemplate: String =
    """// Definiere den Grenzwert einmal außerhalb von loop()
      |int feuchtigkeitsGrenze = TODO_WERT;
      |
      |void loop() {
      |  // 1) Messen
      |  digitalWrite(SENSOR_POWER_PIN, TODO_HIGH_LOW);
      |  delay(TODO_STABILISIERUNG_MS);
      |  int messwert = analogRead(TODO_SENSOR_PIN);
      |  digitalWrite(SENSOR_POWER_PIN, TODO_HIGH_LOW);
      |
      |  // 2) Entscheiden und handeln
      |  if (TODO_BEDINGUNG) {
      |    digitalWrite(TODO_PUMP_PIN, TODO_HIGH_LOW);
      |    delay(TODO_GIESS_DAUER_MS);
      |    digitalWrite(TODO_PUMP_PIN, TODO_HIGH_LOW);
      |  } else {
      |    Serial.println(TODO_FEUCHT_TEXT);
      |  }
      |
      |  // 3) Wartezeit bis zur nächsten Messung
      |  delay(TODO_WARTEZEIT_MS);
      |}""".stripMargin

  private val sensorExploreSketch: String =
    """|/*
       | * Girls Day - Automatische Pflanzen-Bewässerung
       | * Modul 2: Sensor auslesen
       | */
       |
       |const int SENSOR_PIN = A0;
       |const int SENSOR_POWER_PIN = 2;
       |
       |void setup() {
       |  Serial.begin(9600);
       |  pinMode(SENSOR_POWER_PIN, OUTPUT);
       |  digitalWrite(SENSOR_POWER_PIN, LOW);
       |}
       |
       |void loop() {
       |  digitalWrite(SENSOR_POWER_PIN, HIGH);
       |  delay(10);
       |  int messwert = analogRead(SENSOR_PIN);
       |  digitalWrite(SENSOR_POWER_PIN, LOW);
       |
       |  Serial.print("Analoger Wert: ");
       |  Serial.println(messwert);
       |
       |  delay(1000);
       |}
       |""".stripMargin

  private val pumpTestSketch: String =
    """|/*
       | * Girls Day - Automatische Pflanzen-Bewässerung
       | * Modul 4: Pumpe testen
       | */
       |
       |const int PUMP_PIN = 8;
       |
       |void setup() {
       |  Serial.begin(9600);
       |  pinMode(PUMP_PIN, OUTPUT);
       |  digitalWrite(PUMP_PIN, LOW);
       |}
       |
       |void loop() {
       |  digitalWrite(PUMP_PIN, HIGH);
       |  delay(2000);
       |  digitalWrite(PUMP_PIN, LOW);
       |  delay(5000);
       |}
       |""".stripMargin

  private val moistureTestSketch: String =
    """|/*
       | * Girls Day - Automatische Pflanzen-Bewässerung
       | * Modul 3: Fallunterscheidung zum Gießen
       | */
       |
       |const int SENSOR_PIN = A0;
       |const int SENSOR_POWER_PIN = 2;
       |int feuchtigkeitsGrenze = 400;
       |
       |void setup() {
       |  Serial.begin(9600);
       |  pinMode(SENSOR_POWER_PIN, OUTPUT);
       |  digitalWrite(SENSOR_POWER_PIN, LOW);
       |}
       |
       |void loop() {
       |  digitalWrite(SENSOR_POWER_PIN, HIGH);
       |  delay(10);
       |  int messwert = analogRead(SENSOR_PIN);
       |  digitalWrite(SENSOR_POWER_PIN, LOW);
       |
       |  Serial.print("Analoger Wert: ");
       |  Serial.println(messwert);
       |
       |  if (messwert < feuchtigkeitsGrenze) {
       |    Serial.println("Boden ist TROCKEN!");
       |  } else {
       |    Serial.println("Boden ist FEUCHT");
       |  }
       |
       |  delay(1000);
       |}
       |""".stripMargin

  private val combinedSketch: String =
    """|/*
       | * Girls Day - Automatische Pflanzen-Bewässerung
       | * Kompletter Arduino Sketch (alle Module kombiniert)
       | */
       |
       |const int SENSOR_PIN = A0;
       |const int SENSOR_POWER_PIN = 2;
       |const int PUMP_PIN = 8;
       |int feuchtigkeitsGrenze = 400;
       |
       |void setup() {
       |  Serial.begin(9600);
       |  pinMode(SENSOR_POWER_PIN, OUTPUT);
       |  pinMode(PUMP_PIN, OUTPUT);
       |  digitalWrite(SENSOR_POWER_PIN, LOW);
       |  digitalWrite(PUMP_PIN, LOW);
       |}
       |
       |void loop() {
       |  digitalWrite(SENSOR_POWER_PIN, HIGH);
       |  delay(10);
       |  int messwert = analogRead(SENSOR_PIN);
       |  digitalWrite(SENSOR_POWER_PIN, LOW);
       |
       |  Serial.print("Analoger Wert: ");
       |  Serial.println(messwert);
       |
       |  if (messwert < feuchtigkeitsGrenze) {
       |    digitalWrite(PUMP_PIN, HIGH);
       |    delay(2000);
       |    digitalWrite(PUMP_PIN, LOW);
       |  } else {
       |    Serial.println("Boden feucht - keine Bewässerung nötig");
       |  }
       |
       |  delay(10000);
       |}
       |""".stripMargin

  private def createDownloadInteraction(
    buttonLabelKey: String,
    sketchContent: String,
    filename: String,
    id: String,
    enabledSignal: Signal[Boolean]
  ): HtmlEmbeddedDomInteraction = {
    val downloadDiv = div(
      cls := "download-btn-wrapper",
      button(
        cls := "btn-primary",
        disabled <-- enabledSignal.map(!_),
        text <-- fullInfo.signals.stringFromLanguageMapId(LanguageMapContentId(buttonLabelKey)),
        onClick --> { _ => DownloadHelper.downloadFile(filename, sketchContent) }
      )
    )
    HtmlEmbeddedDomInteraction(nextId(id), downloadDiv)
  }

  private lazy val sensorExploreSection: WorkbookSection = {
    val exploreChecklistItems = checklist(
      List("sensorExploreDone1", "sensorExploreDone2", "sensorExploreDone3"),
      "plant-sensor-explore-check"
    )

    val codeTaskResult = createCodeTaskToggle(
      "plant-section2-sensor-read-reorder",
      List(
        "digitalWrite(SENSOR_POWER_PIN, HIGH);",
        "delay(10);",
        "int messwert = analogRead(SENSOR_PIN);",
        "digitalWrite(SENSOR_POWER_PIN, LOW);",
        "Serial.print(\"Analoger Wert: \");",
        "Serial.println(messwert);"
      ),
      "PlantWorkshop/sensorReadCodeEditorTodo",
      sensorReadAdvancedCodeTemplate,
      List(
        LanguageMapContentId("PlantWorkshop/reorderHintSensorPowerHigh"),
        LanguageMapContentId("PlantWorkshop/reorderHintDelay10"),
        LanguageMapContentId("PlantWorkshop/reorderHintAnalogRead"),
        LanguageMapContentId("PlantWorkshop/reorderHintSensorPowerLow"),
        LanguageMapContentId("PlantWorkshop/reorderHintSerialPrint"),
        LanguageMapContentId("PlantWorkshop/reorderHintSerialPrintln")
      ),
      orderConstraints = List(
        0 -> 1,
        1 -> 2,
        2 -> 3,
        3 -> 4,
        4 -> 5
      )
    )

    val downloadContainer = container(
      "PlantWorkshop/section2Subtitle3",
      List(
        instructionLabeledPair("PlantWorkshop/safetyTitle", "PlantWorkshop/section6SafetyWarningText", SafetyLabel),
        instructionMarkdown("PlantWorkshop/section2DownloadSteps"),
        createDownloadInteraction(
          "PlantWorkshop/section2DownloadButton",
          sensorExploreSketch,
          "sensor-auslesen.ino",
          "download-sensor",
          codeTaskResult.isCorrectSignal
        )
      )
    )

    val measurementContainer = container(
      "PlantWorkshop/section2Subtitle4",
      List(
        instructionLabeledPair("PlantWorkshop/section2MeasurementTitle", "PlantWorkshop/section2MeasurementText", TaskLabel)
      )
    )

    val selfCheckContainer2 = container("PlantWorkshop/section2Subtitle5", exploreChecklistItems)

    section(
      "section2",
      "PlantWorkshop/section2Title",
      List(
        container(
          "PlantWorkshop/section2Subtitle1",
          List(
            instructionLabeledPair("PlantWorkshop/goalTitle", "PlantWorkshop/section2GoalText", GoalLabel),
            instructionLabeledPair("PlantWorkshop/instructionTitle", "PlantWorkshop/section2InstructionText", TaskLabel),
            instructionLabeledPair("PlantWorkshop/hintTitle", "PlantWorkshop/section2HintText", HintLabel)
          )
        ),
        container("PlantWorkshop/section2Subtitle2", List(
          instructionCollapsibleHint("PlantWorkshop/ReorderHintTitle", "PlantWorkshop/section2ReorderHintBody"),
          codeTaskResult.interaction
        )),
        downloadContainer,
        measurementContainer,
        selfCheckContainer2
      )
    )
  }

  private lazy val pumpControlSection: WorkbookSection = {
    val checklistItems = checklist(
      List("pumpDone1", "pumpDone2", "pumpDone3"),
      "plant-pump-done"
    )

    val codeTaskResult = createCodeTaskToggle(
      "plant-section4-pump-reorder",
      List(
        "digitalWrite(PUMP_PIN, HIGH);",
        "delay(2000);",
        "digitalWrite(PUMP_PIN, LOW);"
      ),
      "PlantWorkshop/pumpCodeEditorTodo",
      pumpAdvancedCodeTemplate,
      List(
        LanguageMapContentId("PlantWorkshop/reorderHintPumpHigh"),
        LanguageMapContentId("PlantWorkshop/reorderHintDelay2000"),
        LanguageMapContentId("PlantWorkshop/reorderHintPumpLow")
      )
    )

    val wiringPhaseBContainer = container(
      "PlantWorkshop/section4WiringPhaseBTitle",
      List(
        instructionLabeledPair("PlantWorkshop/instructionTitle", "PlantWorkshop/section4WiringPhaseBHint", TaskLabel),
        wiringSlideshowPhaseB
      )
    )

    val downloadContainer = container(
      "PlantWorkshop/section4DownloadTitle",
      List(
        instructionLabeledPair("PlantWorkshop/safetyTitle", "PlantWorkshop/section6SafetyWarningText", SafetyLabel),
        instructionMarkdown("PlantWorkshop/section4DownloadSteps"),
        createDownloadInteraction(
          "PlantWorkshop/section4DownloadButton",
          pumpTestSketch,
          "pumpe-test.ino",
          "download-pump",
          codeTaskResult.isCorrectSignal
        )
      )
    )

    section(
      "section4",
      "PlantWorkshop/section4Title",
      List(
        wiringPhaseBContainer,
        container(
          "PlantWorkshop/section4Subtitle1",
          List(
            instructionLabeledPair("PlantWorkshop/goalTitle", "PlantWorkshop/section4GoalText", GoalLabel),
            instructionLabeledPair("PlantWorkshop/instructionTitle", "PlantWorkshop/section4InstructionText", TaskLabel),
            instructionLabeledPair("PlantWorkshop/hintTitle", "PlantWorkshop/section4HintText", HintLabel)
          )
        ),
        container("PlantWorkshop/section4Subtitle2", List(
          instructionCollapsibleHint("PlantWorkshop/ReorderHintTitle", "PlantWorkshop/section4ReorderHintBody"),
          codeTaskResult.interaction
        )),
        downloadContainer,
        container("PlantWorkshop/section4Subtitle3", checklistItems)
      )
    )
  }

  private lazy val moistureSection: WorkbookSection = {
    val checklistItems = checklist(
      List("moistureDone1", "moistureDone2", "moistureDone3"),
      "plant-moisture-done"
    )

    val codeTaskResult = createCodeTaskToggle(
      "plant-section3-sensor-reorder",
      List(
        "int feuchtigkeitsGrenze = 400;",
        "digitalWrite(SENSOR_POWER_PIN, HIGH);",
        "delay(10);",
        "int messwert = analogRead(SENSOR_PIN);",
        "digitalWrite(SENSOR_POWER_PIN, LOW);",
        "if (messwert < feuchtigkeitsGrenze) {",
        "  Serial.println(\"Boden ist TROCKEN!\");",
        "} else {",
        "  Serial.println(\"Boden ist FEUCHT\");",
        "}"
      ),
      "PlantWorkshop/moistureCodeEditorTodo",
      moistureAdvancedCodeTemplate,
      List(
        LanguageMapContentId("PlantWorkshop/reorderHintFeuchtigkeitsGrenze"),
        LanguageMapContentId("PlantWorkshop/reorderHintSensorPowerHigh"),
        LanguageMapContentId("PlantWorkshop/reorderHintDelay10"),
        LanguageMapContentId("PlantWorkshop/reorderHintAnalogRead"),
        LanguageMapContentId("PlantWorkshop/reorderHintSensorPowerLow"),
        LanguageMapContentId("PlantWorkshop/reorderHintMesswertCheck"),
        LanguageMapContentId("PlantWorkshop/reorderHintSerialPrintln"),
        LanguageMapContentId("PlantWorkshop/reorderHintElseBlock"),
        LanguageMapContentId("PlantWorkshop/reorderHintSerialPrintln"),
        LanguageMapContentId("PlantWorkshop/reorderHintCloseBrace")
      ),
      orderConstraints = List(
        1 -> 2,
        2 -> 3,
        3 -> 4,
        0 -> 5,
        4 -> 5,
        5 -> 6,
        6 -> 7,
        7 -> 8,
        8 -> 9
      )
    )

    val moistureDownloadContainer = container(
      "PlantWorkshop/section3Subtitle4",
      List(
        instructionLabeledPair("PlantWorkshop/safetyTitle", "PlantWorkshop/section6SafetyWarningText", SafetyLabel),
        instructionMarkdown("PlantWorkshop/section3DownloadSteps"),
        createDownloadInteraction(
          "PlantWorkshop/section3DownloadButton",
          moistureTestSketch,
          "feuchtigkeit-messen.ino",
          "download-moisture",
          codeTaskResult.isCorrectSignal
        )
      )
    )

    section(
      "section3",
      "PlantWorkshop/section3Title",
      List(
        container(
          "PlantWorkshop/section3Subtitle1",
          List(
            instructionLabeledPair("PlantWorkshop/goalTitle", "PlantWorkshop/section3GoalText", GoalLabel),
            instructionLabeledPair("PlantWorkshop/instructionTitle", "PlantWorkshop/section3InstructionText", TaskLabel),
            instructionLabeledPair("PlantWorkshop/hintTitle", "PlantWorkshop/section3HintText", HintLabel)
          )
        ),
        container("PlantWorkshop/section3Subtitle2", List(
          instructionCollapsibleHint("PlantWorkshop/ReorderHintTitle", "PlantWorkshop/section3ReorderHintBody"),
          codeTaskResult.interaction
        )),
        moistureDownloadContainer,
        container("PlantWorkshop/section3Subtitle3", checklistItems)
      )
    )
  }

  private lazy val combinedSection: WorkbookSection = {
    val checklistItems = checklist(
      List("combinedDone1", "combinedDone2", "combinedDone3"),
      "plant-combined-done"
    )

    val codeTaskResult = createCodeTaskToggle(
      "plant-combined-reorder",
      List(
        "int feuchtigkeitsGrenze = 400;",
        "digitalWrite(SENSOR_POWER_PIN, HIGH);",
        "delay(10);",
        "int messwert = analogRead(SENSOR_PIN);",
        "digitalWrite(SENSOR_POWER_PIN, LOW);",
        "if (messwert < feuchtigkeitsGrenze) {",
        "  digitalWrite(PUMP_PIN, HIGH);",
        "  delay(2000);",
        "  digitalWrite(PUMP_PIN, LOW);",
        "} else {",
        "  Serial.println(\"Boden feucht - keine Bewässerung nötig\");",
        "}",
        "delay(10000);"
      ),
      "PlantWorkshop/combinedCodeEditorTodo",
      combinedAdvancedCodeTemplate,
      List(
        LanguageMapContentId("PlantWorkshop/reorderHintFeuchtigkeitsGrenze"),
        LanguageMapContentId("PlantWorkshop/reorderHintSensorPowerHigh"),
        LanguageMapContentId("PlantWorkshop/reorderHintDelay10"),
        LanguageMapContentId("PlantWorkshop/reorderHintAnalogRead"),
        LanguageMapContentId("PlantWorkshop/reorderHintSensorPowerLow"),
        LanguageMapContentId("PlantWorkshop/reorderHintMesswertCheck"),
        LanguageMapContentId("PlantWorkshop/reorderHintPumpHigh"),
        LanguageMapContentId("PlantWorkshop/reorderHintDelay2000"),
        LanguageMapContentId("PlantWorkshop/reorderHintPumpLow"),
        LanguageMapContentId("PlantWorkshop/reorderHintElseBlock"),
        LanguageMapContentId("PlantWorkshop/reorderHintSerialPrintln"),
        LanguageMapContentId("PlantWorkshop/reorderHintCloseBrace"),
        LanguageMapContentId("PlantWorkshop/reorderHintDelay10000")
      ),
      orderConstraints = List(
        1 -> 2,
        2 -> 3,
        3 -> 4,
        0 -> 5,
        4 -> 5,
        5 -> 6,
        6 -> 7,
        7 -> 8,
        8 -> 9,
        9 -> 10,
        10 -> 11,
        11 -> 12
      )
    )

    val combinedDownloadContainer = container(
      "PlantWorkshop/section5Subtitle4",
      List(
        instructionLabeledPair("PlantWorkshop/safetyTitle", "PlantWorkshop/section6SafetyWarningText", SafetyLabel),
        instructionMarkdown("PlantWorkshop/section6DownloadSteps"),
        createDownloadInteraction(
          "PlantWorkshop/section6DownloadButton",
          combinedSketch,
          "plantworkshop.ino",
          "download-combined",
          codeTaskResult.isCorrectSignal
        )
      )
    )

    section(
      "section5",
      "PlantWorkshop/section5Title",
      List(
        container(
          "PlantWorkshop/section5Subtitle1",
          List(
            instructionLabeledPair("PlantWorkshop/goalTitle", "PlantWorkshop/section5GoalText", GoalLabel),
            instructionLabeledPair("PlantWorkshop/instructionTitle", "PlantWorkshop/section5InstructionText", TaskLabel),
            instructionLabeledPair("PlantWorkshop/hintTitle", "PlantWorkshop/section5HintText", HintLabel)
          )
        ),
        container("PlantWorkshop/section5Subtitle2", List(
          instructionCollapsibleHint("PlantWorkshop/ReorderHintTitle", "PlantWorkshop/section5ReorderHintBody"),
          codeTaskResult.interaction
        )),
        combinedDownloadContainer,
        container("PlantWorkshop/section5Subtitle3", checklistItems)
      )
    )
  }

  private lazy val testSection: WorkbookSection = {
    val testChecklistItems = checklist(
      List(
        "testChecklistSensorDry",
        "testChecklistSensorWet",
        "testChecklistPumpDry",
        "testChecklistPumpStopsShortly",
        "testChecklistMoistNoPump"
      ),
      "plant-test-check"
    )

    val testChecklistContainer = container(
      "PlantWorkshop/section6TestChecklistTitle",
      List(
        instructionLabeledPair("PlantWorkshop/section6TestChecklistTitle", "PlantWorkshop/section6TestChecklistIntro", TaskLabel)
      ) ++ testChecklistItems
    )

    val troubleshootingContainer = container(
      "PlantWorkshop/section6TroubleshootingTitle",
      List(
        instructionLabeledPair("PlantWorkshop/section6TroubleshootingTitle", "PlantWorkshop/section6TroubleshootingText", HintLabel)
      )
    )

    val bonusContainer = container(
      "PlantWorkshop/section6BonusTitle",
      List(
        instructionLabeledPair("PlantWorkshop/section6BonusTitle", "PlantWorkshop/section6BonusText", GoalLabel)
      )
    )

    val congratulationsContainer = container(
      "PlantWorkshop/section6Congratulations",
      List.empty[WorkbookElement]
    )

    section("section6", "PlantWorkshop/section6Title", List(
      testChecklistContainer,
      troubleshootingContainer,
      bonusContainer,
      congratulationsContainer
    ))
  }
}