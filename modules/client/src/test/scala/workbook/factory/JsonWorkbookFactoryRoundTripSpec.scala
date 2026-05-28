package workbook.factory

import munit.FunSuite

class JsonWorkbookFactoryRoundTripSpec extends FunSuite {

  private val workbookJson: String =
    """
      |{
      |  "workbookMetadata": {
      |    "id": "roundtrip-workbook",
      |    "availableLanguages": ["en", "de"],
      |    "defaultLanguage": "en",
      |    "languageMapFiles": ["/languageMaps/TestWorkbook-en.json"],
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
      |            "level": 2,
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

  test("JsonWorkbookFactory roundtrip keeps semantic structure") {
    val parsed = JsonWorkbookFactory.fromJson(workbookJson)
    val roundTripped = JsonWorkbookFactory.fromJson(parsed.toJson(pretty = false))

    assertEquals(roundTripped, parsed)
  }

  test("JsonWorkbookFactory exposes nested workbook metadata and content") {
    val parsed = JsonWorkbookFactory.fromJson(workbookJson)

    assertEquals(parsed.workbookMetadata.id, "roundtrip-workbook")
    assertEquals(parsed.workbookMetadata.estimatedInteractionDurationSeconds("exercise-1"), 42.0)
    assertEquals(parsed.workbookContent.sections.head.sectionContent.head.level, 2)
    assertEquals(parsed.workbookContent.sections.head.sectionContent.head.elements.head.elementName, "HtmlBasicTextInteraction")
  }
}
