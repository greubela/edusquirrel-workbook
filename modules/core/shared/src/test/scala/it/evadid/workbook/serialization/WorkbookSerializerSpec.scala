package it.evadid.workbook.serialization

import it.evadid.workbook.support.OfflineController
import it.evadid.core.datastructures.language.AppLanguage.English
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.user.User
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.interactions.basic.TextInteraction
import it.evadid.workbook.elements.display.TwoColumnPanel
import it.evadid.workbook.elements.structure.{ExerciseContainer, Workbook, WorkbookSection}
import it.evadid.workbook.elements.structure.WorkbookSection.WorkbookSectionMetadata
import it.evadid.workbook.sync.UpdateImportance
import it.evadid.workbook.abstractions.ExecutionContextInformation
import it.evadid.workbook.elements.structure.Workbook.WorkbookMetadata
import munit.FunSuite
import upickle.default.*
import upickle.core.TraceVisitor.TraceException

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

  private def populatedDefinition: Workbook = {
    val prerequisite = WorkbookSection("intro", WorkbookSectionMetadata(LanguageMapContentId("section/intro")),
      Seq(TextInteraction("intro-answer")))
    val panel = TwoColumnPanel("panel", TextInteraction(" left/\"quoted\"\n学校 "), TextInteraction("right"))
    val exercise = ExerciseContainer("exercise", LanguageMapContentId("exercise/title"), Seq(panel))
    val section = WorkbookSection("main", WorkbookSectionMetadata(LanguageMapContentId("section/main"),
      Seq(prerequisite), Seq(prerequisite)), Seq(exercise))
    // The prerequisite is not a root child: the self-contained graph must include it separately.
    workbook.copy(sections = Seq(section))
  }

  test("uPickle Workbook String round trips include nested structure and out-of-tree prerequisites") {
    val original = populatedDefinition
    val json: String = write[Workbook](original)
    val restored: Workbook = read[Workbook](json)
    assertEquals(restored, original)
    assertEquals(restored.metadata, original.metadata)
    assertEquals(restored.sections.head.metadata.sectionsRequiredBefore.head, original.sections.head.metadata.sectionsRequiredBefore.head)
    assert(restored.sections.head.metadata.sectionsRequiredBefore.head eq restored.sections.head.metadata.sectionsRecommendedBefore.head)
    assertEquals(restored.allContainedInteractionsById.keySet, original.allContainedInteractionsById.keySet)
    assertEquals(read[WorkbookElement](json), original)
    assertEquals(read[Workbook](write[WorkbookElement](original)), original)
    val repeated = (1 to 3).foldLeft(restored)((value, _) => read[Workbook](write[Workbook](value)))
    assertEquals(repeated, original)
  }

  test("a saved workbook lists every element exactly once") {
    val original = populatedDefinition
    val json = ujson.read(write[Workbook](original))
    val ids = json("elements").arr.map(_("elementId").str).toList
    assertEquals(ids.sorted, (original +: original.allChildrenFullSubtree :+ original.sections.head.metadata.sectionsRequiredBefore.head
      :++ original.sections.head.metadata.sectionsRequiredBefore.head.allChildrenFullSubtree).map(_.elementId).distinct.sorted)
    assert(!json.render().contains("serializedElements"), "no element embeds another's serialization")
  }

  test("uPickle Workbook MessagePack round trips preserve the same self-contained graph") {
    val original = populatedDefinition
    val restored = readBinary[Workbook](writeBinary[Workbook](original))
    assertEquals(restored, original)
    assert(restored.sections.head.metadata.sectionsRequiredBefore.head eq restored.sections.head.metadata.sectionsRecommendedBefore.head)
  }

  test("Workbook definition round trips omit live learner history") {
    val text = TextInteraction("live-answer")
    val section = WorkbookSection("live-section", WorkbookSectionMetadata(LanguageMapContentId("section/live")), Seq(text))
    val original = workbook.copy(sections = Seq(section))
    val answers = OfflineController.of(text)
    answers.setStateFromUserInteraction(text, "learner answer", UpdateImportance.MAJOR)
    assertEquals(answers.currentValue(text), "learner answer")
    val json = write[Workbook](original)
    assert(!json.contains("learner answer"))
    val restored = read[Workbook](json)
    assertEquals(restored, original)
  }

  test("the Workbook reader rejects a valid section graph with the wrong root kind") {
    val section = populatedDefinition.sections.head
    val error = intercept[TraceException](read[Workbook](write[WorkbookSection](section)))
    assert(error.getCause.isInstanceOf[IllegalArgumentException])
    assertEquals(error.getCause.getMessage, "Unexpected workbook element: WorkbookSection")
  }

  test("populated workbooks round-trip repeatedly with native metadata and registry JSON") {
    import it.evadid.workbook.interactions.programming.programmingExerciseRegular.ProgrammingExercise
    import it.evadid.workbook.interactions.programming.state.snap.ProgrammingEditorPalette
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
      val current = (1 to 3).foldLeft[WorkbookElement](populated)((value, _) =>
        serializer.deserialize(serializer.serialize(value)))
      assertEquals(current, populated)
    }
  }

  test("reference ids preserve slashes, quotes, newlines and significant whitespace") {
    val text = TextInteraction(" text/\"quoted\"\n ")
    val section = WorkbookSection(" section/one ", WorkbookSectionMetadata(LanguageMapContentId("section/title")), List(text))
    val populated = workbook.copy(sections = List(section))
    for (serializer <- List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike)) {
      assertEquals(serializer.deserialize(serializer.serialize(populated)), populated)
    }
  }


  test("registry round trip resolves nonempty sections and prerequisites regardless of input order") {
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
    import it.evadid.workbook.serialization.{WorkbookElementSerializable, WorkbookElementReference}
    val root = workbook.toSerialized
      .withElementsAddedAs("sections", List(WorkbookElementReference("missing", Some("WorkbookSection"))))
      .withElementsAddedAs("serializedElements", List(TextInteraction("unrelated").toSerialized))
    val error = intercept[it.evadid.distribution.command.SerializedException](WorkbookElementFactory.parse(root))
    assert(error.getMessage.contains("no progress"))
  }



  test("workbooks discover interactions inside sections, exercise containers and display wrappers") {
    import it.evadid.workbook.elements.display.LabeledWorkbookElement
    import it.evadid.workbook.elements.display.LabeledWorkbookElement.{GoalLabel, WorkbookLabel}
    val answer = TextInteraction("saved-answer")
    val wrapped = LabeledWorkbookElement("label", answer, WorkbookLabel(LanguageMapContentId("serializer/goal"), GoalLabel))
    val container = ExerciseContainer("exercise", LanguageMapContentId("serializer/exercise"), List(wrapped))
    val section = WorkbookSection("section", WorkbookSectionMetadata(LanguageMapContentId("serializer/section")), List(container))
    val populated = workbook.copy(sections = List(section))
    assertEquals(populated.allInteractionsInThisInstance.map(_.elementId), List(answer.elementId))
    assertEquals(populated.allContainedInteractionsById.keySet, Set(answer.elementId))
    assert(populated.allContainedInteractionsById(answer.elementId) eq answer)
  }

}
