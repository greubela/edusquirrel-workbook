package util.JSXGraph

import scala.scalajs.js

final case class Coordinate[T: Fraction](x: T, y: T)(using converter: JsValueConverter[T]):
  def toJsTuple: js.Array[Double] = js.Array(converter.toJs(x), converter.toJs(y))
  override def toString: String = s"Coordinate(${converter.format(x)}, ${converter.format(y)})"

final case class BoundingBox[T: Fraction](xMin: T, yMax: T, xMax: T, yMin: T)(using converter: JsValueConverter[T]):
  def toJsArray: js.Array[Double] = js.Array(converter.toJs(xMin), converter.toJs(yMax), converter.toJs(xMax), converter.toJs(yMin))
  override def toString: String = s"BoundingBox(${converter.format(xMin)}, ${converter.format(yMax)}, ${converter.format(xMax)}, ${converter.format(yMin)})"

final case class NavigationOptions(enablePan: Boolean = true, enableZoom: Boolean = true):
  def toJsObject: js.Dictionary[Any] =
    val dict = js.Dictionary[Any]()
    dict.update("pan", enablePan)
    dict.update("zoom", enableZoom)
    dict
  override def toString: String = s"Navigation(pan=$enablePan, zoom=$enableZoom)"

final case class BoardOptions[T: Fraction](
    boundingBox: Option[BoundingBox[T]] = None,
    axis: Boolean = true,
    grid: Boolean = false,
    keepAspectRatio: Boolean = true,
    showNavigation: Boolean = true,
    navigation: NavigationOptions = NavigationOptions(),
    showCopyright: Boolean = false,
    renderer: Renderer = Renderer.SVG
)(using converter: JsValueConverter[T]):

  def toJsDictionary: js.Dictionary[Any] =
    val dict = js.Dictionary[Any](
      "axis" -> axis,
      "grid" -> grid,
      "keepaspectratio" -> keepAspectRatio,
      "shownavigation" -> showNavigation,
      "navigation" -> navigation.toJsObject,
      "showcopyright" -> showCopyright,
      "renderer" -> renderer.renderName
    )
    boundingBox.foreach(bbox => dict.update("boundingbox", bbox.toJsArray))
    dict

  override def toString: String =
    val bboxString = boundingBox.map(_.toString).getOrElse("auto")
    s"BoardOptions(boundingBox=$bboxString, axis=$axis, grid=$grid, renderer=${renderer.renderName})"

sealed trait Renderer:
  def renderName: String
  override def toString: String = renderName

object Renderer:
  case object SVG extends Renderer:
    override val renderName: String = "svg"
  case object Canvas extends Renderer:
    override val renderName: String = "canvas"
  case object VML extends Renderer:
    override val renderName: String = "vml"

  def fromName(name: String): Renderer =
    name.toLowerCase match
      case "svg" => SVG
      case "canvas" => Canvas
      case "vml" => VML
      case other => throw IllegalArgumentException(s"Unsupported renderer '$other'")

sealed trait ElementAttributes:
  def toJsAttributes: js.Dictionary[Any]

final case class CommonAttributes(
    name: Option[String] = None,
    strokeColor: Option[String] = None,
    fillColor: Option[String] = None,
    size: Option[Double] = None,
    fixed: Option[Boolean] = None,
    visible: Option[Boolean] = None,
    dash: Option[Int] = None
) extends ElementAttributes:
  override def toJsAttributes: js.Dictionary[Any] =
    val dict = js.Dictionary[Any]()
    CommonAttributes.write(dict, this)
    dict

  override def toString: String =
    val builder = List(
      name.map(n => s"name=$n"),
      strokeColor.map(c => s"stroke=$c"),
      fillColor.map(c => s"fill=$c"),
      size.map(s => s"size=$s"),
      fixed.map(f => s"fixed=$f"),
      visible.map(v => s"visible=$v"),
      dash.map(d => s"dash=$d")
    ).flatten.mkString(", ")
    if builder.isEmpty then "CommonAttributes()" else s"CommonAttributes($builder)"

object CommonAttributes:
  private def putIfDefined[A](dict: js.Dictionary[Any], key: String, value: Option[A]): Unit = value.foreach(v => dict.update(key, v))

  def write(target: js.Dictionary[Any], attrs: CommonAttributes): Unit =
    putIfDefined(target, "name", attrs.name)
    putIfDefined(target, "strokeColor", attrs.strokeColor)
    putIfDefined(target, "fillColor", attrs.fillColor)
    putIfDefined(target, "size", attrs.size)
    putIfDefined(target, "fixed", attrs.fixed)
    putIfDefined(target, "visible", attrs.visible)
    putIfDefined(target, "dash", attrs.dash)

final case class PointAttributes(common: CommonAttributes = CommonAttributes(), showInfobox: Option[Boolean] = None) extends ElementAttributes:
  override def toJsAttributes: js.Dictionary[Any] =
    val dict = common.toJsAttributes
    showInfobox.foreach(value => dict.update("showInfobox", value))
    dict

  override def toString: String =
    val infobox = showInfobox.map(v => s", showInfobox=$v").getOrElse("")
    s"PointAttributes(${common.toString}$infobox)"

final case class LineAttributes(common: CommonAttributes = CommonAttributes(), straightFirst: Option[Boolean] = None, straightLast: Option[Boolean] = None) extends ElementAttributes:
  override def toJsAttributes: js.Dictionary[Any] =
    val dict = common.toJsAttributes
    straightFirst.foreach(value => dict.update("straightFirst", value))
    straightLast.foreach(value => dict.update("straightLast", value))
    dict

  override def toString: String =
    val details = List(
      straightFirst.map(v => s"straightFirst=$v"),
      straightLast.map(v => s"straightLast=$v")
    ).flatten
    if details.isEmpty then s"LineAttributes(${common.toString})"
    else s"LineAttributes(${common.toString}, ${details.mkString(", ")})"

final case class CircleAttributes(common: CommonAttributes = CommonAttributes()) extends ElementAttributes:
  override def toJsAttributes: js.Dictionary[Any] = common.toJsAttributes
  override def toString: String = s"CircleAttributes(${common.toString})"

final case class TextAttributes(
    anchor: Option[Coordinate[Double]] = None,
    common: CommonAttributes = CommonAttributes(),
    fontSize: Option[Double] = None,
    strokeWidth: Option[Double] = None
) extends ElementAttributes:
  override def toJsAttributes: js.Dictionary[Any] =
    val dict = common.toJsAttributes
    anchor.foreach(value => dict.update("anchor", value.toJsTuple))
    fontSize.foreach(value => dict.update("fontSize", value))
    strokeWidth.foreach(value => dict.update("strokeWidth", value))
    dict

  override def toString: String =
    val details = List(
      anchor.map(a => s"anchor=$a"),
      fontSize.map(s => s"fontSize=$s"),
      strokeWidth.map(w => s"strokeWidth=$w")
    ).flatten.mkString(", ")
    if details.isEmpty then s"TextAttributes(${common.toString})" else s"TextAttributes(${common.toString}, $details)"

final case class CurveAttributes(common: CommonAttributes = CommonAttributes()) extends ElementAttributes:
  override def toJsAttributes: js.Dictionary[Any] = common.toJsAttributes
  override def toString: String = s"CurveAttributes(${common.toString})"

enum ElementKind(val jsxName: String):
  case Point extends ElementKind("point")
  case Line extends ElementKind("line")
  case Circle extends ElementKind("circle")
  case Text extends ElementKind("text")
  case Curve extends ElementKind("curve")
