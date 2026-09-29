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
      author = User("Test Author", "test-author", "author@example.test"),
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

    assert(serialized.contains("serializedElements"))
  }
}
