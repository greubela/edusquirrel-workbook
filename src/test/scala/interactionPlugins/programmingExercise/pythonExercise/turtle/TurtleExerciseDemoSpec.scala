package interactionPlugins.programmingExercise.pythonExercise.turtle

import contentmanagement.model.AppFont
import contentmanagement.model.color.{AppColor, RGBColor}
import contentmanagement.model.file.FullImage
import contentmanagement.webElements.genericHtmlElements.canvas.AppCanvas
import munit.FunSuite
import scala.scalajs.js
import scala.util.Try

class TurtleExerciseDemoSpec extends FunSuite {

  private def installAlgebriteMock(): Unit = {
    val globalThis = js.Dynamic.global.eval("globalThis")
    val existing = globalThis.selectDynamic("Algebrite")
    if (js.isUndefined(existing) || existing == null) {
      val mock =
        js.Dynamic.literal(
          run = { (src: String) =>
            val expression = src
              .replace("simplify((", "")
              .replace("float((", "")
              .replace("))", "")
              .trim
            Try(js.Dynamic.global.eval(expression).toString).getOrElse(expression)
          }
        )
      globalThis.updateDynamic("Algebrite")(mock)
      js.Dynamic.global.updateDynamic("Algebrite")(mock)
    }
  }

  private final class RecordingCanvas(width: Double, height: Double) extends AppCanvas[Unit] {
    case class Line(x1: Double, y1: Double, x2: Double, y2: Double, strokeWidth: Double)

    var lines: List[Line] = Nil
    var clears: Int = 0
    private var fillColor: AppColor = RGBColor.white
    private var strokeColor: AppColor = RGBColor.black

    override def getDomElement() = null
    override def getCanvas: Unit = ()
    override def getWidth: Double = width
    override def getHeight: Double = height
    override def setFillColor(color: AppColor): AppColor = { fillColor = color; color }
    override def getFillColor: AppColor = fillColor
    override def getStrokeColor: AppColor = strokeColor
    override def setStrokeColor(color: AppColor): AppColor = { strokeColor = color; color }
    override def drawRect(x: Double, y: Double, width: Double, height: Double, strokeWidth: Double): Unit = ()
    override def fillRect(x: Double, y: Double, width: Double, height: Double): Unit = ()
    override def drawArc(x: Double, y: Double, diameter: Double, startAngle: Double, arcAngle: Double, strokeWidth: Double): Unit = ()
    override def fillArc(x: Double, y: Double, diameter: Double, startAngle: Double, arcAngle: Double): Unit = ()
    override def drawLine(x1: Double, y1: Double, x2: Double, y2: Double, strokeWidth: Double): Unit =
      lines = lines :+ Line(x1, y1, x2, y2, strokeWidth)
    override def drawCubicBezier(startX: Double, startY: Double, control1X: Double, control1Y: Double, control2X: Double, control2Y: Double, endX: Double, endY: Double, strokeWidth: Double, dashPattern: Option[Seq[Double]]): Unit = ()
    override def drawCircle(x: Double, y: Double, diameter: Double, strokeWidth: Double): Unit = ()
    override def fillCircle(x: Double, y: Double, diameter: Double): Unit = ()
    override def drawImage(x: Double, y: Double, width: Double, height: Double, img: FullImage, alphaUpTo255: Double): Unit = ()
    override def setFont(font: AppFont): Unit = ()
    override def drawStringCentered(x: Double, y: Double, content: String): Unit = ()
    override def clear(clearColor: AppColor): Unit = clears += 1
  }

  test("simple turtle-like program updates state and draws two segments") {
    installAlgebriteMock()
    val canvas = new RecordingCanvas(240, 240)
    val backend = new TurtleBackendImpl(canvas)
    val turtleId = backend.defaultTurtleId

    // Equivalent structure to Python snippet:
    // import turtle; x = 3; print(x); turtle.forward(100); turtle.left(90); turtle.forward(60)
    backend.turtleForward(turtleId, 100)
    backend.turtleLeft(turtleId, 90)
    backend.turtleForward(turtleId, 60)

    assertEqualsDouble(backend.turtleXCor(turtleId), 100.0, 0.0001)
    assertEqualsDouble(backend.turtleYCor(turtleId), 60.0, 0.0001)
    assertEquals(canvas.lines.length, 2)

    val first = canvas.lines.head
    assertEqualsDouble(first.x1, 120.0, 0.0001)
    assertEqualsDouble(first.y1, 120.0, 0.0001)
    assertEqualsDouble(first.x2, 220.0, 0.0001)
    assertEqualsDouble(first.y2, 120.0, 0.0001)

    val second = canvas.lines(1)
    assertEqualsDouble(second.x1, 220.0, 0.0001)
    assertEqualsDouble(second.y1, 120.0, 0.0001)
    assertEqualsDouble(second.x2, 220.0, 0.0001)
    assertEqualsDouble(second.y2, 60.0, 0.0001)
  }
}
