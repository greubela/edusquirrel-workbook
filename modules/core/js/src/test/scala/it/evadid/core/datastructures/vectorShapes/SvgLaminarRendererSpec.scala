package it.evadid.core.datastructures.vectorShapes

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.color.RGBColor
import it.evadid.core.datastructures.font.AppFont
import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.AppShapeComposition
import it.evadid.core.datastructures.vectorShapes.atomar.{AppShapeDrawingRoutineElement, AppShapeTextElement}
import it.evadid.core.datastructures.vectorShapes.compositions.CompositionHBox
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeElementConfig, AppShapeRenderingConfig}
import it.evadid.core.datastructures.vectorShapes.helper.AlignmentInParent
import it.evadid.core.datastructures.vectorShapes.renderer.{SvgLaminarRenderer, SvgViewport}
import it.evadid.core.datastructures.vectorShapes.svg.drawingRoutines.RectangleShape
import it.evadid.util.logging.BasicLogger
import munit.FunSuite
import org.scalajs.dom
import scala.scalajs.js

/** Run npm ci at the repository root before coreJS/test to install jsdom. */
class SvgLaminarRendererSpec extends FunSuite {
  private val globals = js.Dynamic.global.globalThis
  private val requireFn = js.Dynamic.global.selectDynamic("require")
  private val root = js.Dynamic.global.process.cwd().asInstanceOf[String]
  private val jsdom = requireFn(root + "/node_modules/jsdom")
  private val window = js.Dynamic.newInstance(jsdom.JSDOM)("<html><body><div id='root'></div></body></html>").window
  private val names = List("window", "document", "Element", "Node")
  private val previous = names.map(name => name -> globals.selectDynamic(name))
  private var missingHeight = false
  private val context = js.Dynamic.literal(
    font = "",
    measureText = ((text: String) =>
      if missingHeight then js.Dynamic.literal(width = text.length * 10.0)
      else js.Dynamic.literal(width = text.length * 10.0, actualBoundingBoxAscent = 10.0, actualBoundingBoxDescent = 0.0)
    ): js.Function1[String, js.Dynamic]
  )

  override def beforeAll(): Unit = {
    globals.updateDynamic("window")(window)
    globals.updateDynamic("document")(window.document)
    globals.updateDynamic("Element")(window.Element)
    globals.updateDynamic("Node")(window.Node)
    window.HTMLCanvasElement.prototype.updateDynamic("getContext")(((_: String) => context): js.Function1[String, js.Dynamic])
  }
  override def afterAll(): Unit = {
    previous.foreach { (name, value) => globals.updateDynamic(name)(value) }
    window.close()
  }

  private val rendering = AppShapeRenderingConfig.defaultDouble
  private def config(clicked: Boolean => Unit = _ => (), stroke: Double = 1,
      fontToUse: AppFont = AppFont.defaultFont): AppShapeElementConfig[Double] = new AppShapeElementConfig[Double] {
    override def colorStroke = RGBColor.black
    override def colorFill = RGBColor.red
    override def colorFont = RGBColor.yellow
    override def font = fontToUse
    override def strokeWidth = stroke
    override def useCustomPadding = None
    override def onMouseClicked(leftButton: Boolean): Unit = clicked(leftButton)
  }
  private def rectangle(c: AppShapeElementConfig[Double]) =
    AppShapeDrawingRoutineElement(RectangleShape[Double](), c, Some(Dimension(10.0, 20.0)))

  private def click(target: dom.Element, button: Int): Unit = {
    val event = js.Dynamic.newInstance(window.MouseEvent)("click", js.Dynamic.literal(bubbles = true, button = button))
    target.dispatchEvent(event.asInstanceOf[dom.Event])
  }

  test("SVG viewport includes padding and thick descendant strokes") {
    val shape = AppShapeComposition(CompositionHBox[Double](AlignmentInParent.TopLeft), config(stroke = 0),
      List(rectangle(config(stroke = 12)))).renderWithMinimumDimension(rendering)
    val element = SvgLaminarRenderer.renderSvgImage(BasicLogger(), shape).ref
    val viewport = SvgViewport.boundsFor(shape)
    assertEquals(element.getAttribute("width").toDouble, viewport.width)
    assertEquals(element.getAttribute("height").toDouble, viewport.height)
    assertEquals(element.getAttribute("viewBox").split(" ").map(_.toDouble).toList,
      List(viewport.startX, viewport.startY, viewport.width, viewport.height))
    val child = shape.children.head.myBounds
    assert(viewport.startX <= child.startX - 6 && viewport.endX >= child.endX + 6)
    assert(viewport.startY <= child.startY - 6 && viewport.endY >= child.endY + 6)
    assertEquals(element.querySelector("path").getAttribute("stroke-miterlimit"), "4")
  }

  test("child clicks reach only the selected element and preserve primary-button identity") {
    var parentClicks = List.empty[Boolean]
    var leftClicks = List.empty[Boolean]
    var rightClicks = List.empty[Boolean]
    val shape = AppShapeComposition(CompositionHBox[Double](AlignmentInParent.TopLeft),
      config(value => parentClicks :+= value), List(
        rectangle(config(value => leftClicks :+= value)), rectangle(config(value => rightClicks :+= value))))
      .renderWithMinimumDimension(rendering)
    val element = SvgLaminarRenderer.renderSvgImage(BasicLogger(), shape)
    val mounted = render(dom.document.getElementById("root"), element)
    try {
      val paths = element.ref.querySelectorAll("path")
      click(paths(0), 0)
      click(paths(1), 2)
      assertEquals(leftClicks, List(true))
      assertEquals(rightClicks, List(false))
      assertEquals(parentClicks, Nil)
      click(element.ref.querySelector("g"), 0)
      assertEquals(parentClicks, List(true))
    } finally mounted.unmount()
  }

  test("atomic SVG clicks call their handler exactly once") {
    var clicks = 0
    val element = SvgLaminarRenderer.render(BasicLogger(), rectangle(config(_ => clicks += 1)))
    val mounted = render(dom.document.getElementById("root"), element)
    try {
      click(element.ref.querySelector("path"), 0)
      assertEquals(clicks, 1)
      click(element.ref, 0)
      assertEquals(clicks, 2)
    } finally mounted.unmount()
  }

  test("scaled text preserves its style, font color and individual click handler") {
    var clicks = 0
    val font = AppFont("Arial", 12, italic = true, bold = true, variant = "small-caps")
    val shape = AppShapeTextElement("abcd", config(_ => clicks += 1, stroke = 0, fontToUse = font))
      .renderComposition(rendering, Bounds(Point(100.0, 200.0), Dimension(12.0, 13.0)))
    val element = SvgLaminarRenderer.renderSvgImage(BasicLogger(), shape)
    val text = element.ref.querySelector("text")
    val fontSize = text.getAttribute("font-size").stripSuffix("px").toDouble
    val scale = fontSize / font.sizeInPx
    assert(scale < 1)
    assert(40 * scale <= shape.myBounds.width && 10 * scale <= shape.myBounds.height)
    assertEquals(text.getAttribute("font-weight"), "700")
    assertEquals(text.getAttribute("font-style"), "italic")
    assertEquals(text.getAttribute("font-variant"), "small-caps")
    assertEquals(text.getAttribute("fill"), RGBColor.yellow.toWebColor.webStyleHexString)
    assertEquals(text.getAttribute("dominant-baseline"), "hanging")
    assertEquals(text.getAttribute("x").toDouble, shape.myBounds.startX)
    val mounted = render(dom.document.getElementById("root"), element)
    try { click(text, 0); assertEquals(clicks, 1) } finally mounted.unmount()
  }

  test("empty text and missing canvas height metrics stay finite") {
    missingHeight = true
    try {
      val empty = AppShapeTextElement("", config(stroke = 0)).renderWithMinimumDimension(rendering)
      val text = AppShapeTextElement("abc", config(stroke = 0)).renderWithMinimumDimension(rendering)
      assertEquals(empty.myBounds.height, AppFont.defaultFont.sizeInPx)
      assertEquals(text.myBounds.height, AppFont.defaultFont.sizeInPx)
      List(empty, text).foreach { shape =>
        val element = SvgLaminarRenderer.renderSvgImage(BasicLogger(), shape).ref
        assert(!element.outerHTML.contains("NaN") && !element.outerHTML.contains("Infinity"))
      }
    } finally missingHeight = false
  }
}
