package it.evadid.workbook.serialization

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.MaxCommandUsage

import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.{InteractionFormat, LangMapContentIdType, RoleInWorkbook, TypeOfTextDisplay, WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.displayElements.{CollapsibleInstructionElement, DisplayLangMapContent}
import it.evadid.workbook.elements.interactionElements.basic.{LabeledCheckboxInteraction, LabeledNumberInteraction, MessagingInteraction, TextInteraction}
import it.evadid.workbook.elements.interactionElements.basic.LabeledNumberInteraction.NumberType
import it.evadid.workbook.elements.interactionElements.codeTaskToggle.{AdvancedCodeRequirement, CodeTaskToggleInteraction, SketchDownloadInteraction}
import it.evadid.workbook.elements.interactionElements.gpt.{GptInteractionElement, TextInputWithScaffoldingAndGrading}
import it.evadid.workbook.elements.interactionElements.reorderExercise.ReorderInteraction
import it.evadid.workbook.elements.interactionElements.matching.{LikertScaleInteraction, MatchingInteraction}
import it.evadid.workbook.elements.interactionElements.sorting.sortingExercise.{SortingInteraction, SortingItem}
import it.evadid.workbook.elements.interactionElements.sorting.sortingReasonExercise.{SortingReasonInteraction, SortingReasonItem}
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.*
import it.evadid.workbook.elements.interactionElements.choice.WorkbookContentChoiceInteraction
import WorkbookContentChoiceInteraction.*
import it.evadid.core.datastructures.language.WorkbookContent
import it.evadid.workbook.abstractions.grading.GraderRunningBehavior
import it.evadid.workbook.elements.interactionElements.neuron.*
import it.evadid.workbook.elements.interactionElements.table.*
import it.evadid.workbook.elements.interactionElements.pixel.*
import it.evadid.workbook.model.pixel.*
import it.evadid.workbook.elements.interactionElements.blockchain.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.{ProgrammingExercise, ProgrammingExerciseFullJava, SortListExercise, SortingMethod}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{ProgramStructureGrader, ProgramStructureRequirement}
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateRaw}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{TurtleDrawingPolicy, TurtleGraphic}
import it.evadid.workbook.elements.interactionElements.programming.state.snap.ProgrammingEditorPalette
import it.evadid.workbook.elements.interactionElements.text.*
import it.evadid.workbook.elements.interactionElements.sql.{SqlCommandExercise, SqlDatabaseConfig}
import munit.FunSuite

class WorkbookElementSerializableRoundTripSpec extends FunSuite {
  import it.evadid.workbook.elements.displayElements.{WorkbookImageElement, LabeledWorkbookElement}
  import LabeledWorkbookElement.{WorkbookLabel, HintLabel}
  import it.evadid.workbook.elements.structureElements.{Workbook, WorkbookSection, ExerciseContainer}
  import it.evadid.workbook.elements.interactionElements.slideshow.Slideshow
  import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*
  import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingExercise
  import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingExerciseFullJava
  import it.evadid.workbook.elements.interactionElements.programming.state.*
  import it.evadid.workbook.elements.interactionElements.programming.state.snap.ProgrammingEditorPalette
  import it.evadid.workbook.elements.interactionElements.emailSimulator.*
  import it.evadid.workbook.elements.interactionElements.qr.*
  import it.evadid.workbook.model.qr.QrCode
  import it.evadid.vm.test.SampleBeTest

  private def content(id: String) = LanguageMapContentId(id)

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
      it.evadid.workbook.elements.interactionElements.compression.CompressionExperimentInteraction("compression", content("compression/title"),
        it.evadid.workbook.model.compression.RunLengthText(special)),
      SqlCommandExercise("sql", SqlDatabaseConfig("school_exercises"), s"SELECT '$special';"),
      it.evadid.workbook.elements.interactionElements.plot.CoordinatePlotInteraction("plot", content("plot/title"),
        content("plot/x"), content("plot/y"), it.evadid.workbook.model.plot.PlotAxis(0, 3, 0.5), it.evadid.workbook.model.plot.PlotAxis(0, 30, 10)),
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
      it.evadid.workbook.elements.interactionElements.evacuation.EvacuationSimulationInteraction("evacuation-simulation"),
      it.evadid.workbook.elements.interactionElements.evacuation.EvacuationConstructFloorInteraction("evacuation"),
      UnicodeComparisonInteraction("unicode", content("text/unicode"), UnicodeComparisonAnswer("paypal.com", "payраl.com")),
      BlockchainInteraction("chain", content("hash/chain"), it.evadid.workbook.model.blockchain.TeachingChain(
        List(it.evadid.workbook.model.blockchain.TeachingBlock("Anna → Lukas: 4 HP")))),
      workbook, section,
      ExerciseContainer("container", content("container/title"), List(input)),
      LabeledWorkbookElement("labeled", input, WorkbookLabel(content("label/hint"), HintLabel)),
      it.evadid.workbook.elements.displayElements.TwoColumnPanel("columns", image, input),
      it.evadid.workbook.elements.structureElements.ExerciseGroup("group", List(image, input)),
      image, Slideshow("slideshow", List(image)),
      GptInteractionElement("gpt", input, content("exercise/text"), List(content("hint/text")), List(content("criterion/text"))),
      TurtleStitchExploreProjectElement("turtle-explore", special),
      TurtleStitchRecreateShapeInteractionLegacy("turtle-legacy", special),
      TurtleRecreateShapeInteraction("turtle-shape", ProgrammingStateRaw(Python, "forward(10)"), TurtleGraphic.TurtleGraphicSvgString("M0,0 L10,0"), customExerciseConfig = Some(RecreateTurtleDidacticConfig.defaultForPalette(ProgrammingEditorPalette.Embroidery).copy(maxCommandUsage = MaxCommandUsage(occurrencesInCode = Map("forward" -> 2)))),
        environment = it.evadid.vm.conversion.dependencies.ProgrammingEnvironment.of(it.evadid.vm.conversion.dependencies.ProgrammingLibrary.Turtle)),
      ProgrammingExercise("programming", Some(SampleBeTest("assert True")), ProgrammingEditorPalette.Embroidery, Some(special),
        it.evadid.vm.conversion.dependencies.ProgrammingEnvironment.of(it.evadid.vm.conversion.dependencies.ProgrammingLibrary.Turtle, it.evadid.vm.conversion.dependencies.ProgrammingLibrary.Web)),
      ProgrammingExerciseFullJava("java", Some(SampleBeTest("assert False")), environment = it.evadid.vm.conversion.dependencies.ProgrammingEnvironment.of(it.evadid.vm.conversion.dependencies.ProgrammingLibrary.Sudoku)),
      SortListExercise("sort", ProgrammingStateRaw(Python, "def sortiere():\n    pass"), "sortiere", Seq(3, 1, 2), Seq(Seq(2, 1), Seq()),
        SortingMethod.BubbleSort, Seq(Python), Seq(ProgramStructureGrader(ProgramStructureRequirement.UsesLoop, "sort-loop", content("test/loop"))),
        Some(content("exercise/sort")), it.evadid.vm.conversion.dependencies.ProgrammingEnvironment.of(it.evadid.vm.conversion.dependencies.ProgrammingLibrary.NumberList)),
      MailEditor("mail-editor", inbox, special, allowCompose = false),
      MailInteraction("mail-interaction", inbox, special, allowCompose = false),
      CreateQrCodeInteraction("qr", QrCodeRequirements(minBytes = 2, maxBytes = Some(200), requiredMask = Some(3)), QrCode.fromText(special)),
      TextInteraction("text-1"),
      it.evadid.workbook.elements.interactionElements.gpt.TextInputWithScaffoldingAndGrading("text-feedback",
        content("text/task"), Some(content("text/solution")), Some(TextInputWithScaffoldingAndGrading.ContentForLlmInteraction(content("text/hint"), Map.empty)), List(TextInputWithScaffoldingAndGrading.ContentForLlmInteraction(content("text/criterion"), Map.empty))),
      MessagingInteraction("messaging-1"),
      LabeledCheckboxInteraction("checkbox-1", content("label/checkbox")),
      LabeledNumberInteraction("number-1", content("label/number"), NumberType.FractionLike, "1.5", BigDecimal("0.125")),
      SketchDownloadInteraction("download-1", content("label/download"), special, special, "reorder-1"),
      DisplayLangMapContent("display-1", content("instruction/body"), LangMapContentIdType(RoleInWorkbook.EXERCISE_DESCRIPTION, TypeOfTextDisplay.MARKDOWN)),
      CollapsibleInstructionElement("collapsible-1", content("hint/title"), content("hint/body"), initiallyCollapsed = false),
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
        val original = element.toSerialized
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
    val original = TurtleRecreateShapeInteraction("turtle-default-policy", source,
      TurtleGraphic.TurtleGraphicSvgString("M0,0 L10,0"), customExerciseConfig = Some(RecreateTurtleDidacticConfig.defaultDidacticConfig.copy(maxCommandUsage = MaxCommandUsage(occurrencesInCode = Map("forward" -> 2)))))
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
      val original = TurtleRecreateShapeInteraction(s"turtle-policy-$policy", source,
        TurtleGraphic.TurtleGraphicSvgString("M0,0 L10,0"), customExerciseConfig = Some(RecreateTurtleDidacticConfig.defaultDidacticConfig.copy(maxCommandUsage = MaxCommandUsage(occurrencesInCode = Map("forward" -> 2)))),
        comparisonPolicy = policy)
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
    val original = TurtleRecreateShapeInteraction("turtle-invalid-policy", ProgrammingStateRaw(Python, "forward(10)"),
      TurtleGraphic.TurtleGraphicSvgString("M0,0 L10,0"))
    List[ujson.Value](ujson.Str("Other"), ujson.Null, ujson.Num(1), ujson.Bool(true), ujson.Obj()).foreach { policy =>
      val invalid = original.toSerialized.withMapAdded(Map("comparisonPolicy" -> policy))
      val error = intercept[it.evadid.distribution.command.SerializedException](WorkbookElementFactory.parse(invalid))
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

  test("every registered factory has a round-trip fixture") {
    assertEquals(elements.map(_.getClass.getSimpleName).toSet, WorkbookElementFactory.registeredElementTypes)
  }

  test("parsing fails when references form a cycle") {
    val cyclicElements = List("first", "second").map { id =>
      WorkbookElementSerializable(id, classOf[CodeTaskToggleInteraction].getSimpleName, Map())
        .withElementAddedAs("reorder", it.evadid.workbook.jsonFactory.WorkbookElementReference(if (id == "first") "second" else "first", Some(classOf[ReorderInteraction.ReorderCodeInteraction].getSimpleName)))
    }

    val error = intercept[it.evadid.distribution.command.SerializedException](WorkbookElementFactory.parseAll(cyclicElements))
    assert(error.getMessage.contains("cyclic dependency"))
  }
}
