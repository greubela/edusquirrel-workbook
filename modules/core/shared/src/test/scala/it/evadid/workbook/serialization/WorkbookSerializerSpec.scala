package it.evadid.workbook.serialization

import it.evadid.core.datastructures.language.AppLanguage.English
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.user.User
import it.evadid.workbook.elements.structureElements.Workbook
import it.evadid.workbook.elements.structureElements.Workbook.WorkbookMetadata
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import munit.FunSuite

class WorkbookSerializerSpec extends FunSuite {
  private val workbook = Workbook(
    elementId = "serializer-test",
    metadata = WorkbookMetadata(
      author = Set(User("Test Author", "test-author", "author@example.test")),
      contributors = Set.empty,
      workbookTitle = LanguageMapContentId("serializer/title"),
      availableLanguages = List(English)
    ),
    sections = List.empty
  )

  test("reference-based JSON serializer round-trips a workbook") {
    val serialized = WorkbookElementFactory.serializerRegularJsonWorkbook.serialize(workbook)
    val restored = WorkbookElementFactory.serializerRegularJsonWorkbook.deserialize(serialized)

    assertEquals(restored, workbook)
  }

  test("constructor-like serializer includes the embedded element registry") {
    val serialized = workbook.toStringConstructorLike

    assert(serialized.contains("serializedElements"), clue = serialized.take(200))
  }
  test("registry round trip resolves nonempty sections and prerequisites regardless of input order") {
    import it.evadid.workbook.elements.structureElements.WorkbookSection
    import WorkbookSection.WorkbookSectionMetadata
    import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
    val first = WorkbookSection("first", WorkbookSectionMetadata(LanguageMapContentId("section/first")), List(TextInteraction("text")))
    val second = WorkbookSection("second", WorkbookSectionMetadata(LanguageMapContentId("section/second"), List(first), List(first)), Nil)
    val populated = workbook.copy(sections = List(second, first))
    val serialized = WorkbookElementFactory.serializerRegularJsonWorkbook.serialize(populated)
    val restored = WorkbookElementFactory.serializerRegularJsonWorkbook.deserialize(serialized)
    assertEquals(restored, populated)
    assertEquals(restored.sections.head.metadata.sectionsRequiredBefore, List(restored.sections(1)))
    assertEquals(restored.sections.head.metadata.sectionsRecommendedBefore, List(restored.sections(1)))
  }

  test("embedded registries reject unresolved references instead of re-expanding forever") {
    import it.evadid.workbook.jsonFactory.{WorkbookElementSerializable, WorkbookElementReference}
    import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
    val root = workbook.toSerialized
      .withElementsAddedAs("sections", List(WorkbookElementReference("missing", Some("WorkbookSection"))))
      .withElementsAdded("serializedElements", List(upickle.default.write(TextInteraction("unrelated").toSerialized)))
    val error = intercept[it.evadid.distribution.command.SerializedException](WorkbookElementFactory.parse(root))
    assert(error.getMessage.contains("no progress"))
  }

}
