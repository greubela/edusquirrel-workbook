package it.evadid.homepage.workbook.content

import com.raquo.laminar.api.L.{*, given}
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.homepage.control.info.FullInfo
import it.evadid.homepage.workbook.htmlRenderer.pluginRenderer.reorderExercise.HtmlReorderInteractionRenderer
import it.evadid.homepage.workbook.legacy.htmlElements.HtmlEmbeddedDomInteraction
import it.evadid.homepage.workbook.legacy.plantworkshop.helpers.*
import it.evadid.workbook.model.abstractions.WorkbookElement
import it.evadid.workbook.model.elements.*
import it.evadid.workbook.model.elements.ImageElement.FileBasedImageElement
import it.evadid.workbook.model.elements.LabeledInstructionElement.*
import it.evadid.workbook.model.interaction.basic.LabeledCheckboxInteraction
import it.evadid.workbook.model.interaction.plugins.slideshow.{Slideshow, SlideshowPanel}
import todomove.datastructures.web.file.FileFactory

case class CreatePlantworkshopWorkbook(override val fullInfo: FullInfo) extends WorkbookFactory {

  override val workbookId: String = "PlantWorkshop"

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
      pumpControlSection,
      moistureSection,
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

  private lazy val wiringSlideshow: Slideshow = {
    val panels = (1 to 11).toList.map { i =>
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

    Slideshow("plant-wiring-slideshow", panels)
  }

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
        instructionLabeledPair("PlantWorkshop/section1ChecklistIntro","PlantWorkshop/section1WiringHint", TaskLabel)
      ) ++ componentChecklist
    )

    val container2 = container(
      "PlantWorkshop/section1Subtitle1",
      List(
        instructionLabeledPair("PlantWorkshop/safetyTitle", "PlantWorkshop/section1SafetyText", SafetyLabel),
        wiringSlideshow
      )
    )

    section("section1", "PlantWorkshop/section1Title", List(container1, container2))
  }

  private def createCodeTaskToggle(
    reorderId: String,
    snippets: List[String],
    codeEditorTitle: String,
    hints: List[LanguageMapContentId] = List.empty,
    orderConstraints: List[(Int, Int)] = Nil
  ): HtmlEmbeddedDomInteraction = {
    val reorder = codeReorder(reorderId, snippets, AppLanguage.C, hints, orderConstraints)
    val reorderDom = HtmlReorderInteractionRenderer.render(reorder).getDomElement()

    val advancedCodeState = Var(
      "// TODO: Ergänze hier dein Programm\n// Beispiel:\n// digitalWrite(PUMP_PIN, HIGH);\n// delay(2000);\n// digitalWrite(PUMP_PIN, LOW);"
    )

    val codeEditor = CodeEditorHelper.createCodeEditor(
      advancedCodeState,
      codeEditorTitle,
      PumpControlValidator.validatePumpControl
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

    HtmlEmbeddedDomInteraction(nextId(reorderId + "-toggle"), toggleArea)
  }

  private lazy val pumpControlSection: WorkbookSection = {
    val checklistItems = checklist(
      List("pumpDone1", "pumpDone2", "pumpDone3"),
      "plant-pump-done"
    )

    val codeTask = createCodeTaskToggle(
      "plant-pump-reorder",
      List(
        "digitalWrite(PUMP_PIN, HIGH);",
        "delay(2000);",
        "digitalWrite(PUMP_PIN, LOW);"
      ),
      "PlantWorkshop/pumpCodeEditorTodo",
      List(
        LanguageMapContentId("PlantWorkshop/reorderHintPumpHigh"),
        LanguageMapContentId("PlantWorkshop/reorderHintDelay2000"),
        LanguageMapContentId("PlantWorkshop/reorderHintPumpLow")
      )
    )

    section(
      "section2",
      "PlantWorkshop/section2Title",
      List(
        container(
          "PlantWorkshop/section2Title",
          List(
            instructionLabeledPair("PlantWorkshop/goalTitle", "PlantWorkshop/section2GoalText", GoalLabel),
            instructionLabeledPair("PlantWorkshop/instructionTitle", "PlantWorkshop/section2InstructionText", TaskLabel),
            instructionLabeledPair("PlantWorkshop/hintTitle", "PlantWorkshop/section2HintText", HintLabel)
          )
        ),
        container("PlantWorkshop/section2Title", List(codeTask)),
        container("PlantWorkshop/section2Title", checklistItems)
      )
    )
  }

  private lazy val moistureSection: WorkbookSection = {
    val checklistItems = checklist(
      List("moistureDone1", "moistureDone2", "moistureDone3"),
      "plant-moisture-done"
    )

    val codeTask = createCodeTaskToggle(
      "plant-moisture-reorder",
      List(
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
      "PlantWorkshop/pumpCodeEditorTodo",
      List(
        LanguageMapContentId("PlantWorkshop/reorderHintSensorPowerHigh"),
        LanguageMapContentId("PlantWorkshop/reorderHintDelay10"),
        LanguageMapContentId("PlantWorkshop/reorderHintAnalogRead"),
        LanguageMapContentId("PlantWorkshop/reorderHintSensorPowerLow"),
        LanguageMapContentId("PlantWorkshop/reorderHintMesswertCheck"),
        LanguageMapContentId("PlantWorkshop/reorderHintSerialPrintln"),
        LanguageMapContentId("PlantWorkshop/reorderHintElseBlock"),
        LanguageMapContentId("PlantWorkshop/reorderHintSerialPrintln"),
        LanguageMapContentId("PlantWorkshop/reorderHintCloseBrace")
      )
    )

    section(
      "section3",
      "PlantWorkshop/section3Title",
      List(
        container(
          "PlantWorkshop/section3Title",
          List(
            instructionLabeledPair("PlantWorkshop/goalTitle", "PlantWorkshop/section3GoalText", GoalLabel),
            instructionLabeledPair("PlantWorkshop/instructionTitle", "PlantWorkshop/section3InstructionText", TaskLabel),
            instructionLabeledPair("PlantWorkshop/hintTitle", "PlantWorkshop/section3HintText", HintLabel)
          )
        ),
        container("PlantWorkshop/section3Title", List(codeTask)),
        container("PlantWorkshop/section3Title", checklistItems)
      )
    )
  }

  private lazy val combinedSection: WorkbookSection = {
    val checklistItems = checklist(
      List("combinedDone1", "combinedDone2", "combinedDone3"),
      "plant-combined-done"
    )

    val codeTask = createCodeTaskToggle(
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
      "PlantWorkshop/pumpCodeEditorTodo",
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

    section(
      "section4",
      "PlantWorkshop/section4Title",
      List(
        container(
          "PlantWorkshop/section4Title",
          List(
            instructionLabeledPair("PlantWorkshop/goalTitle", "PlantWorkshop/section4GoalText", GoalLabel),
            instructionLabeledPair("PlantWorkshop/instructionTitle", "PlantWorkshop/section4InstructionText", TaskLabel),
            instructionLabeledPair("PlantWorkshop/hintTitle", "PlantWorkshop/section4HintText", HintLabel)
          )
        ),
        container("PlantWorkshop/section4Title", List(codeTask)),
        container("PlantWorkshop/section4Title", checklistItems)
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

    val downloadContainer = container(
      "PlantWorkshop/section5Title",
      List(
        instructionLabeledPair("PlantWorkshop/safetyTitle", "PlantWorkshop/section5SafetyWarningText", SafetyLabel),
        instructionMarkdown("PlantWorkshop/section5DownloadSteps")
      )
    )

    val testChecklistContainer = container(
      "PlantWorkshop/section5TestChecklistTitle",
      List(
        instructionLabeledPair("PlantWorkshop/section5TestChecklistTitle", "PlantWorkshop/section5TestChecklistIntro", TaskLabel)
      ) ++ testChecklistItems
    )

    val troubleshootingContainer = container(
      "PlantWorkshop/section5TroubleshootingTitle",
      List(
        instructionLabeledPair("PlantWorkshop/section5TroubleshootingTitle", "PlantWorkshop/section5TroubleshootingText", HintLabel)
      )
    )

    val bonusContainer = container(
      "PlantWorkshop/section5BonusTitle",
      List(
        instructionLabeledPair("PlantWorkshop/section5BonusTitle", "PlantWorkshop/section5BonusText", GoalLabel)
      )
    )

    val congratulationsContainer = container(
      "PlantWorkshop/section5Congratulations",
      List(
        instructionPlaintext("PlantWorkshop/section5Congratulations")
      )
    )

    section("section5", "PlantWorkshop/section5Title", List(
      downloadContainer,
      testChecklistContainer,
      troubleshootingContainer,
      bonusContainer,
      congratulationsContainer
    ))
  }
}