package it.evadid.workbook.serialization

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.workbook.interactions.programming.programmingExerciseTurtle.MaxCommandUsage
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.{InteractionFormat, LangMapContentIdType, RoleInWorkbook, TypeOfTextDisplay, WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.display.{CollapsibleInstructionElement, DisplayLangMapContent, WorkbookSubmissionElement}
import it.evadid.workbook.interactions.basic.{LabeledCheckboxInteraction, LabeledNumberInteraction, MessagingInteraction, TextInteraction}
import it.evadid.workbook.interactions.basic.LabeledNumberInteraction.NumberType
import it.evadid.workbook.interactions.codeTaskToggle.{AdvancedCodeRequirement, CodeTaskToggleInteraction, SketchDownloadInteraction}
import it.evadid.workbook.interactions.gpt.{GptInteractionElement, TextInputWithScaffoldingAndGrading}
import it.evadid.workbook.interactions.reorderExercise.ReorderInteraction
import it.evadid.workbook.interactions.matching.{LikertScaleInteraction, MatchingInteraction}
import it.evadid.workbook.interactions.sorting.sortingExercise.{SortingInteraction, SortingItem}
import it.evadid.workbook.interactions.sorting.sortingReasonExercise.{SortingReasonInteraction, SortingReasonItem}
import upickle.default.*
import it.evadid.workbook.interactions.choice.WorkbookContentChoiceInteraction
import WorkbookContentChoiceInteraction.*
import it.evadid.core.datastructures.language.WorkbookContent
import it.evadid.workbook.abstractions.grading.GraderRunningBehavior
import it.evadid.workbook.interactions.neuron.*
import it.evadid.workbook.interactions.table.*
import it.evadid.workbook.interactions.pixel.*
import it.evadid.workbook.interactions.pixel.*
import it.evadid.workbook.interactions.blockchain.*
import it.evadid.workbook.interactions.programming.programmingExerciseRegular.{ProgrammingExercise, ProgrammingExerciseFullJava, SortListExercise, SortingMethod}
import it.evadid.workbook.interactions.programming.programmingExerciseTurtle.{ProgramStructureGrader, ProgramStructureRequirement}
import it.evadid.workbook.interactions.programming.state.{ProgrammingState, ProgrammingStateRaw}
import it.evadid.workbook.interactions.programming.programmingExerciseTurtle.{TurtleDrawingPolicy, TurtleGraphic}
import it.evadid.workbook.interactions.text.*
import it.evadid.workbook.interactions.sql.{SqlCommandExercise, SqlDatabaseConfig}
import it.evadid.distribution.command.SerializedException
import munit.FunSuite

class WorkbookElementSerializableRoundTripSpec extends FunSuite {
  import it.evadid.workbook.elements.display.{WorkbookImageElement, LabeledWorkbookElement}
  import LabeledWorkbookElement.{WorkbookLabel, HintLabel}
  import it.evadid.workbook.elements.structure.{Workbook, WorkbookSection, ExerciseContainer}
  import it.evadid.workbook.interactions.slideshow.Slideshow
  import it.evadid.workbook.interactions.programming.programmingExerciseTurtle.*
  import it.evadid.workbook.interactions.programming.state.snap.ProgrammingEditorPalette
  import it.evadid.workbook.interactions.emailSimulator.*
  import it.evadid.workbook.interactions.qr.*
  import it.evadid.workbook.interactions.qr.QrCode
  import it.evadid.vm.test.SampleBeTest
  import it.evadid.vm.conversion.dependencies.{ProgrammingEnvironment, ProgrammingLibrary}

  private def content(id: String) = LanguageMapContentId(id)
  private val lineGraphic = TurtleGraphic.TurtleGraphicSvgString("M0,0 L10,0")
  private val twoForwards = MaxCommandUsage(occurrencesInCode = Map("forward" -> 2))
  private val turtleConfig = RecreateTurtleDidacticConfig.defaultDidacticConfig.copy(maxCommandUsage = twoForwards)

  private val elements: List[WorkbookElement] = {
    val reorder = ReorderInteraction.ReorderCodeInteraction(
      "reorder-1", List("move(10)", "turn(90)"), Python, seed = 7,
      hints = List(content("hint/one")), orderConstraints = List(0 -> 1)
    )
    val image = WorkbookImageElement.LanguageMapBasedWorkbookImageElement("image", content("image/source"),
      TypeOfTextDisplay.URL_RELATIVE_TO_WORKBOOK_RESOURCES, Some(content("image/description")),
      Some(it.evadid.core.datastructures.file.CopyrightInfo.fromAi))
    val input = TextInteraction("gpt-input")
    val section = WorkbookSection("section", WorkbookSection.WorkbookSectionMetadata(content("section/title")), List(input))
    val workbook = Workbook("workbook", Workbook.WorkbookMetadata(Set.empty, Set.empty, content("workbook/title"), List(it.evadid.core.datastructures.language.AppLanguage.English)), List(section))
    val special = " Quotes: \"hello\"; backslash: \\; newline:\nGrüße )({} "
    val inbox = InboxState.withMails(List(Mail("mail", "sender@example.test", special, special, special, MailFolder.Inbox, Some(special), "2026-10-08", expectedFolder = Some(MailFolder.Archive))))
    List(
      it.evadid.workbook.interactions.compression.CompressionExperimentInteraction("compression", content("compression/title"),
        it.evadid.workbook.interactions.compression.RunLengthText(special)),
      SqlCommandExercise("sql", SqlDatabaseConfig("school_exercises"), s"SELECT '$special';"),
      it.evadid.workbook.interactions.plot.CoordinatePlotInteraction("plot", content("plot/title"),
        content("plot/x"), content("plot/y"), it.evadid.workbook.interactions.plot.PlotAxis(0, 3, 0.5), it.evadid.workbook.interactions.plot.PlotAxis(0, 30, 10)),
      WorkbookContentChoiceInteraction("choice", ChoiceConfig(content("choice/prompt"), Some(true), GraderRunningBehavior.RUN_ALWAYS), Seq("choice/a", "choice/b").map(key => ChoiceOptionWorkbookContent(WorkbookContent(content(key)), None))),
      ThresholdNeuronInteraction("neuron", List(content("neuron/input")),
        List(NeuronExample(content("neuron/row"), List(1), true)), NeuronParameters(List(1), 1)),
      AnswerTableInteraction("table", content("table/title"), List(content("table/row")),
        List(content("table/a"), content("table/b")), List(List(
          FixedTableCell(content("table/given")), EditableTableCell(Some(List("0", "1")), List("0", "1"))))),
      BinaryPixelInteraction("pixels", content("pixels/title"), BinaryPixelImage.blank(1, 2),
        Some(BinaryPixelImage.fromRows(List("01"))),
        List(PixelThresholdProbe(content("pixels/probe"), List(PixelPosition(0, 1)), 1)),
        List(PixelPreset(content("pixels/preset"), BinaryPixelImage.fromRows(List("10"))))),
      SquareMiddleHashInteraction("hash", content("hash/title"), FindHashPreimage("22"), SquareMiddleHashAnswer("65")),
      Sha256Interaction("sha256", content("hash/sha256"), FindSha256Prefix(2), Sha256Answer("286")),
      it.evadid.workbook.interactions.evacuation.EvacuationSimulationInteraction("evacuation-simulation"),
      it.evadid.workbook.interactions.evacuation.EvacuationConstructFloorInteraction("evacuation"),
      UnicodeComparisonInteraction("unicode", content("text/unicode"), UnicodeComparisonAnswer("paypal.com", "payраl.com")),
      BlockchainInteraction("chain", content("hash/chain"), it.evadid.workbook.interactions.blockchain.TeachingChain(
        List(it.evadid.workbook.interactions.blockchain.TeachingBlock("Anna → Lukas: 4 HP")))),
      workbook, section,
      ExerciseContainer("container", content("container/title"), List(input)),
      LabeledWorkbookElement("labeled", input, WorkbookLabel(content("label/hint"), HintLabel)),
      it.evadid.workbook.elements.display.TwoColumnPanel("columns", image, input),
      it.evadid.workbook.elements.structure.ExerciseGroup("group", List(image, input)),
      image, Slideshow("slideshow", List(image)),
      GptInteractionElement("gpt", input, content("exercise/text"), List(content("hint/text")), List(content("criterion/text"))),
      TurtleStitchExploreProjectElement("turtle-explore", special),
      TurtleStitchRecreateShapeInteractionLegacy("turtle-legacy", special),
      TurtleRecreateShapeInteraction("turtle-shape", ProgrammingStateRaw(Python, "forward(10)"), lineGraphic, customExerciseConfig = Some(RecreateTurtleDidacticConfig.defaultForPalette(ProgrammingEditorPalette.Embroidery).copy(maxCommandUsage = twoForwards)),
        environment = ProgrammingEnvironment.of(ProgrammingLibrary.Turtle)),
      ProgrammingExercise("programming", Some(SampleBeTest("assert True")), ProgrammingEditorPalette.Embroidery, Some(special),
        ProgrammingEnvironment.of(ProgrammingLibrary.Turtle, ProgrammingLibrary.Web)),
      ProgrammingExerciseFullJava("java", Some(SampleBeTest("assert False")), environment = ProgrammingEnvironment.of(ProgrammingLibrary.Sudoku)),
      SortListExercise("sort", ProgrammingStateRaw(Python, "def sortiere():\n    pass"), "sortiere", Seq(3, 1, 2), Seq(Seq(2, 1), Seq()),
        SortingMethod.BubbleSort, Seq(Python), Seq(ProgramStructureGrader(ProgramStructureRequirement.UsesLoop, "sort-loop", content("test/loop"))),
        Some(content("exercise/sort")), ProgrammingEnvironment.of(ProgrammingLibrary.NumberList)),
      MailEditor("mail-editor", inbox, special, allowCompose = false),
      MailInteraction("mail-interaction", inbox, special, allowCompose = false),
      CreateQrCodeInteraction("qr", QrCodeRequirements(minBytes = 2, maxBytes = Some(200), requiredMask = Some(3)), QrCode.fromText(special)),
      TextInteraction("text-1"),
      it.evadid.workbook.interactions.gpt.TextInputWithScaffoldingAndGrading("text-feedback",
        content("text/task"), Some(content("text/solution")), Some(TextInputWithScaffoldingAndGrading.ContentForLlmInteraction(content("text/hint"), Map.empty)), List(TextInputWithScaffoldingAndGrading.ContentForLlmInteraction(content("text/criterion"), Map.empty))),
      MessagingInteraction("messaging-1"),
      LabeledCheckboxInteraction("checkbox-1", content("label/checkbox")),
      LabeledNumberInteraction("number-1", content("label/number"), NumberType.FractionLike, "1.5", BigDecimal("0.125")),
      SketchDownloadInteraction("download-1", content("label/download"), special, special, "reorder-1"),
      DisplayLangMapContent("display-1", content("instruction/body"), LangMapContentIdType(RoleInWorkbook.EXERCISE_DESCRIPTION, TypeOfTextDisplay.MARKDOWN)),
      CollapsibleInstructionElement("collapsible-1", content("hint/title"), content("hint/body"), initiallyCollapsed = false),
      WorkbookSubmissionElement("submission-1"),
      reorder,
      ReorderInteraction.ReorderMapIdInteraction("reorder-map-1", List(content("line/one"), content("line/two")), 9),
      MatchingInteraction("matching-1", Some(content("matching/task")), List(content("left/one"), content("left/two")),
        List(content("right/one"), content("right/two"), content("right/three")), Some(List(2, 0))),
      MatchingInteraction("matching-ungraded", None, List(content("left/one")), List(content("right/one")), None),
      LikertScaleInteraction("likert-1", Some(content("survey/intro")), List(content("survey/one"), content("survey/two")),
        List(content("scale/no"), content("scale/neutral"), content("scale/yes"))),
      CodeTaskToggleInteraction("toggle-1", reorder, content("title/editor"), "print('hello')", List(AdvancedCodeRequirement("print", content("hint/print"))), content("success/message")),
      SortingInteraction("sorting-1", List(content("field/one")), List(SortingItem(content("item/one"), 0, content("feedback/wrong"))), content("button/open")),
      SortingReasonInteraction("sorting-reason-1", List(content("field/one")), List(SortingReasonItem(content("item/one"), 0, content("feedback/wrong"), content("reason/prompt"))), content("button/open"))
    )
  }

  elements.foreach { element =>
    for ((format, encode, decode) <- List(
      ("JSON", (e: WorkbookElement) => WorkbookElementFactory.serializerRefBasedJson.serialize(e), (s: String) => read[WorkbookElementSerializable](s)),
      ("constructor", (e: WorkbookElement) => e.toStringConstructorLike, WorkbookElementSerializable.fromStringConstructorLike)
    )) {
      test(s"${element.getClass.getSimpleName} round-trips through $format") {
        // The single-element text formats embed the elements a workbook references.
        val original = element.associatedFactory.toSelfContainedElementUnsafe(element)
        val restored = decode(encode(element))
        assertEquals(restored.elementId, element.elementId)
        // Constructor output also includes elementId as a field for factories whose JSON omits it.
        assertEquals(restored.allConstructorFields - "elementId", original.allConstructorFields - "elementId")
        val references = element match {
          case gpt: GptInteractionElement => List(gpt.underlyingTextInteraction)
          case _ => Nil
        }
        val dependencies = (element.allChildrenFullSubtree ++ references).map(_.toSerialized)
        val roundTripped = WorkbookElementFactory.parseAll(restored +: dependencies).head
        assertEquals(roundTripped, element)
      }
    }
  }

  private def assertStateRoundTrip[F <: InteractionFormat](element: WorkbookInteractionElement[F]): Unit = {
    val serializer = InteractionFormat.codecsFor(element.interactionFormat).stateSerializer
    assertEquals(serializer.deserialize(serializer.serialize(element.defaultValue)), element.defaultValue)
  }

  elements.collect { case element: WorkbookInteractionElement[? <: InteractionFormat] => element }.foreach { element =>
    test(s"${element.getClass.getSimpleName} interaction state round-trips") {
      assertStateRoundTrip(element)
    }
  }

  test("old number elements without diff retain the default step") {
    val element = LabeledNumberInteraction("number", content("number/label"), NumberType.IntegerLike)
    val serialized = element.toSerialized
    assertEquals(WorkbookElementFactory.parse(serialized.copy(allConstructorFields = serialized.allConstructorFields - "diff")), element)
  }

  test("turtle definitions default to stroke grading without changing their saved source") {
    val source = ProgrammingStateRaw(Python, " \r\nforward(10)\n\t ")
    val original = TurtleRecreateShapeInteraction("turtle-default-policy", source, lineGraphic, customExerciseConfig = Some(turtleConfig))
    val serialized = original.toSerialized
    val legacy = serialized.copy(allConstructorFields = serialized.allConstructorFields - "comparisonPolicy")
    assert(!legacy.allConstructorFields.contains("comparisonPolicy"))
    val restored = WorkbookElementFactory.parse(legacy).asInstanceOf[TurtleRecreateShapeInteraction]
    assertEquals(restored.comparisonPolicy, TurtleDrawingPolicy.Strokes)
    assertEquals(restored.elementId, original.elementId)
    assertEquals(restored.initProgram, source)
    assertEquals(restored.desiredResult, original.desiredResult)
    assertEquals(restored.exerciseConfig.maxCommandUsage, original.exerciseConfig.maxCommandUsage)
    assertEquals(restored.interactionFormat.stateSerializer.serialize(source), original.interactionFormat.stateSerializer.serialize(source))
  }

  test("turtle comparison policies roundtrip through both definition formats without rewriting drafts") {
    val sources: List[ProgrammingState] = List(
      ProgrammingStateRaw(Python, " \r\ndef draw(\n\t "),
      ProgrammingStateRaw(Java, " \r\npublic class Drawing {\n  static void draw(\t "),
      ProgrammingStateRaw(SnapLanguage, "<project name=\"Grüße\">\r\n  <notes> \\ </notes>\n</project>\t "))
    for {
      policy <- TurtleDrawingPolicy.values
      source <- sources
    } do {
      val original = TurtleRecreateShapeInteraction(s"turtle-policy-$policy", source, lineGraphic,
        customExerciseConfig = Some(turtleConfig), comparisonPolicy = policy)
      val explicit = original.toSerialized.withElementAddedAs("comparisonPolicy", policy)
      val restoredDefinitions = List(
        WorkbookElementFactory.parse(explicit),
        WorkbookElementFactory.serializerRefBasedJson.deserialize(WorkbookElementFactory.serializerRefBasedJson.serialize(original)),
        WorkbookElementFactory.serializerConstructorLike.deserialize(original.toStringConstructorLike))
      restoredDefinitions.foreach { element =>
        val restored = element.asInstanceOf[TurtleRecreateShapeInteraction]
        assertEquals(restored, original)
        assertEquals(restored.elementId, original.elementId)
        assertEquals(restored.comparisonPolicy, policy)
        assertEquals(restored.defaultValue, source)
        val saved = original.interactionFormat.stateSerializer.serialize(source)
        assertEquals(restored.interactionFormat.stateSerializer.serialize(source), saved)
        assertEquals(restored.interactionFormat.stateSerializer.deserialize(saved), source)
        assertEquals(restored.interactionFormat.stateSerializer.deserialize(saved).fingerprint(),
          source.fingerprint())
      }
    }
  }

  test("invalid turtle definition comparison policies do not fall back to stroke grading") {
    val original = TurtleRecreateShapeInteraction("turtle-invalid-policy", ProgrammingStateRaw(Python, "forward(10)"), lineGraphic)
    List[ujson.Value](ujson.Str("Other"), ujson.Null, ujson.Num(1), ujson.Bool(true), ujson.Obj()).foreach { policy =>
      val invalid = original.toSerialized.withMapAdded(Map("comparisonPolicy" -> policy))
      val error = intercept[SerializedException](WorkbookElementFactory.parse(invalid))
      assert(error.msg.contains("turtle-invalid-policy"))
      assert(error.msg.contains("comparisonPolicy"))
      assert(error.msg.contains("because:"))
      assert(error.cause.nonEmpty)
    }
  }

  test("old image elements without description or copyright retain absent metadata") {
    val image = WorkbookImageElement("old-image", content("image/source"), TypeOfTextDisplay.URL_RELATIVE_TO_WORKBOOK_RESOURCES)
    val serialized = image.toSerialized
    assertEquals(WorkbookElementFactory.parse(serialized.copy(allConstructorFields = serialized.allConstructorFields -- List("description", "copyright"))), image)
  }

  test("constructor format preserves element ids without unquoting or trimming") {
    for (id <- List("", " id ", "\"quoted\"", "line\nwith\ttabs", "Grüße )({} \\")) {
      val element = TextInteraction(id)
      assertEquals(WorkbookElementFactory.serializerConstructorLike.deserialize(element.toStringConstructorLike), element)
      assertEquals(WorkbookElementFactory.serializerRefBasedJson.deserialize(WorkbookElementFactory.serializerRefBasedJson.serialize(element)), element)
    }
  }

  test("every registered type has its own factory and a round-trip fixture") {
    // A factory registered under two names would lose one of them here.
    assertEquals(elements.map(WorkbookElementFactory.persistedTypeOf).toSet, WorkbookElementFactory.registeredElementTypes)
    assert(elements.forall(element => element.toSerialized.elementType == WorkbookElementFactory.persistedTypeOf(element)))
  }

  test("parsing fails when references form a cycle") {
    val cyclicElements = List("first", "second").map { id =>
      WorkbookElementSerializable(id, "CodeTaskToggleInteraction", Map())
        .withElementAddedAs("reorder", it.evadid.workbook.serialization.WorkbookElementReference(if (id == "first") "second" else "first", Some("ReorderCodeInteraction")))
    }

    val error = intercept[SerializedException](WorkbookElementFactory.parseAll(cyclicElements))
    assert(error.getMessage.contains("cyclic dependency"))
  }
}
