package util.JSXGraph

import contentmanagement.model.geometry.Point
import contentmanagement.webElements.svg.atomarElements.{AppCircleSvgElement, AppLineSvgElement}
import munit.FunSuite
import scala.scalajs.js

class JsxGraphFacadeSpec extends FunSuite:
  given JsValueConverter[Double] = JsValueConverter.defaultConverter[Double]

  test("BoardOptions toString lists bounding box and renderer") {
    val options = BoardOptions[Double](
      boundingBox = Some(BoundingBox(0.0, 1.0, 2.0, -1.0)),
      grid = true,
      renderer = Renderer.Canvas
    )
    val repr = options.toString
    assert(repr.contains("BoundingBox("))
    assert(repr.contains("grid=true"))
    assert(repr.contains("renderer=canvas"))
  }

  test("Facade creates elements with meaningful toString output") {
    val facade = new JsxGraphFacade[Double](RecordingBridge())
    val board = facade.initBoard("container")

    val pointA = board.createPoint(Coordinate(0.0, 0.0), PointAttributes(CommonAttributes(name = Some("A"))))
    val pointB = board.createPoint(Coordinate(1.5, 2.0), PointAttributes(CommonAttributes(name = Some("B"))))

    val line = board.createLine(pointA, pointB, LineAttributes(CommonAttributes(name = Some("AB"))))
    val circle = board.createCircle(pointA, pointB, CircleAttributes(CommonAttributes(name = Some("circleAB"))))
    val text = board.createText(Coordinate(0.5, 0.5), "Center", TextAttributes(common = CommonAttributes(name = Some("label")), fontSize = Some(12.0)))
    val curve = board.createCurve((x: Double) => x, (y: Double) => y * y, CurveAttributes(CommonAttributes(name = Some("parabola"))))

    assert(pointA.toString.startsWith("JsxPoint(name=A"))
    assert(line.toString.contains("from=A"))
    assert(circle.toString.contains("through=B"))
    assert(text.toString.contains("text=Center"))
    assert(curve.toString.contains("parabola"))
    assert(board.toString.contains("JsxBoard"))
  }

  test("Facade overloads accept geometry and svg types") {
    val facade = new JsxGraphFacade[Double](RecordingBridge())
    val board = facade.initBoard("container")

    val geoStart = Point(0.0, 0.0)
    val geoEnd = Point(2.0, 3.0)
    val geoLine = board.createLine(geoStart, geoEnd, LineAttributes(CommonAttributes(name = Some("geoLine"))))

    val svgLine = AppLineSvgElement(geoStart, geoEnd)
    val svgLineWrapped = board.createLine(svgLine, LineAttributes(CommonAttributes(name = Some("svgLine"))))

    val circleSvg = AppCircleSvgElement(center = Point(1.0, 1.0), radius = 2.0)
    val jsxCircle = board.createCircle(circleSvg, CircleAttributes(CommonAttributes(name = Some("circleFromSvg"))))

    val geoPoint = Point(4.0, 5.0)
    val textFromPoint = board.createText(geoPoint, "Hello", TextAttributes(common = CommonAttributes(name = Some("text"))))

    assert(geoLine.toString.contains("geoLine"))
    assert(svgLineWrapped.toString.contains("svgLine"))
    assert(jsxCircle.toString.contains("circleFromSvg"))
    assert(textFromPoint.toString.contains("Hello"))
  }

  test("Point coordinates can be updated and boards freed") {
    val bridge = RecordingBridge()
    val facade = new JsxGraphFacade[Double](bridge)
    val board = facade.initBoard("demo")

    val point = board.createPoint(Coordinate(1.0, 1.0), PointAttributes(CommonAttributes(name = Some("P1"))))
    point.moveTo(Coordinate(3.5, -2.0))

    assertEqualsDouble(point.x, 3.5, 0.0)
    assertEqualsDouble(point.y, -2.0, 0.0)

    board.free()
    assertEquals(bridge.freedBoards, List("recording-board-demo"))
  }

private final class RecordingBridge extends JSXGraphBridge:
  private val freedBoardIds = scala.collection.mutable.ListBuffer.empty[String]

  override def initBoard(containerId: String, options: js.Dictionary[Any]): JSXBoardNative = RecordingBoard(containerId).asInstanceOf[JSXBoardNative]

  override def freeBoard(board: JSXBoardNative): Unit =
    freedBoardIds += board.asInstanceOf[RecordingBoard].id

  def freedBoards: List[String] = freedBoardIds.toList

private final class RecordingBoard(containerId: String) extends js.Object:
  val id: String = s"recording-board-$containerId"

  def create(elementType: String, parents: js.Array[Any], attributes: js.UndefOr[js.Dictionary[Any]]): js.Any =
    elementType match
      case ElementKind.Point.jsxName =>
        val xCoord = parents(0).asInstanceOf[Double]
        val yCoord = parents(1).asInstanceOf[Double]
        val point = new js.Object:
          val id: String = s"p-$xCoord,$yCoord"
          val name: String = pickName(attributes, "P")
          private var x: Double = xCoord
          private var y: Double = yCoord
          val X: js.Function0[Double] = () => x
          val Y: js.Function0[Double] = () => y
          def setPosition(method: Int, coords: js.Array[Double], doRound: js.UndefOr[Boolean]): Unit =
            x = coords(0)
            y = coords(1)
        point.asInstanceOf[js.Any]
      case ElementKind.Line.jsxName =>
        js.Dynamic.literal(
          id = "line-1",
          name = pickName(attributes, "Line")
        ).asInstanceOf[js.Any]
      case ElementKind.Circle.jsxName =>
        js.Dynamic.literal(
          id = "circle-1",
          name = pickName(attributes, "Circle")
        ).asInstanceOf[js.Any]
      case ElementKind.Text.jsxName =>
        val content = parents(2).asInstanceOf[String]
        js.Dynamic.literal(
          id = "text-1",
          name = pickName(attributes, "Text"),
          text = content
        ).asInstanceOf[js.Any]
      case ElementKind.Curve.jsxName =>
        js.Dynamic.literal(
          id = "curve-1",
          name = pickName(attributes, "Curve")
        ).asInstanceOf[js.Any]
      case other => throw new IllegalArgumentException(s"Unexpected element: $other")

  def update(): Unit = ()

  def fullUpdate(): Unit = ()

  private def pickName(attributes: js.UndefOr[js.Dictionary[Any]], defaultValue: String): String =
    attributes.toOption.flatMap(_.get("name")).map(_.toString).getOrElse(defaultValue)
