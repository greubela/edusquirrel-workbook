package it.evadid.core.util.io

import it.evadid.workbook.abstractions.InteractionFormat.TextInteractionFormat

import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.core.datastructures.tree.nodeImpl.{NodeBasedTreeNode, NodeBasedTreePosition}
import it.evadid.core.datastructures.vectorShapes.svg.*
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderCommand.*
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.distribution.commandTypes.MailCommands.SendMailRequest
import it.evadid.vm.BeProgram
import it.evadid.vm.parsing.java.clean.model.{JavaAST, JavaType}
import it.evadid.vm.parsing.python.clean.model.{PyAST, PythonType}
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.display.TwoColumnPanel
import it.evadid.workbook.interactions.basic.TextInteraction
import munit.FunSuite
import upickle.default.*

class SystematicDerivedCodecSpec extends FunSuite {
  private def copies[T: ReadWriter](value: T): List[T] =
    List(read[T](write(value)), readBinary[T](writeBinary(value)))
  private def roundTrip[T: ReadWriter](value: T): Unit = copies(value).foreach(assertEquals(_, value))
  test("protocol request records expose their own derived codec") {
    roundTrip(SendMailRequest("a@example.com", "学校", "body\nline two", Nil))
  }
  test("workbook containers round-trip through the canonical element codec") {
    val panel = TwoColumnPanel("panel", TextInteraction("left"), TextInteraction("right"))
    roundTrip(panel)
    copies[WorkbookElement](panel).foreach(decoded => assertEquals(decoded, panel))
  }
  test("workbook interface readers reject incompatible element kinds") {
    val panel: WorkbookElement = TwoColumnPanel("panel", TextInteraction("left"), TextInteraction("right"))
    intercept[Exception](read[WorkbookInteractionElement[TextInteractionFormat.type]](write(panel)))
  }

  test("standalone section snapshots include prerequisites outside their child list") {
    import it.evadid.core.datastructures.language.LanguageMapContentId
    import it.evadid.workbook.elements.structure.WorkbookSection
    import WorkbookSection.WorkbookSectionMetadata
    val first = WorkbookSection("first", WorkbookSectionMetadata(LanguageMapContentId("first/title")), List(TextInteraction("one")))
    val second = WorkbookSection("second", WorkbookSectionMetadata(LanguageMapContentId("second/title"), List(first), List(first)),
      List(TwoColumnPanel("panel", TextInteraction("left"), TextInteraction("right"))))
    roundTrip(second)
    copies[WorkbookElement](second).foreach(value => assertEquals(value, second))
  }

  test("workbook snapshots reject conflicting definitions with the same id") {
    val value: WorkbookElement = TwoColumnPanel("root", TextInteraction("same"),
      TwoColumnPanel("same", TextInteraction("left"), TextInteraction("right")))
    intercept[IllegalArgumentException](write(value))
  }

  test("Snap primitive defaults round-trip as JSON numbers, strings and booleans") {
    import it.evadid.workbook.interactions.programming.state.snap.SnapTurtleCatalog.*
    Primitives.foreach(roundTrip(_))
    val value = ExtraBlockSpec("example", "motion", List(ujson.Num(10), ujson.Num(1.25), ujson.Bool(true), ujson.Str("学校")))
    roundTrip(value)
    assertEquals(read[ExtraBlockSpec](write(value)).defaults, value.defaults)
  }

  test("Snap primitive defaults reject structured and non-finite JSON values") {
    import it.evadid.workbook.interactions.programming.state.snap.SnapTurtleCatalog.ExtraBlockSpec
    List(ujson.Null, ujson.Arr(1), ujson.Obj("x" -> 1), ujson.Num(Double.NaN),
      ujson.Num(Double.PositiveInfinity), ujson.Num(Double.NegativeInfinity)).foreach { invalid =>
      intercept[IllegalArgumentException](ExtraBlockSpec("invalid", "motion", List(invalid)))
    }
    intercept[Exception](read[ExtraBlockSpec]("""{"spec":"invalid","category":"motion","defaults":[{}]}"""))
  }

  test("previously private interaction-state codecs are public and retain legacy JSON") {
    import it.evadid.workbook.interactions.todo.*
    import it.evadid.workbook.interactions.matching.MatchingInteractionState
    import it.evadid.workbook.interactions.codeTaskToggle.CodeTaskToggleState
    import it.evadid.workbook.interactions.sorting.sortingReasonExercise.SortingReasonInteractionState
    def compatible[T: ReadWriter](value: T, serializer: it.evadid.core.util.io.Serializer[T]): Unit = {
      roundTrip(value)
      assertEquals(writeJs(value), ujson.read(serializer.serialize(value)))
      assertEquals(read[T](serializer.serialize(value)), value)
    }
    compatible(ChoiceSelectionState(List(2, 0)), ChoiceSelectionState.serializer)
    compatible(DropdownBlanksState(List(None, Some(2))), DropdownBlanksState.serializer)
    compatible(FillInBlanksState(List("学校", "")), FillInBlanksState.serializer)
    compatible(MatchingInteractionState(List(Some(1), None)), MatchingInteractionState.serializer)
    compatible(TableFillInState(List("one\ntwo", "")), TableFillInState.serializer)
    roundTrip(CodeTaskToggleState(false, "print('hello')"))
    roundTrip(SortingReasonInteractionState.initial(3))
  }

  test("atomic type codecs restore registered executable literal policies") {
    import it.evadid.vm.types.BeDataType
    for (value <- List(BeDataType.String, BeDataType.Int, BeDataType.Numeric, BeDataType.Boolean)) {
      copies(value).foreach { decoded =>
        assert(decoded eq value)
        assertEquals(decoded.isValidLiteral("12"), value.isValidLiteral("12"))
      }
    }
  }
}
