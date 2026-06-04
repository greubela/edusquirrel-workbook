package content

import com.raquo.laminar.api.L.{*, given}
import datastructures.web.file.FileDescription
import interactionPlugins.slideshow.{SlideDeckExercise, SlidePanel}
import workbook.htmlElements.basic.*
import workbook.htmlElements.container.HtmlExerciseContainer
import workbook.htmlElements.interactions.{HtmlBasicCheckboxInteraction, HtmlReorderInteraction}
import workbook.model.{Workbook, WorkbookSection}
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.FullInfo

import scala.concurrent.ExecutionContext
import content.plantworkshop.helpers.{CodeEditorHelper, PumpControlValidator}

case class CreatePlantworkshopWorkbook(override val fullInfo: FullInfo) extends WorkbookFactory {

  private def checklist(keys: List[String], prefix: String): List[HtmlWorkbookElement] =
    keys.map { key =>
      HtmlBasicCheckboxInteraction(
        fullInfo = fullInfo,
        id = nextId(prefix),
        labelLanguageMapId = s"PlantWorkshop/$key"
      )
    }

  private def codeReorder(baseId: String, snippets: List[String]): HtmlReorderInteraction[String] =
    HtmlReorderInteraction[String](
      fullInfo = fullInfo,
      id = nextId(baseId),
      elements = snippets,
      elementRenderer = snippet => pre(code(snippet)),
      itemCssClass = "reorder-item--code"
    )

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
      fullInfo = fullInfo,
      titleLanguageMapId = "PlantWorkshop/workbookTitle",
      sections = sections
    )
  }

  override def createWorkbook: Workbook = workbook

  private def missingElementPlaceholder(contextKey: String): HtmlWorkbookElement = instructionPlaintext(s"PlantWorkshop/$contextKey")

  private lazy val motivationSection: WorkbookSection = {
    val intro = HtmlExerciseContainer(fullInfo, List(
      HtmlContainerTitle(fullInfo, "PlantWorkshop/section0Title"),
      instructionMarkdown("PlantWorkshop/section0IntroMarkdown"),
      instructionMarkdown("PlantWorkshop/section0SafetyMarkdown")
    ))

    WorkbookSection(fullInfo, "PlantWorkshop/section0Title", List(intro))
  }

  private def createWiringSlideshow(): SlideDeckExercise = {
    val slides = (1 to 11).toList.map { i =>
      if (i == 3 || i == 4 || i == 8) {
        SlidePanel.imageSlideTwoColumns(
          FileDescription.relativeToResourceFolder(s"img/plantworkshop/schaltkreis/Plant conv $i.png"),
          leftLabel = s"PlantWorkshop/LLabel",
          rightLabel = s"PlantWorkshop/RLabel",
          leftBody = s"PlantWorkshop/wiringSlideTextL${i}",
          rightBody = s"PlantWorkshop/wiringSlideTextR${i}",
          fullInfo = fullInfo
        )
      } else if (i == 5) {
        SlidePanel.imageSlide(
          FileDescription.relativeToResourceFolder(s"img/plantworkshop/schaltkreis/Plant conv $i.png"),
          textMapId = s"PlantWorkshop/wiringSlideText${i}",
          descriptionMapId = s"PlantWorkshop/wiringSlideCurrentStatus",
          fullInfo = fullInfo
        )
      } else {
        SlidePanel.imageSlide(
          FileDescription.relativeToResourceFolder(s"img/plantworkshop/schaltkreis/Plant conv $i.png"),
          textMapId = s"PlantWorkshop/wiringSlideText${i}",
          descriptionMapId = s"PlantWorkshop/wiringSlideHelp",
          fullInfo = fullInfo
        )
      }
    }

    SlideDeckExercise(
      fullInfo = fullInfo,
      id = nextId("plant-wiring-slideshow"),
      slides = slides
    )
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

    val container = List(
      HtmlExerciseContainer(fullInfo,
        List(
          HtmlContainerTitle(fullInfo, "PlantWorkshop/section1Title"),
          instructionPlaintext("PlantWorkshop/section1ChecklistIntro"),
          instructionPlaintext("PlantWorkshop/section1WiringHint")
        ) ++ componentChecklist
      ),
      HtmlExerciseContainer(fullInfo,
        List(
          HtmlContainerTitle(fullInfo, "PlantWorkshop/section1Subtitle1"),
          instructionLabeledPair("PlantWorkshop/safetyTitle", "PlantWorkshop/section1SafetyText", "instruction-safety"),
          createWiringSlideshow()
        )
      )
    )

    WorkbookSection(fullInfo, "PlantWorkshop/section1Title", container)
  }

  private lazy val pumpControlSection: WorkbookSection = {
    val reorder = codeReorder("plant-pump-reorder", List(
      "digitalWrite(PUMP_PIN, HIGH);",
      "delay(2000);",
      "digitalWrite(PUMP_PIN, LOW);"
    ))
    // Initialize the reorder DOM once so its state persists across toggles
    val reorderDom = reorder.getDomElement()

    val advancedCodeState = Var(
      "// TODO: Ergänze hier dein Programm\n// Beispiel:\n// digitalWrite(PUMP_PIN, HIGH);\n// delay(2000);\n// digitalWrite(PUMP_PIN, LOW);"
    )

    val codeEditor = CodeEditorHelper.createCodeEditor(
      advancedCodeState,
      "PlantWorkshop/pumpCodeEditorTodo",
      PumpControlValidator.validatePumpControl
    )

    val checklistItems = checklist(
      List("pumpDone1", "pumpDone2", "pumpDone3"),
      "plant-pump-done"
    )

    val isBeginnerMode = Var(true)

    val toggleArea = div(
      cls := "task-box",
      div(
        cls := "mode-toggle",
        button(
          cls := "mode-toggle__btn",
          cls.toggle("mode-toggle__btn--active") <-- isBeginnerMode.signal,
          "Anfänger",
          onClick.mapTo(true) --> isBeginnerMode
        ),
        button(
          cls := "mode-toggle__btn",
          cls.toggle("mode-toggle__btn--active") <-- isBeginnerMode.signal.map(!_),
          "Fortgeschritten",
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

    val containers = List(
      HtmlExerciseContainer(fullInfo, List(
        HtmlContainerTitle(fullInfo, "PlantWorkshop/section2Title"),
        instructionLabeledPair("PlantWorkshop/goalTitle", "PlantWorkshop/section2GoalText", "instruction-goal"),
        instructionLabeledPair("PlantWorkshop/instructionTitle", "PlantWorkshop/section2InstructionText", "instruction-task"),
        instructionLabeledPair("PlantWorkshop/hintTitle", "PlantWorkshop/section2HintText", "instruction-hint")
      )),
      HtmlExerciseContainer(fullInfo, List(
        pseudoElement(toggleArea)
      )),
      HtmlExerciseContainer(fullInfo,
        List(
          instructionPlaintext("PlantWorkshop/section2AdvancedHint")
        ) ++ checklistItems
      )
    )

    WorkbookSection(fullInfo, "PlantWorkshop/section2Title", containers)
  }

  private lazy val moistureSection: WorkbookSection = {
    val reorder = codeReorder("plant-moisture-reorder", List(
      "digitalWrite(SENSOR_POWER_PIN, HIGH);",
      "delay(10);",
      "int messwert = analogRead(SENSOR_PIN);",
      "digitalWrite(SENSOR_POWER_PIN, LOW);",
      "if (messwert < feuchtigkeitsGrenze) {",
      "  Serial.println(\"Boden ist TROCKEN!\");",
      "} else {",
      "  Serial.println(\"Boden ist FEUCHT\");",
      "}"
    ))
    val reorderDom = reorder.getDomElement()

    val checklistItems = checklist(
      List("moistureDone1", "moistureDone2", "moistureDone3"),
      "plant-moisture-done"
    )

    val isBeginnerMode = Var(true)

    val advancedCodeState = Var(
      "// TODO: Ergänze hier dein Programm\n// Beispiel:\n// digitalWrite(PUMP_PIN, HIGH);\n// delay(2000);\n// digitalWrite(PUMP_PIN, LOW);"
    )

    val codeEditor = CodeEditorHelper.createCodeEditor(
      advancedCodeState,
      "PlantWorkshop/pumpCodeEditorTodo",
      PumpControlValidator.validatePumpControl
    )
    val toggleArea = div(
      cls := "task-box",
      div(
        cls := "mode-toggle",
        button(
          cls := "mode-toggle__btn",
          cls.toggle("mode-toggle__btn--active") <-- isBeginnerMode.signal,
          "Anfänger",
          onClick.mapTo(true) --> isBeginnerMode
        ),
        button(
          cls := "mode-toggle__btn",
          cls.toggle("mode-toggle__btn--active") <-- isBeginnerMode.signal.map(!_),
          "Fortgeschritten",
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

    val containers = List(
      HtmlExerciseContainer(fullInfo, List(
        HtmlContainerTitle(fullInfo, "PlantWorkshop/section3Title"),
        instructionLabeledPair("PlantWorkshop/goalTitle", "PlantWorkshop/section3GoalText", "instruction-goal"),
        instructionLabeledPair("PlantWorkshop/instructionTitle", "PlantWorkshop/section3InstructionText", "instruction-task"),
        instructionLabeledPair("PlantWorkshop/hintTitle", "PlantWorkshop/section3HintText", "instruction-hint")
      )),
      HtmlExerciseContainer(fullInfo, List(
        pseudoElement(toggleArea)
      )),
      HtmlExerciseContainer(fullInfo,
        List(
          instructionPlaintext("PlantWorkshop/section3AdvancedHint")
        ) ++ checklistItems
      )
    )

    WorkbookSection(fullInfo, "PlantWorkshop/section3Title", containers)
  }

  private lazy val combinedSection: WorkbookSection = {
    val reorder = codeReorder("plant-combined-reorder", List(
      "digitalWrite(SENSOR_POWER_PIN, HIGH);",
      "delay(10);",
      "int messwert = analogRead(SENSOR_PIN);",
      "digitalWrite(SENSOR_POWER_PIN, LOW);",
      "if (messwert < feuchtigkeitsGrenze) {",
      "  digitalWrite(PUMP_PIN, LOW);",
      "  delay(2000);",
      "  digitalWrite(PUMP_PIN, HIGH);",
      "}",
      "delay(10000);"
    ))
    val reorderDom = reorder.getDomElement()

    val checklistItems = checklist(
      List("combinedDone1", "combinedDone2", "combinedDone3"),
      "plant-combined-done"
    )

    val isBeginnerMode = Var(true)

    val advancedCodeState = Var(
      "// TODO: Ergänze hier dein Programm\n// Beispiel:\n// digitalWrite(PUMP_PIN, HIGH);\n// delay(2000);\n// digitalWrite(PUMP_PIN, LOW);"
    )

    val codeEditor = CodeEditorHelper.createCodeEditor(
      advancedCodeState,
      "PlantWorkshop/pumpCodeEditorTodo",
      PumpControlValidator.validatePumpControl
    )
    
    val toggleArea = div(
      cls := "task-box",
      div(
        cls := "mode-toggle",
        button(
          cls := "mode-toggle__btn",
          cls.toggle("mode-toggle__btn--active") <-- isBeginnerMode.signal,
          "Anfänger",
          onClick.mapTo(true) --> isBeginnerMode
        ),
        button(
          cls := "mode-toggle__btn",
          cls.toggle("mode-toggle__btn--active") <-- isBeginnerMode.signal.map(!_),
          "Fortgeschritten",
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

    val containers = List(
      HtmlExerciseContainer(fullInfo, List(
        HtmlContainerTitle(fullInfo, "PlantWorkshop/section4Title"),
        instructionLabeledPair("PlantWorkshop/goalTitle", "PlantWorkshop/section4GoalText", "instruction-goal"),
        instructionLabeledPair("PlantWorkshop/instructionTitle", "PlantWorkshop/section4InstructionText", "instruction-task"),
        instructionLabeledPair("PlantWorkshop/hintTitle", "PlantWorkshop/section4HintText", "instruction-hint")
      )),
      HtmlExerciseContainer(fullInfo, List(
        pseudoElement(toggleArea)
      )),
      HtmlExerciseContainer(fullInfo,
        List(
          instructionPlaintext("PlantWorkshop/section4AdvancedHint")
        ) ++ checklistItems
      )
    )

    WorkbookSection(fullInfo, "PlantWorkshop/section4Title", containers)
  }

  private lazy val testSection: WorkbookSection = {
    val testChecklistItems = checklist(
      List(
        "testChecklistSensorValues",
        "testChecklistPumpStarts",
        "testChecklistPumpStops"
      ),
      "plant-test-check"
    )

    val migrationChecklistItems = checklist(
      List(
        "migrationChecklist1",
        "migrationChecklist2",
        "migrationChecklist3",
        "migrationChecklist4",
        "migrationChecklist5",
        "migrationChecklist6"
      ),
      "plant-migration-check"
    )

    val container = HtmlExerciseContainer(fullInfo,
      List(
        HtmlContainerTitle(fullInfo, "PlantWorkshop/section5Title"),
        instructionPlaintext("PlantWorkshop/section5DownloadInfo"),
        instructionPlaintext("PlantWorkshop/section5Troubleshooting"),
        missingElementPlaceholder("missingArduinoExport"),
        instructionPlaintext("PlantWorkshop/migrationChecklistTitle")
      ) ++ testChecklistItems ++ migrationChecklistItems ++ List(
        instructionPlaintext("PlantWorkshop/legacyReference")
      )
    )

    WorkbookSection(fullInfo, "PlantWorkshop/section5Title", List(container))
  }
}