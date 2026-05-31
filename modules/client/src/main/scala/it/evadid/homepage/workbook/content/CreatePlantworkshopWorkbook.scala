package it.evadid.homepage.workbook.content

import com.raquo.laminar.api.L.{*, given}


import scala.concurrent.ExecutionContext

/*
case class CreatePlantworkshopWorkbook(override val fullInfo: FullInfo) extends WorkbookFactory {


  override val workbookId: String = "PlantWorkshop" // todo: Lang map should automatically prefix this


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

    Workbook(
      fullInfo = fullInfo,
      titleLanguageMapId = "PlantWorkshop/workbookTitle",
      sections = sections
    )
  }

  private def missingElementPlaceholder(contextKey: String): HtmlWorkbookElement = instructionPlaintext(s"PlantWorkshop/$contextKey")

  private def createMotivationSection(): WorkbookSection = {
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
        HtmlSlidePanel.imageSlideTwoColumns(
          FileFactory.relativeToResourceFolder(s"img/plantworkshop/schaltkreis/Plant conv $i.png"),
          leftLabel = s"PlantWorkshop/LLabel",
          rightLabel = s"PlantWorkshop/RLabel",
          leftBody = s"PlantWorkshop/wiringSlideTextL${i}",
          rightBody = s"PlantWorkshop/wiringSlideTextR${i}",
          fullInfo = fullInfo
        )
      } else if (i == 5) {
        HtmlSlidePanel.imageSlide(
          FileFactory.relativeToResourceFolder(s"img/plantworkshop/schaltkreis/Plant conv $i.png"),
          textMapId = s"PlantWorkshop/wiringSlideText${i}",
          descriptionMapId = s"PlantWorkshop/wiringSlideCurrentStatus",
          fullInfo = fullInfo
        )
      } else {
        HtmlSlidePanel.imageSlide(
          FileFactory.relativeToResourceFolder(s"img/plantworkshop/schaltkreis/Plant conv $i.png"),
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
          instructionMarkdown("PlantWorkshop/section1PowerWarningMarkdown"),
          createWiringSlideshow(),
          instructionPlaintext("PlantWorkshop/section1RelayInfo")
        )
      )
    )

    WorkbookSection(fullInfo, "PlantWorkshop/section1Title", container)
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

    val container = HtmlExerciseContainer(fullInfo, List(
      HtmlContainerTitle(fullInfo, "PlantWorkshop/section2Title"),
      instructionPlaintext("PlantWorkshop/section2Goal"),
      instructionPlaintext("PlantWorkshop/section2Instruction"),
      instructionPlaintext("PlantWorkshop/section2BeginnerHint"),
      reorder,
      instructionPlaintext("PlantWorkshop/section2AdvancedHint"),
      missingElementPlaceholder("missingPumpInteraction")
    ) ++ checklistItems)

    WorkbookSection(fullInfo, "PlantWorkshop/section2Title", List(container))
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

    val container = HtmlExerciseContainer(fullInfo, List(
      HtmlContainerTitle(fullInfo, "PlantWorkshop/section3Title"),
      instructionPlaintext("PlantWorkshop/section3Goal"),
      instructionPlaintext("PlantWorkshop/section3Instruction"),
      instructionPlaintext("PlantWorkshop/section3BeginnerHint"),
      reorder,
      instructionPlaintext("PlantWorkshop/section3AdvancedHint"),
      missingElementPlaceholder("missingMoistureInteraction")
    ) ++ checklistItems)

    WorkbookSection(fullInfo, "PlantWorkshop/section3Title", List(container))
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

    val container = HtmlExerciseContainer(fullInfo, List(
      HtmlContainerTitle(fullInfo, "PlantWorkshop/section4Title"),
      instructionPlaintext("PlantWorkshop/section4Goal"),
      instructionPlaintext("PlantWorkshop/section4Instruction"),
      instructionPlaintext("PlantWorkshop/section4BeginnerHint"),
      reorder,
      instructionPlaintext("PlantWorkshop/section4AdvancedHint"),
      missingElementPlaceholder("missingCombinedInteraction")
    ) ++ checklistItems)

    WorkbookSection(fullInfo, "PlantWorkshop/section4Title", List(container))
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
}*/