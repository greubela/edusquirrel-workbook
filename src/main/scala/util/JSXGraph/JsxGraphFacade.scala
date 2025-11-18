package util.JSXGraph

import contentmanagement.model.geometry.{Point as GeoPoint}
import contentmanagement.webElements.svg.atomarElements.{AppCircleSvgElement, AppLineSvgElement}
import scala.scalajs.js

trait JSXGraphBridge:
  def initBoard(containerId: String, options: js.Dictionary[Any]): JSXBoardNative
  def freeBoard(board: JSXBoardNative): Unit

object JSXGraphBridge:
  val global: JSXGraphBridge = new JSXGraphBridge:
    override def initBoard(containerId: String, options: js.Dictionary[Any]): JSXBoardNative = JSXGraphNative.initBoard(containerId, options)
    override def freeBoard(board: JSXBoardNative): Unit = JSXGraphNative.freeBoard(board)

final class JsxGraphFacade[T: Fraction](bridge: JSXGraphBridge = JSXGraphBridge.global)(using converter: JsValueConverter[T]):
  def initBoard(containerId: String, options: BoardOptions[T] = BoardOptions[T]()): JsxBoard[T] =
    val nativeBoard = bridge.initBoard(containerId, options.toJsDictionary)
    JsxBoard(containerId, options, nativeBoard, bridge)

final case class JsxBoard[T: Fraction](containerId: String, options: BoardOptions[T], native: JSXBoardNative, bridge: JSXGraphBridge)(using converter: JsValueConverter[T]):
  def createPoint(position: Coordinate[T], attributes: PointAttributes = PointAttributes()): JsxPoint[T] =
    val parents = js.Array[Any](converter.toJs(position.x), converter.toJs(position.y))
    val point = native.create(ElementKind.Point.jsxName, parents, attributes.toJsAttributes).asInstanceOf[JSXPointNative]
    JsxPoint(point, attributes)

  def createPoint(point: GeoPoint[T]): JsxPoint[T] =
    createPoint(Coordinate(point.x, point.y))

  def createPoint(point: GeoPoint[T], attributes: PointAttributes): JsxPoint[T] =
    createPoint(Coordinate(point.x, point.y), attributes)

  def createLine(from: JsxPoint[T], to: JsxPoint[T], attributes: LineAttributes = LineAttributes()): JsxLine[T] =
    val parents = js.Array[Any](from.native, to.native)
    val line = native.create(ElementKind.Line.jsxName, parents, attributes.toJsAttributes).asInstanceOf[JSXLineNative]
    JsxLine(line, from, to, attributes)

  def createLine(from: GeoPoint[T], to: GeoPoint[T]): JsxLine[T] =
    createLine(from, to, LineAttributes())

  def createLine(from: GeoPoint[T], to: GeoPoint[T], attributes: LineAttributes): JsxLine[T] =
    val fromPoint = createPoint(from)
    val toPoint = createPoint(to)
    createLine(fromPoint, toPoint, attributes)

  def createLine(line: AppLineSvgElement[T]): JsxLine[T] =
    createLine(line, LineAttributes())

  def createLine(line: AppLineSvgElement[T], attributes: LineAttributes): JsxLine[T] =
    createLine(line.startPoint, line.endPoint, attributes)

  def createCircle(center: JsxPoint[T], through: JsxPoint[T], attributes: CircleAttributes = CircleAttributes()): JsxCircle[T] =
    val parents = js.Array[Any](center.native, through.native)
    val circle = native.create(ElementKind.Circle.jsxName, parents, attributes.toJsAttributes).asInstanceOf[JSXCircleNative]
    JsxCircle(circle, center, through, attributes)

  def createCircle(center: GeoPoint[T], through: GeoPoint[T]): JsxCircle[T] =
    createCircle(center, through, CircleAttributes())

  def createCircle(center: GeoPoint[T], through: GeoPoint[T], attributes: CircleAttributes): JsxCircle[T] =
    val centerPoint = createPoint(center)
    val throughPoint = createPoint(through)
    createCircle(centerPoint, throughPoint, attributes)

  def createCircle(circle: AppCircleSvgElement[T]): JsxCircle[T] =
    createCircle(circle, CircleAttributes())

  def createCircle(circle: AppCircleSvgElement[T], attributes: CircleAttributes): JsxCircle[T] =
    val numeric = summon[Fraction[T]]
    import numeric.*
    val through = GeoPoint(circle.center.x + circle.radius, circle.center.y)
    createCircle(circle.center, through, attributes)

  def createText(position: Coordinate[T], content: String, attributes: TextAttributes = TextAttributes()): JsxText[T] =
    val parents = js.Array[Any](converter.toJs(position.x), converter.toJs(position.y), content)
    val text = native.create(ElementKind.Text.jsxName, parents, attributes.toJsAttributes).asInstanceOf[JSXTextNative]
    JsxText(text, position, content, attributes)

  def createText(position: GeoPoint[T], content: String): JsxText[T] =
    createText(position, content, TextAttributes())

  def createText(position: GeoPoint[T], content: String, attributes: TextAttributes): JsxText[T] =
    createText(Coordinate(position.x, position.y), content, attributes)

  def createCurve(x: js.Function1[Double, Double], y: js.Function1[Double, Double], attributes: CurveAttributes = CurveAttributes()): JsxCurve[T] =
    val parents = js.Array[Any](x, y)
    val curve = native.create(ElementKind.Curve.jsxName, parents, attributes.toJsAttributes).asInstanceOf[JSXCurveNative]
    JsxCurve(curve, x, y, attributes)

  def update(): Unit = native.update()

  def fullUpdate(): Unit = native.fullUpdate()

  def free(): Unit = bridge.freeBoard(native)

  override def toString: String = s"JsxBoard(id=$containerId, options=${options.toString})"
