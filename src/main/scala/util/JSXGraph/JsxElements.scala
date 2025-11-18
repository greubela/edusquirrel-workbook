package util.JSXGraph

import scala.scalajs.js

sealed trait JsxElement[T]:
  type Native <: JSXGeometryElementNative
  val native: Native
  def id: String = native.id
  def name: String = native.name.toOption.getOrElse(native.id)

final case class JsxPoint[T: Fraction](native: JSXPointNative, attributes: PointAttributes = PointAttributes()) extends JsxElement[T]:
  override type Native = JSXPointNative
  def x: Double = native.X()
  def y: Double = native.Y()
  def moveTo(coordinate: Coordinate[T])(using converter: JsValueConverter[T]): Unit =
    val coordsByUser =
      if js.typeOf(js.Dynamic.global.selectDynamic("JXG")) != "undefined" then
        js.Dynamic.global.selectDynamic("JXG").selectDynamic("COORDS_BY_USER").asInstanceOf[Int]
      else 0
    native.setPosition(coordsByUser, coordinate.toJsTuple)
  override def toString: String = s"JsxPoint(name=$name, x=$x, y=$y)"

final case class JsxLine[T: Fraction](native: JSXLineNative, from: JsxPoint[T], to: JsxPoint[T], attributes: LineAttributes = LineAttributes()) extends JsxElement[T]:
  override type Native = JSXLineNative
  override def toString: String = s"JsxLine(name=$name, from=${from.name}, to=${to.name})"

final case class JsxCircle[T: Fraction](native: JSXCircleNative, center: JsxPoint[T], through: JsxPoint[T], attributes: CircleAttributes = CircleAttributes()) extends JsxElement[T]:
  override type Native = JSXCircleNative
  override def toString: String = s"JsxCircle(name=$name, center=${center.name}, through=${through.name})"

final case class JsxText[T: Fraction](native: JSXTextNative, position: Coordinate[T], content: String, attributes: TextAttributes = TextAttributes()) extends JsxElement[T]:
  override type Native = JSXTextNative
  override def toString: String = s"JsxText(name=$name, position=$position, text=$content)"

final case class JsxCurve[T: Fraction](native: JSXCurveNative, xFunction: js.Function1[Double, Double], yFunction: js.Function1[Double, Double], attributes: CurveAttributes = CurveAttributes()) extends JsxElement[T]:
  override type Native = JSXCurveNative
  override def toString: String = s"JsxCurve(name=$name)"
