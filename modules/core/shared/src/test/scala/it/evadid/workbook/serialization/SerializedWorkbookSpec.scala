package it.evadid.workbook.serialization

import it.evadid.core.datastructures.language.AppLanguage.English
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.model.abstractions.WorkbookElement
import it.evadid.workbook.model.elements.{LangMapContentBasedElement, Workbook, WorkbookSection}
import it.evadid.workbook.model.abstractions.{LangMapContentIdType, RoleInWorkbook, TypeOfTextContent}
import munit.FunSuite

class SerializedWorkbookSpec extends FunSuite {

  private val elementType = LangMapContentIdType(RoleInWorkbook.EXERCISE_DESCRIPTION, TypeOfTextContent.PLAINTEXT)

  private def textElement(id: String): WorkbookElement =
    LangMapContentBasedElement(LanguageMapContentId(id), elementType)

  test("export stores section bodies once in the top-level section registry") {
    val introElement = textElement("test/intro")
    val practiceElement = textElement("test/practice")
    val intro = WorkbookSection("intro", LanguageMapContentId("test/intro-title"), List(introElement))
    val practice = WorkbookSection(
      "practice",
      LanguageMapContentId("test/practice-title"),
      List(practiceElement),
      sectionsRequiredBefore = List(intro),
      sectionsRecommendedBefore = List(intro)
    )
    val workbook = Workbook("workbook", LanguageMapContentId("test/workbook-title"), List(practice), List(English))

    val elementRefs = Map(introElement -> ElementRef("element-intro"), practiceElement -> ElementRef("element-practice"))
    val serialized = SerializedWorkbook.fromWorkbook(workbook, elementRefs.apply)

    assertEquals(serialized.sectionOrder, List("practice"))
    assertEquals(serialized.sectionRegistry.keySet, Set("practice", "intro"))
    assertEquals(serialized.sectionRegistry("practice").sectionContentRefs, List(ElementRef("element-practice")))
    assertEquals(serialized.sectionRegistry("practice").sectionsRequiredBeforeIds, List("intro"))
    assertEquals(serialized.sectionRegistry("practice").sectionsRecommendedBeforeIds, List("intro"))
    assert(!serialized.toJson().contains("sectionsRequiredBefore\": [{"))
  }

  test("import resolves section dependency ids after constructing registry sections") {
    val introElement = textElement("test/import-intro")
    val practiceElement = textElement("test/import-practice")
    val serialized = SerializedWorkbook(
      workbookId = "workbook",
      workbookTitle = LanguageMapContentId("test/workbook-title"),
      sectionOrder = List("practice"),
      availableLanguages = List(English),
      sectionRegistry = Map(
        "intro" -> SerializedWorkbookSection(
          sectionId = "intro",
          sectionTitle = LanguageMapContentId("test/intro-title"),
          sectionContentRefs = List(ElementRef("element-intro")),
          sectionsRequiredBeforeIds = List.empty,
          sectionsRecommendedBeforeIds = List.empty
        ),
        "practice" -> SerializedWorkbookSection(
          sectionId = "practice",
          sectionTitle = LanguageMapContentId("test/practice-title"),
          sectionContentRefs = List(ElementRef("element-practice")),
          sectionsRequiredBeforeIds = List("intro"),
          sectionsRecommendedBeforeIds = List.empty
        )
      )
    )

    val workbook = serialized.toWorkbook(Map(ElementRef("element-intro") -> introElement, ElementRef("element-practice") -> practiceElement))

    assertEquals(workbook.sections.map(_.sectionId), List("practice"))
    assertEquals(workbook.sections.head.sectionContent, List(practiceElement))
    assertEquals(workbook.sections.head.sectionsRequiredBefore.map(_.sectionId), List("intro"))
    assertEquals(workbook.sections.head.sectionsRequiredBefore.head.sectionContent, List(introElement))
  }

  test("json round-trip preserves serialized section registry") {
    val serialized = SerializedWorkbook(
      workbookId = "workbook",
      workbookTitle = LanguageMapContentId("test/workbook-title"),
      sectionOrder = List("intro"),
      availableLanguages = List(English),
      sectionRegistry = Map(
        "intro" -> SerializedWorkbookSection(
          sectionId = "intro",
          sectionTitle = LanguageMapContentId("test/intro-title"),
          sectionContentRefs = List(ElementRef("element-intro")),
          sectionsRequiredBeforeIds = List.empty,
          sectionsRecommendedBeforeIds = List.empty
        )
      )
    )

    assertEquals(SerializedWorkbook.fromJson(serialized.toJson()), serialized)
  }
}
