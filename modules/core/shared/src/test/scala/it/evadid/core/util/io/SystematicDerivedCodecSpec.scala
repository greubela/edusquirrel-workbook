package it.evadid.core.util.io

import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.core.datastructures.tree.nodeImpl.{NodeBasedTreeNode, NodeBasedTreePosition}
import it.evadid.core.datastructures.vectorShapes.svg.*
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderCommand.*
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.distribution.commandTypes.MailCommands.SendMailRequest
import it.evadid.util.parsing.Js
import it.evadid.vm.BeProgram
import it.evadid.vm.parsing.java.clean.model.{JavaAST, JavaType}
import it.evadid.vm.parsing.python.clean.model.{PyAST, PythonType}
import it.evadid.vm.simulation.BeSimulatorState
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.displayElements.TwoColumnPanel
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import munit.FunSuite
import upickle.default.*

class SystematicDerivedCodecSpec extends FunSuite {
  private def copies[T: ReadWriter](value: T): List[T] =
    List(read[T](write(value)), readBinary[T](writeBinary(value)))
  private def roundTrip[T: ReadWriter](value: T): Unit = copies(value).foreach(assertEquals(_, value))

  test("nested immutable tree nodes and positions have default codecs") {
    roundTrip(NodeBasedTreeNode("学校", List(NodeBasedTreeNode("leaf", Nil))))
    val position = NodeBasedTreePosition(List(2, 0, 4))
    copies(position).foreach { decoded =>
      assertEquals(decoded.forParent(), position.forParent())
      assertEquals(decoded.forChild(3), position.forChild(3))
    }
  }
  test("JSON value-class trees preserve duplicate object keys, order and all node kinds") {
    val value: Js.Val = Js.Obj("same" -> Js.Str("学校"), "same" -> Js.Num(-1.25),
      "array" -> Js.Arr(Js.True, Js.False, Js.Null, Js.Arr()))
    roundTrip(value)
    roundTrip(Js.Str("quoted\"\ntext"))
    roundTrip(Js.Num(1.25))
    roundTrip(Js.Arr(Js.Null))
    roundTrip(Js.Obj("x" -> Js.False))
  }
  test("concrete JSON node codecs reject a different node type") {
    intercept[Exception](read[Js.Str](write[Js.Val](Js.Num(2))))
  }
  test("every SVG command kind preserves its payload and drawing operation") {
    val p = Point(1.25, -2.5)
    val d = Dimension(3.75, 4.5)
    val values: List[SvgPathBuilderCommand[Double]] = List(
      AddControlLinesCommand(p, List(p)), MoveAbs(p), MoveRel(d), ClosePath(), LineAbs(p), LineRel(d),
      HorizontalRel(1.25), VerticalRel(-2.5), CubicAbs(p, p, p), CubicRel(d, d, d),
      QuadAbs(p, p), QuadRel(d, d), ArcAbs(2.0, 3.0, 45.0, true, false, p),
      ArcRel(2.0, 3.0, 45.0, false, true, d), CenteredCircleControl(4.0), RawAppend(" L 9 10"))
    values.foreach { value =>
      roundTrip(value)
      copies(value).foreach(decoded => assertEquals(decoded.getPathDString(), value.getPathDString()))
    }
    roundTrip(StartPathCommand(p))
    roundTrip(CubicAbs(p, p, p))
  }
  test("numeric SVG codecs also preserve decimal coordinates without Double conversion") {
    roundTrip(MoveAbs(Point(BigDecimal("0.1234567890123456789"), BigDecimal("4.75"))))
  }
  test("unknown SVG command tags fail explicitly") {
    intercept[Exception](read[SvgPathBuilderCommand[Double]]("""{"kind":"unknown","payload":null}"""))
  }
  test("immutable builders retain command order, current position and bounds") {
    val builder = SvgPathBuilderImmutable(Point(0.0, 0.0)).lineToRel(Dimension(10.0, 3.0))
      .moveToAbs(Point(20.0, 5.0)).closePath()
    copies[SvgPathBuilder[Double]](builder).foreach { decoded =>
      assert(decoded.isInstanceOf[SvgPathBuilderImmutable[?]])
      assertEquals(decoded.toSvgPathD, builder.toSvgPathD)
      assertEquals(decoded.pathPoints, builder.pathPoints)
      assertEquals(decoded.bounds, builder.bounds)
    }
  }
  test("mutable builder snapshots preserve state for subsequent relative movement and close") {
    val builder = SvgPathBuilderMutable(Point(0.0, 0.0))
      .moveToAbs(Point(10.0, 20.0)).lineToRel(Dimension(5.0, 2.0))
    val decoded = copies(builder).head
    assertEquals(decoded.toSvgPathD, builder.toSvgPathD)
    assertEquals(decoded.pathPoints, builder.pathPoints)
    decoded.lineToRel(Dimension(1.0, 3.0)).closePath()
    builder.lineToRel(Dimension(1.0, 3.0)).closePath()
    assertEquals(decoded.current, Point(10.0, 20.0))
    assertEquals(decoded.toSvgPathD, builder.toSvgPathD)
    copies[SvgPathBuilder[Double]](builder).foreach(value => assert(value.isInstanceOf[SvgPathBuilderMutable[?]]))
  }
  test("turtle builder snapshots retain pen state, geometry and subsequent commands") {
    val builder = TurtlePathBuilder(Point(0.0, 0.0), List(TurtleCommand("forward", List(10.0)), TurtleCommand("right", List(90.0))))
    copies(builder).foreach { decoded =>
      assertEquals(decoded.turtleState, builder.turtleState)
      assertEquals(decoded.svgPathBuilder.toSvgPathD, builder.svgPathBuilder.toSvgPathD)
      val next = TurtleCommand("forward", List(5.0))
      assertEquals(decoded.handleStringCommand(next), builder.handleStringCommand(next))
    }
  }
  test("typed SVG paths retain builders while the root retains rendered path data") {
    val builder = SvgPathBuilderImmutable(Point(0.0, 0.0)).lineToAbs(Point(3.0, 4.0))
    val path = SvgPath.BuilderBasedSvgPath(builder.bounds, builder)
    copies(path).foreach(decoded => assertEquals(decoded.svgPathDString, path.svgPathDString))
    copies[SvgPath](path).foreach(decoded => assertEquals(decoded.svgPathDString, path.svgPathDString))
  }
  test("Python ASTs preserve nested control flow through the open expression branch") {
    import PyAST.*
    val tree: PyAST = PyProgram(List(StatementWithLineNumber(
      PyIfStatement(PyOperationBinary(PyTarget("x"), ">", PythonLiteral("0", new PythonType.PYTHON_INTEGER)),
        PyExecutionBlock(List(PyReturnStatement(Some(PyTarget("x"))))), PyExecutionBlock(List(PyPassStatement))), 4)))
    copies(tree).foreach { decoded =>
      assertEquals(write(decoded), write(tree))
      assertEquals(decoded.allNamedElements().keySet, tree.allNamedElements().keySet)
    }
  }
  test("Python literals restore their hexadecimal literal serializer") {
    val literal: PyAST = PyAST.PythonLiteral("0xff", new PythonType.PYTHON_INTEGER_HEX)
    copies(literal).foreach { value =>
      val restored = value.asInstanceOf[PyAST.PythonLiteral[BigInt]]
      assertEquals(restored.literalValue, "0xff")
      assertEquals(restored.literalType.serializerPythonValue.deserialize("0xff"), BigInt(255))
    }
  }
  test("nested Python type descriptions restore collection and optional types") {
    val dataType: PythonType[List[BigInt]] = new PythonType.PYTHON_LIST(new PythonType.PYTHON_INTEGER_HEX)
    copies(dataType).foreach { value =>
      assertEquals(value.typeStringInPython, dataType.typeStringInPython)
      assertEquals(value.serializerPythonValue.deserialize("[0xff, 0x1]"), List(BigInt(255), BigInt(1)))
    }
    val optional: PythonType[Option[String]] = new PythonType.PYTHON_OPTIONAL(new PythonType.PYTHON_STRING)
    copies(optional).foreach(value => assertEquals(value.serializerPythonValue.deserialize("None"), None))
  }
  test("malformed language type descriptions reject unknown tags and invalid arity") {
    intercept[Exception](read[PythonType[Any]]("""{"kind":"unknown","arguments":[],"label":""}"""))
    intercept[Exception](read[PythonType[Any]]("""{"kind":"list","arguments":[],"label":""}"""))
  }
  test("Java AST literals retain types and language-specific literal behavior") {
    val literal: JavaAST = JavaAST.JavaLiteral("0xff", new JavaType.JAVA_INTEGER_HEX)
    copies(literal).foreach { decoded =>
      val value = decoded.asInstanceOf[JavaAST.JavaLiteral[BigInt]]
      assertEquals(value.literalValue, "0xff")
      assertEquals(value.literalType.serializerJavaValue.deserialize("0xff"), BigInt(255))
    }
    val collection: JavaType[List[BigInt]] = new JavaType.JAVA_LIST(new JavaType.JAVA_INTEGER)
    copies(collection).foreach(value => assertEquals(value.typenameInCode, collection.typenameInCode))
  }
  test("VM simulation state has a default codec independent of serializer imports") {
    val state = BeSimulatorState.startState(BeProgram.empty.fullProgram, NodeBasedTreePosition.root)
    roundTrip(state)
  }
  test("protocol request records expose their own derived codec") {
    roundTrip(SendMailRequest("a@example.com", "学校", "body\nline two"))
  }
  test("derived workbook containers resolve nested registered element interfaces") {
    val panel = TwoColumnPanel("panel", TextInteraction("left"), TextInteraction("right"))
    roundTrip(panel)
    copies[WorkbookElement](panel).foreach(decoded => assertEquals(decoded, panel))
  }
  test("workbook interface readers reject incompatible element kinds") {
    val panel: WorkbookElement = TwoColumnPanel("panel", TextInteraction("left"), TextInteraction("right"))
    intercept[Exception](read[WorkbookInteractionElement[String]](write(panel)))
  }

  test("standalone section snapshots include prerequisites outside their child list") {
    import it.evadid.core.datastructures.language.LanguageMapContentId
    import it.evadid.workbook.elements.structureElements.WorkbookSection
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

  test("Snap primitive defaults preserve their primitive types and reject executable values") {
    import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtleCatalog.*
    Primitives.foreach(roundTrip(_))
    val value = ExtraBlockSpec("example", "motion", List(10, 1.25, true, "学校"))
    roundTrip(value)
    assertEquals(read[ExtraBlockSpec](write(value)).defaults.map(_.getClass), value.defaults.map(_.getClass))
    intercept[Exception](write(ExtraBlockSpec("bad", "motion", List(() => 1))))
    intercept[Exception](read[ExtraBlockSpec]("""{"spec":"bad","category":"motion","defaults":[{"kind":"int","value":1.5}]}"""))
  }

  test("Snap Double defaults retain non-finite values in JSON and MessagePack") {
    import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtleCatalog.ExtraBlockSpec
    val value = ExtraBlockSpec("numeric", "motion", List(Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity))
    copies(value).foreach { decoded =>
      val numbers = decoded.defaults.map(_.asInstanceOf[Double])
      assert(numbers.head.isNaN)
      assertEquals(numbers(1), Double.PositiveInfinity)
      assertEquals(numbers(2), Double.NegativeInfinity)
    }
  }

  test("constant observables and observer policies retain their value semantics") {
    import it.evadid.core.datastructures.state.observable.*
    import ObserverDerivationLogic.*
    roundTrip(ConstantValueObservable("学校"))
    roundTrip(ConstantEmptyObservable[String]())
    roundTrip[ObserverDerivationLogic](DeriveAllValues)
    roundTrip[ObserverDerivationLogic](DeriveOnlyLastValues)
    assertEquals(read[ConstantValueObservable[String]](write(ConstantValueObservable("value"))).now(), Some("value"))
  }

  test("async loading states preserve their loading timestamp through the sealed root") {
    import it.evadid.core.datastructures.state.async.AsyncDataState
    import AsyncDataState.AsyncDataLoading
    val timestamp = java.time.LocalDateTime.parse("2025-01-02T03:04:05")
    val value: AsyncDataState[String, Int] = AsyncDataLoading(timestamp)
    roundTrip(value)
    copies(value).foreach(decoded => assertEquals(decoded.loadingSince, Some(timestamp)))
    val legacy = writeJs(AsyncDataLoading[String, Int](timestamp))
    legacy.obj.remove("startedAt")
    assert(read[AsyncDataLoading[String, Int]](legacy).loadingSince.nonEmpty)
  }

  test("previously private interaction-state codecs are public and retain legacy JSON") {
    import it.evadid.workbook.elements.interactionElements.todo.*
    import it.evadid.workbook.elements.interactionElements.codeTaskToggle.CodeTaskToggleState
    import it.evadid.workbook.elements.interactionElements.sorting.sortingReasonExercise.SortingReasonInteractionState
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
    compatible(CodeTaskToggleState(false, "print('hello')"), CodeTaskToggleState.serializer)
    compatible(SortingReasonInteractionState.initial(3), SortingReasonInteractionState.serializer)
  }

  test("factory and language-storage transfer records have default codecs") {
    import it.evadid.core.util.io.SerializableWithCompanion.GenericSerializableFactory
    import it.evadid.core.datastructures.language.control.LanguageMapStorage.LanguageMapStorageSerializable
    import it.evadid.core.datastructures.language.serialization.abstractions.ParsedTriples
    roundTrip(GenericSerializableFactory("fixture", Map("text" -> "学校")))
    roundTrip(LanguageMapStorageSerializable(ParsedTriples(Set.empty, Set.empty)))
  }

  test("atomic type codecs restore registered executable literal policies") {
    import it.evadid.vm.types.BeDataType
    for (value <- List(BeDataType.String, BeDataType.Int, BeDataType.Numeric, BeDataType.Boolean)) {
      copies(value).foreach { decoded =>
        assert(decoded eq value)
        assertEquals(decoded.isValidLiteral("12"), value.isValidLiteral("12"))
      }
    }
    roundTrip(BeDataType.BeSerializableAtomicType("Int"))
  }

  test("rich synchronization histories expose a default codec with timestamps and legacy history format") {
    import it.evadid.workbook.interaction.sync.SyncFormatter.RichInteractionVariableHistorySerialized
    import it.evadid.workbook.interaction.variable.InteractionVariableHistorySerialized
    val value = RichInteractionVariableHistorySerialized("fixture", java.time.LocalDateTime.parse("2025-01-02T03:04:05"),
      "学校", InteractionVariableHistorySerialized.empty)
    roundTrip(value)
  }
}
