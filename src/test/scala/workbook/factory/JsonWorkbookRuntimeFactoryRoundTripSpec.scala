package workbook.factory

import munit.FunSuite
import scala.scalajs.js
import workbook.model.info.AllWorkbookInfo

class JsonWorkbookRuntimeFactoryRoundTripSpec extends FunSuite {

  private val sampleJson: String =
    """
      |{
      |  "workbookMetadata": {
      |    "id": "roundtrip-workbook",
      |    "availableLanguages": ["en", "de"],
      |    "defaultLanguage": "en",
      |    "languageMapFiles": [],
      |    "titleMapId": "RoundTrip/WorkbookTitle",
      |    "estimatedInteractionDurationSeconds": {
      |      "exercise-1": 42.0
      |    }
      |  },
      |  "workbookContent": {
      |    "sections": [
      |      {
      |        "sectionId": "section-1",
      |        "sectionTitleMapId": "RoundTrip/Section1Title",
      |        "sectionsRequiredBefore": [],
      |        "sectionContent": [
      |          {
      |            "exerciseId": "exercise-container-1",
      |            "elements": [
      |              {
      |                "elementName": "HtmlBasicTextInteraction",
      |                "factoryArgs": {
      |                  "id": "exercise-1"
      |                }
      |              }
      |            ]
      |          }
      |        ]
      |      }
      |    ]
      |  }
      |}
      |""".stripMargin



  private val plantInteractionJson: String =
    """
      |{
      |  "workbookMetadata": {
      |    "id": "plant-json",
      |    "availableLanguages": ["en"],
      |    "defaultLanguage": "en",
      |    "languageMapFiles": [],
      |    "titleMapId": "PlantWorkshop/workbookTitle",
      |    "estimatedInteractionDurationSeconds": {}
      |  },
      |  "workbookContent": {
      |    "sections": [
      |      {
      |        "sectionId": "section-plant",
      |        "sectionTitleMapId": "PlantWorkshop/section1Title",
      |        "sectionsRequiredBefore": [],
      |        "sectionContent": [
      |          {
      |            "exerciseId": "exercise-plant-1",
      |            "elements": [
      |              {
      |                "elementName": "HtmlBasicCheckboxInteraction",
      |                "factoryArgs": {
      |                  "id": "checkbox-1",
      |                  "labelLanguageMapId": "PlantWorkshop/componentArduino"
      |                }
      |              },
      |              {
      |                "elementName": "HtmlReorderInteraction",
      |                "factoryArgs": {
      |                  "id": "reorder-1",
      |                  "elementsJson": "[\"line1\",\"line2\"]"
      |                }
      |              },
      |              {
      |                "elementName": "SlideDeckExercise",
      |                "factoryArgs": {
      |                  "id": "slide-1",
      |                  "titleMapId": "PlantWorkshop/wiringSlideshowTitle",
      |                  "slidesJson": "[{\"imagePath\":\"img/plantworkshop/step0.png\",\"textMapId\":\"PlantWorkshop/wiringSlideText1\",\"sourceMapId\":\"PlantWorkshop/wiringSlideSource\",\"descriptionMapId\":\"PlantWorkshop/wiringSlideDescription\"}]"
      |                }
      |              }
      |            ]
      |          }
      |        ]
      |      }
      |    ]
      |  }
      |}
      |""".stripMargin
  test("round trip json string -> workbook -> json string keeps JsonWorkbookFactory structure") {
    if (js.typeOf(js.Dynamic.global.document) == "undefined") {
      println("[INFO] Skipping round-trip runtime workbook test because DOM 'document' is unavailable.")
      assert(true)
    } else {
      val workbookInfo = AllWorkbookInfo()
      val runtimeFactory = JsonWorkbookRuntimeFactory.fromJson(workbookInfo, sampleJson)

      val workbook = runtimeFactory.createWorkbook
      val roundTripJsonOpt = JsonWorkbookRuntimeFactory.toJsonString(workbook)

      assert(roundTripJsonOpt.nonEmpty, "expected workbook to be registered for json round-trip export")

      val originalParsed = JsonWorkbookFactory.fromJson(sampleJson)
      val roundTripParsed = JsonWorkbookFactory.fromJson(roundTripJsonOpt.get)

      assertEquals(roundTripParsed, originalParsed)
    }
  }

  test("runtime factory builds plant workshop interactions from json") {
    if (js.typeOf(js.Dynamic.global.document) == "undefined") {
      println("[INFO] Skipping plant interaction runtime workbook test because DOM 'document' is unavailable.")
      assert(true)
    } else {
      val workbookInfo = AllWorkbookInfo()
      val runtimeFactory = JsonWorkbookRuntimeFactory.fromJson(workbookInfo, plantInteractionJson)

      val workbook = runtimeFactory.createWorkbook
      val roundTripJson = JsonWorkbookRuntimeFactory.toJsonString(workbook)

      assertEquals(workbook.sections.length, 1)
      assert(roundTripJson.nonEmpty, "expected workbook to remain registered for json round-trip export")
    }
  }
}
