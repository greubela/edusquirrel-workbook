package content

import com.raquo.laminar.api.L.{*, given}
import datastructures.web.file.FileDescription
import interactionPlugins.slideshow.{SlideDeckExercise, SlidePanel}
import workbook.htmlElements.basic.{HtmlContainerTitle, HtmlPlaintextInstructionElement, HtmlUnsafeHtmlInstructionElement}
import workbook.htmlElements.container.HtmlExerciseContainer
import workbook.htmlElements.interactions.{HtmlBasicCheckboxInteraction, HtmlReorderInteraction}
import workbook.model.{Workbook, WorkbookSection}
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.AllWorkbookInfo

import scala.concurrent.ExecutionContext

case class CreatePlantworkshopWorkbook(override val workbookInfo: AllWorkbookInfo) extends WorkbookFactory {

  private def textElement(languageMapId: String): HtmlPlaintextInstructionElement =
    HtmlPlaintextInstructionElement(
      workbookInfo,
      workbookInfo.stringSignalFromLanguageMapId(languageMapId)(ExecutionContext.global)
    )

  private def htmlElement(languageMapId: String): HtmlUnsafeHtmlInstructionElement =
    HtmlUnsafeHtmlInstructionElement(
      workbookInfo,
      workbookInfo.stringSignalFromLanguageMapId(languageMapId)(ExecutionContext.global)
    )

  private def checklist(keys: List[String], prefix: String): List[HtmlWorkbookElement] =
    keys.map { key =>
      HtmlBasicCheckboxInteraction(
        workbookInfo = workbookInfo,
        id = nextId(prefix),
        labelLanguageMapId = s"PlantWorkshop/$key"
      )
    }

  private def codeReorder(baseId: String, snippets: List[String]): HtmlReorderInteraction[String] =
    HtmlReorderInteraction[String](
      workbookInfo = workbookInfo,
      id = nextId(baseId),
      elements = snippets,
      elementRenderer = snippet => pre(code(snippet))
    )

  override def createWorkbook: Workbook = {
    val sections = List(
      createMotivationSection(),
      createComponentsSection(),
      createPumpControlSection(),
      createMoistureSection(),
      createCombinedSection(),
      createTestSection()
    )

    val workbook = Workbook(
      workbookInfo = workbookInfo,
      titleLanguageMapId = "PlantWorkshop/workbookTitle",
      sections = sections
    )

    sections.headOption.foreach(first => workbookInfo.updateConfig(_.copy(activeSection = Some(first))))
    workbook
  }

  private def missingElementPlaceholder(contextKey: String): HtmlWorkbookElement =
    HtmlPlaintextInstructionElement(
      workbookInfo,
      workbookInfo.stringSignalFromLanguageMapId(s"PlantWorkshop/$contextKey")(ExecutionContext.global)
    )

  private def createMotivationSection(): WorkbookSection = {
    val intro = HtmlExerciseContainer(workbookInfo, List(
      HtmlContainerTitle(workbookInfo, "PlantWorkshop/section0Title"),
      htmlElement("PlantWorkshop/section0IntroHtml"),
      htmlElement("PlantWorkshop/section0SafetyHtml")
    ))

    WorkbookSection(workbookInfo, "PlantWorkshop/section0Title", List(intro))
  }

  private def createWiringSlideshow(): SlideDeckExercise = {
    val slides = List(
      SlidePanel.imageSlide(
        FileDescription.relativeToResourceFolder("img/plantworkshop/step0.png"),
        textMapId = "PlantWorkshop/wiringSlideText1",
        sourceMapId = "PlantWorkshop/wiringSlideSource",
        descriptionMapId = "PlantWorkshop/wiringSlideDescription",
        workbookInfo = workbookInfo
      ),
      SlidePanel.imageSlide(
        FileDescription.relativeToResourceFolder("img/plantworkshop/step1.png"),
        textMapId = "PlantWorkshop/wiringSlideText2",
        sourceMapId = "PlantWorkshop/wiringSlideSource",
        descriptionMapId = "PlantWorkshop/wiringSlideDescription",
        workbookInfo = workbookInfo
      ),
      SlidePanel.imageSlide(
        FileDescription.relativeToResourceFolder("img/plantworkshop/step2.png"),
        textMapId = "PlantWorkshop/wiringSlideText3",
        sourceMapId = "PlantWorkshop/wiringSlideSource",
        descriptionMapId = "PlantWorkshop/wiringSlideDescription",
        workbookInfo = workbookInfo
      )
    )

    SlideDeckExercise(
      workbookInfo = workbookInfo,
      id = nextId("plant-wiring-slideshow"),
      titleMapId = "PlantWorkshop/wiringSlideshowTitle",
      slides = slides
    )
  }

  private def createComponentsSection(): WorkbookSection = {
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

    val container = HtmlExerciseContainer(workbookInfo,
      List(
        HtmlContainerTitle(workbookInfo, "PlantWorkshop/section1Title"),
        textElement("PlantWorkshop/section1ChecklistIntro"),
        textElement("PlantWorkshop/section1WiringHint")
      ) ++ componentChecklist ++ List(
        htmlElement("PlantWorkshop/section1PowerWarningHtml"),
        createWiringSlideshow(),
        textElement("PlantWorkshop/section1RelayInfo")
      )
    )

    WorkbookSection(workbookInfo, "PlantWorkshop/section1Title", List(container))
  }

  private def createPumpControlSection(): WorkbookSection = {
    val reorder = codeReorder("plant-pump-reorder", List(
      "digitalWrite(PUMP_PIN, LOW);",
      "delay(2000);",
      "digitalWrite(PUMP_PIN, HIGH);"
    ))

    val checklistItems = checklist(
      List("pumpDone1", "pumpDone2", "pumpDone3"),
      "plant-pump-done"
    )

    val container = HtmlExerciseContainer(workbookInfo, List(
      HtmlContainerTitle(workbookInfo, "PlantWorkshop/section2Title"),
      textElement("PlantWorkshop/section2Goal"),
      textElement("PlantWorkshop/section2Instruction"),
      textElement("PlantWorkshop/section2BeginnerHint"),
      reorder,
      textElement("PlantWorkshop/section2AdvancedHint"),
      missingElementPlaceholder("missingPumpInteraction")
    ) ++ checklistItems)

    WorkbookSection(workbookInfo, "PlantWorkshop/section2Title", List(container))
  }

  private def createMoistureSection(): WorkbookSection = {
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

    val checklistItems = checklist(
      List("moistureDone1", "moistureDone2", "moistureDone3"),
      "plant-moisture-done"
    )

    val container = HtmlExerciseContainer(workbookInfo, List(
      HtmlContainerTitle(workbookInfo, "PlantWorkshop/section3Title"),
      textElement("PlantWorkshop/section3Goal"),
      textElement("PlantWorkshop/section3Instruction"),
      textElement("PlantWorkshop/section3BeginnerHint"),
      reorder,
      textElement("PlantWorkshop/section3AdvancedHint"),
      missingElementPlaceholder("missingMoistureInteraction")
    ) ++ checklistItems)

    WorkbookSection(workbookInfo, "PlantWorkshop/section3Title", List(container))
  }

  private def createCombinedSection(): WorkbookSection = {
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

    val checklistItems = checklist(
      List("combinedDone1", "combinedDone2", "combinedDone3"),
      "plant-combined-done"
    )

    val container = HtmlExerciseContainer(workbookInfo, List(
      HtmlContainerTitle(workbookInfo, "PlantWorkshop/section4Title"),
      textElement("PlantWorkshop/section4Goal"),
      textElement("PlantWorkshop/section4Instruction"),
      textElement("PlantWorkshop/section4BeginnerHint"),
      reorder,
      textElement("PlantWorkshop/section4AdvancedHint"),
      missingElementPlaceholder("missingCombinedInteraction")
    ) ++ checklistItems)

    WorkbookSection(workbookInfo, "PlantWorkshop/section4Title", List(container))
  }

  private def createTestSection(): WorkbookSection = {
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

    val container = HtmlExerciseContainer(workbookInfo,
      List(
        HtmlContainerTitle(workbookInfo, "PlantWorkshop/section5Title"),
        textElement("PlantWorkshop/section5DownloadInfo"),
        textElement("PlantWorkshop/section5Troubleshooting"),
        missingElementPlaceholder("missingArduinoExport"),
        textElement("PlantWorkshop/migrationChecklistTitle")
      ) ++ testChecklistItems ++ migrationChecklistItems ++ List(
        textElement("PlantWorkshop/legacyReference")
      )
    )

    WorkbookSection(workbookInfo, "PlantWorkshop/section5Title", List(container))
  }
}
