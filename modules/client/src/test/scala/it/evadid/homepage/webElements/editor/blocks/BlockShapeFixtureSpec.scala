package it.evadid.homepage.webElements.editor.blocks

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement
import it.evadid.core.datastructures.vectorShapes.blocks.*
import it.evadid.core.datastructures.vectorShapes.renderer.SvgLaminarRenderer
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathParser
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorFlowchartGutter
import it.evadid.core.datastructures.vectorShapes.renderer.PythonFlowchartGutter
import it.evadid.util.logging.Logger
import it.evadid.vm.BeProgram
import it.evadid.vm.code.usage.BeFunctionCall
import it.evadid.vm.conversion.pythonexpression.PythonToExpression
import it.evadid.vm.editing.{BeNodePath, BeProgramEditing}
import it.evadid.vm.types.BeChildRole.{BodySequence, ExpressionInSequence}
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.URIUtils.decodeURIComponent

/** The block drawings match those of the replaced legacy shape renderer, recorded with the same text metrics. */
class BlockShapeFixtureSpec extends EditorDomSuite {
  override def beforeAll(): Unit = {
    super.beforeAll()
    // Deterministic text metrics: jsdom has no canvas implementation.
    val context = js.Dynamic.literal(font = "", measureText = ((text: String) => js.Dynamic.literal(
      width = text.length * 7.0, actualBoundingBoxAscent = 10.0, actualBoundingBoxDescent = 2.0)): js.Function1[String, js.Dynamic])
    js.Dynamic.global.window.HTMLCanvasElement.prototype.getContext = ((_: js.Any) => context): js.Function1[js.Any, js.Dynamic]
  }

  private lazy val legacy: Map[String, List[String]] = upickle.default.read[Map[String, List[String]]](
    js.Dynamic.global.selectDynamic("require")("fs").readFileSync("modules/client/src/test/resources/blocks/legacy-block-drawings.json", "utf8")
      .asInstanceOf[String])
  private val logger = Logger.withNameAndPrefixes(Some("BlockShapeFixtureSpec"))

  /** Absolute path points: equal for absolute and relative encodings of one outline. */
  private def geometry(d: String): String =
    SvgPathParser.parseString(d).map(_.pathPoints.map(p => f"${p.x}%.6f,${p.y}%.6f").mkString(" ")).getOrElse(s"unparsable $d")

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

  private def drawing(element: AppShapeElement[Double]): List[String] =
    drawn(SvgLaminarRenderer.renderElementAsSvg(logger, BlockElements.layout(element)).ref)

  private val nested = PythonToExpression.program("forward(100)\nwhile True:\n    right(90)\nif True:\n    left(45)\nelse:\n    backward(10)\n")

  test("commands and library labels draw the legacy outlines, text and colours") {
    val forward = PythonToExpression.program("forward(100)\n")
    val call = BeProgramEditing.find(forward, BeNodePath.root / BodySequence(0) / ExpressionInSequence(0)).get.asInstanceOf[BeFunctionCall]
    assertEquals(drawing(BeExpressionBlocks.program(BeProgram(call), BlockView.library)), legacy("forward-command"))
    assertEquals(drawing(BeExpressionBlocks.template(call, Some(LanguageMap.universalMap[HumanLanguage]("REPEAT-WHILE")))), legacy("library-label"))
  }

  test("sequences, loops, branches and placeholders nest like the legacy blocks") {
    val actual = drawing(BeExpressionBlocks.program(nested, BlockView(placeholders = false, ControlFlowDisplay.Backgrounds)))
    assertEquals(actual, legacy("nested-program"))
    assert(actual.size > 20, actual)
    assertEquals(drawing(BeExpressionBlocks.program(BeProgram.empty, BlockView(controlFlow = ControlFlowDisplay.Backgrounds))),
      legacy("placeholder-program"))
  }

  test("the control-flow overlay draws the legacy paths and decorations") {
    val lines = List(FlowLine.Plain(FlowShape.ProgramStarter), FlowLine.Plain(FlowShape.FunctionCall),
      FlowLine.Increase(FlowShape.RepetitionSplit, FlowShape.DownUp(false)), FlowLine.Plain(FlowShape.Directed(true, true)),
      FlowLine.Decrease(FlowShape.RepetitionUnion), FlowLine.Increase(FlowShape.IfElseSplit, FlowShape.Directed(true, false)),
      FlowLine.Plain(FlowShape.Empty), FlowLine.Replace(FlowShape.IfElseCross, FlowShape.Directed(true, false)),
      FlowLine.Plain(FlowShape.Directed(true, false)), FlowLine.Decrease(FlowShape.IfElseUnion), FlowLine.Plain(FlowShape.ProgramStopper))
    List("overlay-uniform-rows" -> List.fill(lines.size)(16.0),
      "overlay-mixed-rows" -> List(30.0, 25.0, 80.0, 41.0, 50.0, 95.0, 20.0, 50.0, 33.0, 70.0, 30.0)).foreach { (name, heights) =>
      assertEquals(drawing(FlowLayout(lines).overlay(BlockRenderingConfig(), heights).toShape(BlockRenderingConfig())), legacy(name))
    }
  }

  test("the CodeMirror gutter draws the legacy overlay of its rows") {
    val chart = PythonFlowchartGutter.fromPython("if True:\n    print(1)\nelse:\n    print(2)")
    val image = CodeMirrorFlowchartGutter.render(chart).rows(1).slices.head.image
    val host = dom.document.createElement("div")
    host.innerHTML = decodeURIComponent(image.drop(image.indexOf(',') + 1))
    assertEquals(drawn(host.firstElementChild), legacy("gutter-if-else"))
  }
}
