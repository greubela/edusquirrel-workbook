package it.evadid.homepage.workbook.content

import com.raquo.laminar.api.L.{*, given}
import it.evadid.core.datastructures.file.FileDescription
import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.homepage.control.info.FullInfo
import it.evadid.homepage.workbook.legacy.htmlElements.interactions.HtmlReorderInteraction
import it.evadid.homepage.workbook.legacy.plantworkshop.helpers.*
import it.evadid.workbook.model.abstractions.WorkbookElement
import it.evadid.workbook.model.elements.*
import it.evadid.workbook.model.elements.LabeledInstructionElement.*
import it.evadid.workbook.model.interaction.basic.LabeledCheckboxInteraction
import it.evadid.workbook.model.interaction.plugins.reorderExercise.ReorderInteraction
import it.evadid.workbook.model.interaction.plugins.slideshow.{Slideshow, SlideshowPanel}

import scala.concurrent.ExecutionContext

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

  private def missingElementPlaceholder(contextKey: String): WorkbookElement = instructionPlaintext(s"PlantWorkshop/$contextKey")

  private lazy val motivationSection: WorkbookSection = {
    val intro = container("PlantWorkshop/section0Title",
      List(
        //HtmlContainerTitle(fullInfo, "PlantWorkshop/section0Title"),
        instructionMarkdown("PlantWorkshop/section0IntroMarkdown"),
        instructionMarkdown("PlantWorkshop/section0SafetyMarkdown")
      ))

    section("section0", "PlantWorkshop/section0Title", List(intro))
  }

  private lazy val wiringSlideshow: Slideshow = {
    val panels = (1 to 11).toList.map { i =>
      // FileDescription.relativeToResourceFolder(s"img/plantworkshop/schaltkreis/Plant conv $i.png"),
      val imageId = LanguageMapContentId(s"PlantWorkshop/wiringSlideshowImage$i")
      if (i == 3 || i == 4 || i == 8) {
        SlideshowPanel.TwoColumnImagePanel(
          imageId,
          LanguageMapContentId("PlantWorkshop/LLabel"),
          LanguageMapContentId("PlantWorkshop/RLabel"),
          LanguageMapContentId(s"PlantWorkshop/wiringSlideTextL${i}"),
          LanguageMapContentId(s"PlantWorkshop/wiringSlideTextR${i}")
        )
      } else if (i == 5) {
        SlideshowPanel.ImageSlide(imageId,
          LanguageMapContentId(s"PlantWorkshop/wiringSlideText${i}"),
          LanguageMapContentId(s"PlantWorkshop/wiringSlideCurrentStatus")
        )
      }
      else {
        SlideshowPanel.ImageSlide(imageId,
          LanguageMapContentId(s"PlantWorkshop/wiringSlideText${i}"),
          LanguageMapContentId(s"PlantWorkshop/wiringSlideHelp")
        )
      }
    }

    Slideshow("plant-wiring-slideshow", panels)
  }
  /*
      private def createWiringSlideshow(): SlideDeckExercise = {
        val slidePanelsWiringSlideshow = (1 to 11).toList.map { i =>
          if (i == 3 || i == 4 || i == 8) {
            SlideshowPanel.TwoColumnImagePanel(
              s"PlantWorkshop/wiringSlideshowPanel$i",)

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
      }*/

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

    val container1 = container("PlantWorkshop/section1Title", List(
      instructionPlaintext("PlantWorkshop/section1ChecklistIntro"),
      instructionPlaintext("PlantWorkshop/section1WiringHint")
    ) ++ componentChecklist
    )

    val container2 = container("PlantWorkshop/section1Subtitle1", List(
      instructionLabeledPair("PlantWorkshop/safetyTitle", "PlantWorkshop/section1SafetyText", SafetyLabel),
      wiringSlideshow
    ))

    section("section1", "PlantWorkshop/section1Title", List(container1, container2))

  }

  private lazy val pumpControlSection: WorkbookSection = {
    /*
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

    // missing after Ex 1:
      HtmlExerciseContainer(fullInfo, List(
        pseudoElement(toggleArea)
      ))
    */

    val checklistItems: List[WorkbookElement] = checklist(
      List("pumpDone1", "pumpDone2", "pumpDone3"),
      "plant-pump-done"
    )

    val ex1 = container("PlantWorkshop/section2Title", List(
      instructionLabeledPair("PlantWorkshop/goalTitle", "PlantWorkshop/section2GoalText", GoalLabel),
      instructionLabeledPair("PlantWorkshop/instructionTitle", "PlantWorkshop/section2InstructionText", TaskLabel),
      instructionLabeledPair("PlantWorkshop/hintTitle", "PlantWorkshop/section2HintText", HintLabel)
    ))

    val ex3 = container("PlantWorkshop/section2Title", List(
      instructionPlaintext("PlantWorkshop/missingElements"),
      instructionPlaintext("PlantWorkshop/section2AdvancedHint"),
    ) ++ checklistItems)


    section("section2", "PlantWorkshop/section2Title", List(ex1, ex3))
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
    ), AppLanguage.C)

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
    /*
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
    )*/

    val ex1 = container("PlantWorkshop/section3Title", List(
      instructionLabeledPair("PlantWorkshop/goalTitle", "PlantWorkshop/section3GoalText", GoalLabel),
      instructionLabeledPair("PlantWorkshop/instructionTitle", "PlantWorkshop/section3InstructionText", TaskLabel),
      instructionLabeledPair("PlantWorkshop/hintTitle", "PlantWorkshop/section3HintText", HintLabel)
    ))

    /*
    missing Element
     HtmlExerciseContainer(fullInfo, List(
        pseudoElement(toggleArea)
      ))
     */
    val ex3 = container("PlantWorkshop/section3AdvancedHint", checklistItems)

    section("section3", "PlantWorkshop/section3Title", List(ex1, ex3))
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
    ), AppLanguage.C)
    

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
/*
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
*/
    val ex1 = container("PlantWorkshop/section4Title", List(
      instructionLabeledPair("PlantWorkshop/goalTitle", "PlantWorkshop/section4GoalText", GoalLabel),
      instructionLabeledPair("PlantWorkshop/instructionTitle", "PlantWorkshop/section4InstructionText", TaskLabel),
      instructionLabeledPair("PlantWorkshop/hintTitle", "PlantWorkshop/section4HintText", HintLabel)
    ))

    /*
    todo: Missing element

      HtmlExerciseContainer(fullInfo, List(
        pseudoElement(toggleArea)
      )),

     */
    val ex3 = container("PlantWorkshop/section4AdvancedHint", checklistItems)

    section("section4", "PlantWorkshop/section4Title", List(ex1, ex3))
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

    val ex1 = container("PlantWorkshop/section5Title",
      List(
        instructionPlaintext("PlantWorkshop/section5DownloadInfo"),
        instructionPlaintext("PlantWorkshop/section5Troubleshooting"),
        missingElementPlaceholder("missingArduinoExport"),
        instructionPlaintext("PlantWorkshop/migrationChecklistTitle")
      ) ++ testChecklistItems ++ migrationChecklistItems ++ List(
        instructionPlaintext("PlantWorkshop/legacyReference")
      ))

    section("section5", "PlantWorkshop/section5Title", List(ex1))
  }


}
