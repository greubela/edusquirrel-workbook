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

  test("constructor-like serializer round-trips a workbook and its registry") {
    val serialized = WorkbookElementFactory.serializerConstructorLike.serialize(workbook)
    assertEquals(WorkbookElementFactory.serializerConstructorLike.deserialize(serialized), workbook)
  }

  test("populated workbooks round-trip repeatedly with native metadata and registry JSON") {
    import it.evadid.workbook.elements.structureElements.WorkbookSection
    import WorkbookSection.WorkbookSectionMetadata
    import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
    import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingExercise
    import it.evadid.workbook.elements.interactionElements.programming.state.snap.ProgrammingEditorPalette
    val first = WorkbookSection("first", WorkbookSectionMetadata(LanguageMapContentId("section/first")), List(TextInteraction("text")))
    val second = WorkbookSection("second", WorkbookSectionMetadata(LanguageMapContentId("section/second"), List(first), List(first)),
      List(ProgrammingExercise("programming", editorPalette = ProgrammingEditorPalette.Embroidery, referencePython = Some("print(\"hello\")\n"))))
    val populated = workbook.copy(sections = List(second, first))
    val json = ujson.read(WorkbookElementFactory.serializerRegularJsonWorkbook.serialize(populated))
    val registry = json("allConstructorFields")("serializedElements").arr
    assert(registry.forall(_.isInstanceOf[ujson.Obj]))
    val metadata = registry.find(_("elementId").str == "second").get("allConstructorFields")("metadata")
    assert(metadata.isInstanceOf[ujson.Obj])
    assertEquals(metadata("sectionTitle").str, "LangMapId(section/second)")
    assertEquals(metadata("requiredBefore").arr.size, 1)
    for (serializer <- List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike)) {
      var current: it.evadid.workbook.abstractions.WorkbookElement = populated
      for (_ <- 1 to 3) current = serializer.deserialize(serializer.serialize(current))
      assertEquals(current, populated)
    }
  }

  test("reference ids preserve slashes, quotes, newlines and significant whitespace") {
    import it.evadid.workbook.elements.structureElements.WorkbookSection
    import WorkbookSection.WorkbookSectionMetadata
    import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
    val text = TextInteraction(" text/\"quoted\"\n ")
    val section = WorkbookSection(" section/one ", WorkbookSectionMetadata(LanguageMapContentId("section/title")), List(text))
    val populated = workbook.copy(sections = List(section))
    for (serializer <- List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike)) {
      assertEquals(serializer.deserialize(serializer.serialize(populated)), populated)
    }
  }

  test("legacy string-encoded registry entries and section metadata remain readable") {
    import it.evadid.workbook.elements.structureElements.WorkbookSection
    import WorkbookSection.WorkbookSectionMetadata
    import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
    import upickle.default.*
    val first = WorkbookSection("first", WorkbookSectionMetadata(LanguageMapContentId("section/first")), List(TextInteraction("text")))
    val second = WorkbookSection("second", WorkbookSectionMetadata(LanguageMapContentId("section/second"), List(first), List(first)), Nil)
    val populated = workbook.copy(sections = List(second, first))
    val root = populated.toSerialized
    val registry = root.allConstructorFields("serializedElements").arr.toList.map { json =>
      val entry = read[it.evadid.workbook.jsonFactory.WorkbookElementSerializable](json)
      if (entry.elementType == "WorkbookSection") {
        val metadata = entry.allConstructorFields("metadata")
        val legacy = ujson.write(ujson.Obj.from(metadata.obj.toSeq.map { (key, value) => key -> ujson.Str(ujson.write(value)) }))
        write(entry.withElementAdded("metadata", legacy))
      } else write(entry)
    }
    assertEquals(WorkbookElementFactory.parse(root.withElementsAdded("serializedElements", registry)), populated)
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


  test("one registry accepts native JSON, legacy JSON strings and constructor strings in reverse dependency order") {
    import it.evadid.workbook.elements.structureElements.WorkbookSection
    import WorkbookSection.WorkbookSectionMetadata
    import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
    import it.evadid.workbook.jsonFactory.WorkbookElementSerializable
    import upickle.default.*
    val text = TextInteraction("shared/\"text\"")
    val first = WorkbookSection("first", WorkbookSectionMetadata(LanguageMapContentId("section/first")), List(text))
    val second = WorkbookSection("second", WorkbookSectionMetadata(LanguageMapContentId("section/second"), List(first), List(first)), List(text))
    val populated = workbook.copy(sections = List(second, first))
    val entries = populated.toSerialized.allConstructorFields("serializedElements").arr.toList
      .map(json => read[WorkbookElementSerializable](json)).map(entry => entry.elementId -> entry).toMap
    val mixed = ujson.Arr(
      writeJs(entries("second")),
      ujson.Str(write(entries("first"))),
      ujson.Str(WorkbookElementFactory.serializerConstructorLike.serialize(text)))
    val root = populated.toSerialized.withMapAdded(Map("serializedElements" -> mixed))
    val restored = WorkbookElementFactory.parse(root)
    assertEquals(restored, populated)
    for (serializer <- List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike)) {
      assertEquals(serializer.deserialize(serializer.serialize(restored)), populated)
    }
  }

}
