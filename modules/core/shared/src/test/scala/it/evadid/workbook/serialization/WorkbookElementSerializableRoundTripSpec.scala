package it.evadid.workbook.serialization

import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.{LangMapContentIdType, RoleInWorkbook, TypeOfTextDisplay, WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.displayElements.{CollapsibleInstructionElement, DisplayLangMapContent}
import it.evadid.workbook.elements.interactionElements.basic.{LabeledCheckboxInteraction, LabeledNumberInteraction, MessagingInteraction, TextInteraction}
import it.evadid.workbook.elements.interactionElements.basic.LabeledNumberInteraction.NumberType
import it.evadid.workbook.elements.interactionElements.codeTaskToggle.{AdvancedCodeRequirement, CodeTaskToggleInteraction, SketchDownloadInteraction}
import it.evadid.workbook.elements.interactionElements.gpt.GptInteractionElement
import it.evadid.workbook.elements.interactionElements.reorderExercise.ReorderInteraction
import it.evadid.workbook.elements.interactionElements.sortingExercise.{SortingInteraction, SortingItem}
import it.evadid.workbook.elements.interactionElements.sortingReasonExercise.{SortingReasonInteraction, SortingReasonItem}
import it.evadid.workbook.jsonFactory.{WorkbookElementSerializable, WorkbookElementFactory}
import upickle.default.*
import it.evadid.workbook.elements.interactionElements.choice.ChoiceInteraction
import it.evadid.workbook.elements.interactionElements.neuron.*
import it.evadid.workbook.elements.interactionElements.table.*
import it.evadid.workbook.elements.interactionElements.pixel.*
import it.evadid.workbook.model.pixel.*
import it.evadid.workbook.elements.interactionElements.blockchain.*
import it.evadid.workbook.elements.interactionElements.text.*
import it.evadid.workbook.elements.interactionElements.sql.{SqlCommandExercise, SqlDatabaseConfig}
import munit.FunSuite

class WorkbookElementSerializableRoundTripSpec extends FunSuite {
  import it.evadid.workbook.elements.displayElements.{ImageElement, LabeledWorkbookElement}
  import LabeledWorkbookElement.{WorkbookLabel, HintLabel}
  import it.evadid.workbook.elements.structureElements.{Workbook, WorkbookSection, ExerciseContainer}
  import it.evadid.workbook.elements.interactionElements.slideshow.{Slideshow, SlideshowPanel}
  import it.evadid.workbook.elements.interactionElements.Turtle.*
  import it.evadid.workbook.elements.interactionElements.programming.*
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
    val image = ImageElement("image", content("image/source"), TypeOfTextDisplay.URL_RELATIVE_TO_WORKBOOK_RESOURCES)
    val slide = SlideshowPanel.ImageSlide("slide", image, content("slide/title"), content("slide/body"))
    val twoColumn = SlideshowPanel.TwoColumnImagePanel("two-column", image, content("left/title"), content("right/title"), content("left/body"), content("right/body"))
    val input = TextInteraction("gpt-input")
    val section = WorkbookSection("section", WorkbookSection.WorkbookSectionMetadata(content("section/title")), List(input))
    val workbook = Workbook("workbook", Workbook.WorkbookMetadata(Set.empty, Set.empty, content("workbook/title"), List(it.evadid.core.datastructures.language.AppLanguage.English)), List(section))
    val special = " Quotes: \"hello\"; backslash: \\; newline:\nGrüße )({} "
    val inbox = InboxState.withMails(List(Mail("mail", "sender@example.test", special, special, special, MailFolder.Inbox, Some(special), "2026-10-08", expectedFolder = Some(MailFolder.Archive))))
    List(
      SqlCommandExercise("sql", SqlDatabaseConfig("school_exercises"), s"SELECT '$special';"),
      it.evadid.workbook.elements.interactionElements.plot.CoordinatePlotInteraction("plot", content("plot/title"),
        content("plot/x"), content("plot/y"), it.evadid.workbook.model.plot.PlotAxis(0, 3, 0.5), it.evadid.workbook.model.plot.PlotAxis(0, 30, 10)),
      ChoiceInteraction("choice", content("choice/prompt"), List(content("choice/a"), content("choice/b")), true, Some(List(0, 1))),
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
      image, slide, twoColumn, Slideshow("slideshow", List(slide, twoColumn)),
      GptInteractionElement("gpt", input, content("exercise/text"), List(content("hint/text")), List(content("criterion/text"))),
      TurtleStitchExploreProjectElement("turtle-explore", special),
      TurtleStitchRecreateShapeInteractionLegacy("turtle-legacy", special),
      TurtleRecreateShapeInteraction("turtle-shape", ProgrammingStatePythonString("forward(10)"), TurtleGraphic.TurtleGraphicSvgString("M0,0 L10,0"), ProgrammingEditorPalette.Embroidery, Map("forward" -> Integer.valueOf(2))),
      ProgrammingExercise("programming", Some(SampleBeTest("assert True")), ProgrammingEditorPalette.Embroidery, Some(special)),
      ProgrammingExerciseFullJava("java", Some(SampleBeTest("assert False"))),
      MailEditor("mail-editor", inbox, special, allowCompose = false),
      MailInteraction("mail-interaction", inbox, special, allowCompose = false),
      CreateQrCodeInteraction("qr", QrCodeRequirements(minBytes = 2, maxBytes = Some(200), requiredMask = Some(3)), QrCode.fromText(special)),
      TextInteraction("text-1"),
      MessagingInteraction("messaging-1"),
      LabeledCheckboxInteraction("checkbox-1", content("label/checkbox")),
      LabeledNumberInteraction("number-1", content("label/number"), NumberType.FractionLike, "1.5", BigDecimal("0.125")),
      SketchDownloadInteraction("download-1", content("label/download"), special, special, "reorder-1"),
      DisplayLangMapContent("display-1", content("instruction/body"), LangMapContentIdType(RoleInWorkbook.EXERCISE_DESCRIPTION, TypeOfTextDisplay.MARKDOWN)),
      CollapsibleInstructionElement("collapsible-1", content("hint/title"), content("hint/body"), initiallyCollapsed = false),
      reorder,
      ReorderInteraction.ReorderMapIdInteraction("reorder-map-1", List(content("line/one"), content("line/two")), 9),
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
        val roundTripped = WorkbookElementFactory.parseAll(restored :: dependencies).head
        assertEquals(roundTripped, element)
      }
    }
  }

  private def assertStateRoundTrip[T](element: WorkbookInteractionElement[T]): Unit = {
    val serializer = element.serializerInteractionContent
    assertEquals(serializer.deserialize(serializer.serialize(element.defaultValue)), element.defaultValue)
  }

  elements.collect { case element: WorkbookInteractionElement[?] => element }.foreach { element =>
    test(s"${element.getClass.getSimpleName} interaction state round-trips") {
      assertStateRoundTrip(element)
    }
  }

  test("legacy turtle state preserves absent and present XML and reads the old raw format") {
    val serializer = TurtleStitchRecreateShapeInteractionLegacy("legacy", "project.xml").serializerInteractionContent
    val xml = "<project name=\"Grüße\">\n<notes> \\ </notes></project>"
    for (state <- List(TurtleStitchProjectState.empty(), TurtleStitchProjectState.parseFromString("").get, TurtleStitchProjectState.parseFromString(xml).get)) {
      assertEquals(serializer.deserialize(serializer.serialize(state)), state)
    }
    assertEquals(serializer.deserialize(xml), TurtleStitchProjectState.parseFromString(xml).get)
    assertEquals(serializer.deserialize(""), TurtleStitchProjectState.empty())
  }

  test("old number elements without diff retain the default step") {
    val element = LabeledNumberInteraction("number", content("number/label"), NumberType.IntegerLike)
    val serialized = element.toSerialized
    assertEquals(WorkbookElementFactory.parse(serialized.copy(allConstructorFields = serialized.allConstructorFields - "diff")), element)
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

  test("GptInteractionElement resolves its underlying interaction reference") {
    val input = TextInteraction("gpt-input")
    val gpt = GptInteractionElement("gpt", input, content("exercise/text"), List(content("hint/text")), List(content("criterion/text")))

    assertEquals(WorkbookElementFactory.parseAll(List(gpt.toSerialized, input.toSerialized)), List(gpt, input))
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
