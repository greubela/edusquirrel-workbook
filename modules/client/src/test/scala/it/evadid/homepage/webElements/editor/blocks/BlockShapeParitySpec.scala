package it.evadid.homepage.webElements.editor.blocks

import it.evadid.core.datastructures.geometry.{Bounds, Point}
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement
import it.evadid.core.datastructures.vectorShapes.blocks.*
import it.evadid.core.datastructures.vectorShapes.renderer.SvgLaminarRenderer
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathParser
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.control.{BeBlockIfElse, BeBlockSequence, BeBlockStarter, BeBlockWhile}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.other.BeBlockUnsupported
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer.*
import it.evadid.util.logging.Logger
import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.{BeIfElse, BeSequence, BeWhile}
import it.evadid.vm.code.errors.BeExpressionUnsupported
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.tree.{BeExpressionNode, BeExpressionReference, BeExtensionPoint}
import it.evadid.vm.code.usage.{BeFunctionCall, BeUseValue}
import it.evadid.vm.conversion.pythonexpression.PythonToExpression
import it.evadid.vm.naming.NamingStyle
import it.evadid.vm.types.BeChildRole.{BodySequence, ConditionInControlStructure, ExpressionInSequence}
import it.evadid.vm.types.BeScope.GlobalScope
import it.evadid.vm.types.{BeChildInfo, BeChildRole, BeDataType}
import org.scalajs.dom
import todomove.webElementsOld.webElements.svg.shapes.{BeShape, ControlFlowAndExpressionShape, TextShape}
import todomove.webElementsOld.webElements.svg.shapes.composite.{HBoxSameHeight, HorizontalAlignment, ShapeAroundShape, VBoxSameWidth, VerticalAlignment}
import todomove.webElementsOld.webElements.svg.shapes.controlflow.doubleWidth.*
import todomove.webElementsOld.webElements.svg.shapes.controlflow.singleWidth.*
import todomove.webElementsOld.webElements.svg.shapes.datatypes.{LiteralShape, RectangleShape, UnitShape}
import todomove.webElementsOld.webElements.svg.shapes.special.nested.ShapeWithControlFlow

import scala.scalajs.js

/** The block shapes ported to the vector shape framework draw what the legacy shapes drew. */
class BlockShapeParitySpec extends EditorDomSuite {
  override def beforeAll(): Unit = {
    super.beforeAll()
    // Deterministic text metrics: jsdom has no canvas implementation.
    val context = js.Dynamic.literal(font = "", measureText = ((text: String) => js.Dynamic.literal(
      width = text.length * 7.0, actualBoundingBoxAscent = 10.0, actualBoundingBoxDescent = 2.0)): js.Function1[String, js.Dynamic])
    js.Dynamic.global.window.HTMLCanvasElement.prototype.getContext = ((_: js.Any) => context): js.Function1[js.Any, js.Dynamic]
  }

  private val legacy = BeRenderingConfig.default()
  private val factory = legacy.amendFactory
  private val logger = Logger.withNameAndPrefixes(Some("BlockShapeParitySpec"))

  /** Tag, geometry and normalized colours of every drawn element, in document order. */
  private def drawn(root: dom.Element): List[String] = {
    def color(value: String | Null, absent: String): String = Option(value).filter(_.nonEmpty).map { raw =>
      val rgba = "rgba?\\((\\d+), (\\d+), (\\d+), ([\\d.]+)\\)".r
      raw match {
        case rgba(r, g, b, a) => if a.toDouble == 0 then "none" else "#%02x%02x%02x".format(r.toInt, g.toInt, b.toInt)
        case "transparent" | "none" => "none"
        case "white" => "#ffffff"
        case hex => hex.toLowerCase
      }
    }.getOrElse(absent)
    val nodes = root.querySelectorAll("path, text")
    (0 until nodes.length).toList.map(nodes(_)).map { node =>
      val fill = color(node.getAttribute("fill"), "#000000")
      val stroke = color(node.getAttribute("stroke"), "none")
      val dash = Option(node.getAttribute("stroke-dasharray")).getOrElse("")
      if node.tagName.toLowerCase == "text" then s"text ${node.getAttribute("x")} ${node.getAttribute("y")} ${node.textContent} $fill $stroke"
      else s"path ${geometry(node.getAttribute("d"))} $fill $stroke $dash"
    }
  }

  /** Absolute path points: equal for absolute and relative encodings of one outline. */
  private def geometry(d: String): String =
    SvgPathParser.parseString(d).map(_.pathPoints.map(p => f"${p.x}%.6f,${p.y}%.6f").mkString(" ")).getOrElse(s"unparsable $d")

  private def legacyDrawing(shape: BeShape): List[String] =
    drawn(shape.render(legacy, Bounds(Point(0.0, 0.0), shape.displaySize(legacy))).renderWithMods.ref)

  private def newDrawing(element: AppShapeElement[Double]): List[String] =
    drawn(SvgLaminarRenderer.renderElementAsSvg(logger, BlockElements.layout(element)).ref)

  private def children(expression: BeExpression): Seq[BeExpressionReference] = expression.structureInfo.getChildrenAsReference(GlobalScope())

  private def literal(value: BeUseValue): BeShape =
    ShapeAroundShape(LiteralShape, TextShape(LanguageMap.universalMap(value.structureInfo.toStringInLanguage(Python, English))))
      .addAmends(factory.literalColorsAmend)

  private def call(function: BeFunctionCall): BeShape = {
    val name = TextShape(function.funcDef.functionTypeInfo.displayName.asLanguageMap(NamingStyle.SnakeCase))
    val parameters = children(function).map(_.expr).collect { case value: BeUseValue => literal(value) }.toList
    ShapeAroundShape(UnitShape, HBoxSameHeight(name :: parameters, true)).addAmends(factory.defaultFunctionColorsAmend)
  }

  /** The legacy blocks' nested rendering, as `BeBlock.renderNested` composed it. */
  private def nested(expression: BeExpression): ControlFlowAndExpressionShape = {
    def results(blocks: Seq[(BeChildRole, BeExpression)]): Map[(BeExpressionNode, BeBlock), ControlFlowAndExpressionShape] =
      blocks.zipWithIndex.map { case ((role, child), index) =>
        (BeExpressionReference(BeChildInfo(role, GlobalScope()), child): BeExpressionNode,
          BeBlockUnsupported(BeExpressionUnsupported(index.toString)): BeBlock) -> nested(child)
      }.toMap
    def roles(value: BeExpression) = children(value).map(ref => ref.childInfo.myRoleInParent -> ref.expr)
    expression match {
      case start: BeStartProgram => BeBlockStarter().renderNested(null, results(roles(start)), null)
      case sequence: BeSequence => BeBlockSequence(sequence).renderNested(null, results(roles(sequence)), null)
      case loop: BeWhile => BeBlockWhile(loop).renderNested(null, results(roles(loop)), null)
      case branch: BeIfElse => BeBlockIfElse(branch).renderNested(null, results(roles(branch)), null)
      case function: BeFunctionCall => ShapeWithControlFlow(ControlFlowFunctionCall(), call(function))
      case value: BeUseValue => ShapeWithControlFlow(ControlFlowEmpty(), literal(value))
      case other => fail(s"No legacy reference for $other")
    }
  }

  test("literal and command blocks draw the legacy outlines, text and colours") {
    val program = PythonToExpression.program("forward(100)\n")
    val forward = children(children(program.fullProgram).head.expr).head.expr.asInstanceOf[BeFunctionCall]
    assertEquals(newDrawing(BeExpressionBlocks.program(BeProgram(forward), BlockView.library)),
      legacyDrawing(VBoxSameWidth(List(call(forward)), false, HorizontalAlignment.Left, VerticalAlignment.Center)))
    val label = LanguageMap.universalMap[it.evadid.core.datastructures.language.AppLanguage.HumanLanguage]("REPEAT-WHILE")
    assertEquals(newDrawing(BeExpressionBlocks.template(forward, Some(label))),
      legacyDrawing(ShapeAroundShape(RectangleShape, TextShape(label)).addAmends(List(
        com.raquo.laminar.api.L.svg.fill := legacy.colorPalette.yellows(2).toWebColor.webStyleRgbString))))
  }

  test("sequences, loops and branches nest like the legacy blocks") {
    val program = PythonToExpression.program("forward(100)\nwhile True:\n    right(90)\nif True:\n    left(45)\nelse:\n    backward(10)\n")
    val actual = newDrawing(BeExpressionBlocks.program(program, BlockView(placeholders = false, ControlFlowDisplay.Backgrounds)))
    assertEquals(actual, legacyDrawing(nested(program.fullProgram)))
    assert(actual.size > 20, actual)
  }

  test("placeholders draw the legacy unit outline in the idle colour") {
    val empty = BeProgram.empty
    val sequence = children(empty.fullProgram).head
    val slot = BeExtensionPoint(false, BeChildInfo(ExpressionInSequence(0), GlobalScope()), BeDataType.Unit)
    val placeholder = ShapeWithControlFlow(ControlFlowDirected(true, true), UnitShape.addAmends(factory.mutedColorsFunctionAmend))
    val legacySequence = BeBlockSequence(sequence.expr.asInstanceOf[BeSequence]).renderNested(null,
      Map((slot: BeExpressionNode, BeBlockUnsupported(BeExpressionUnsupported("slot")): BeBlock) -> placeholder), null)
    val legacyStart = BeBlockStarter().renderNested(null, Map((sequence: BeExpressionNode,
      BeBlockUnsupported(BeExpressionUnsupported("seq")): BeBlock) -> legacySequence), null)
    assertEquals(newDrawing(BeExpressionBlocks.program(empty, BlockView(controlFlow = ControlFlowDisplay.Backgrounds))), legacyDrawing(legacyStart))
  }

  test("the control-flow overlay draws the legacy paths and decorations") {
    val legacyLines: List[NestedBlockLine] = List(
      ControlFlowLine(ControlFlowProgramStarter()),
      ControlFlowLine(ControlFlowFunctionCall()),
      ControlFlowIncreaseLine(RepetitionSplit(), None, ControlFlowDownUp(false)),
      ControlFlowLine(ControlFlowDirected(true, true)),
      ControlFlowDecreaseLine(RepetitionUnion(), None),
      ControlFlowIncreaseLine(IfElseSplit(), None, ControlFlowDirected(true, false)),
      ControlFlowLine(ControlFlowEmpty()),
      ControlFlowReplaceLine(ControlFlowCross(), None, ControlFlowDirected(true, false)),
      ControlFlowLine(ControlFlowDirected(true, false)),
      ControlFlowDecreaseLine(IfElseUnion(), None),
      ControlFlowLine(ControlFlowProgramStopper()))
    val lines = List(FlowLine.Plain(FlowShape.ProgramStarter), FlowLine.Plain(FlowShape.FunctionCall),
      FlowLine.Increase(FlowShape.RepetitionSplit, FlowShape.DownUp(false)), FlowLine.Plain(FlowShape.Directed(true, true)),
      FlowLine.Decrease(FlowShape.RepetitionUnion), FlowLine.Increase(FlowShape.IfElseSplit, FlowShape.Directed(true, false)),
      FlowLine.Plain(FlowShape.Empty), FlowLine.Replace(FlowShape.IfElseCross, FlowShape.Directed(true, false)),
      FlowLine.Plain(FlowShape.Directed(true, false)), FlowLine.Decrease(FlowShape.IfElseUnion), FlowLine.Plain(FlowShape.ProgramStopper))
    List(List.fill(lines.size)(16.0), List(30.0, 25.0, 80.0, 41.0, 50.0, 95.0, 20.0, 50.0, 33.0, 70.0, 30.0)).foreach { heights =>
      val overlay = FlowLayout(lines).overlay(BlockRenderingConfig(), heights)
      val original = NestedBlockRenderer(List(NestedBlockSegment(legacyLines))).controlFlowOnly(legacy, heights)
      assertEquals(newDrawing(overlay.toShape(BlockRenderingConfig())), legacyDrawing(original))
      assertEquals(overlay.displaySize(legacy.controlSegmentSize), original.displaySize(legacy))
    }
  }
}
