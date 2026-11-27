package it.evadid.core.datastructures.canvas

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.modifiers.KeySetter.SvgAttrSetter
import it.evadid.core.datastructures.color.{AppColor, RGBColor}
import it.evadid.core.datastructures.file.*
import it.evadid.core.datastructures.font.AppFont

class SvgCanvas(width: Int, height: Int) extends AppCanvas[Element] {

  def getDomElement(): Element = canvasElement

  private val elements: Var[List[Element]] = Var(List())

  private val canvasElement: Element = div(
    idAttr := "this-svg",
    children <-- elements.signal.map(elements => List(createSvg(elements)))
  )

  override def getCanvas: Element = {
    canvasElement
  }

  var fillColor: AppColor = setFillColor(RGBColor(0, 0, 0, 255))
  var strokeColor: AppColor = setStrokeColor(RGBColor(0, 0, 0, 255))


  private def createSvg(elements: List[Element]): Element =
    svg.svg(
      svg.width := "" + width,
      svg.height := "" + height,
      elements
    )

  def addSvgElement(element: L.SvgElement): Unit = elements.update(_ :+ element)

  /** Position and size attributes of rectangles and images. */
  private def bounds(x: Double, y: Double, width: Double, height: Double): Seq[SvgAttrSetter[String]] = List(
    svg.x := "" + x,
    svg.y := "" + y,
    svg.width := "" + width,
    svg.height := "" + height
  )

  private def circleGeometry(x: Double, y: Double, diameter: Double): Seq[SvgAttrSetter[String]] = List(
    svg.cx := "" + x,
    svg.cy := "" + y,
    svg.r := "" + diameter / 2
  )

  private def getStrokeConfig(
                               strokeWidth: Double,
                               dashPattern: Option[Seq[Double]] = None
                             ): Seq[SvgAttrSetter[String]] = {
    val base = List(
      svg.stroke := strokeColor.toRGB.toHex(),
      svg.strokeWidth := "" + strokeWidth,
      svg.fillOpacity := "0"
    )
    dashPattern.filter(_.nonEmpty) match {
      case Some(pattern) => base :+ (svg.strokeDashArray := pattern.mkString(" "))
      case None => base
    }
  }

  def getFillConfig(): Seq[SvgAttrSetter[String]] = List(
    svg.strokeWidth := "0",
    svg.fillOpacity := alphaStringFromCol(fillColor),
    svg.fill := fillColor.toRGB.toHex()
  )


  override def setFillColor(color: AppColor): AppColor = {
    fillColor = color
    color
  }

  override def getFillColor: AppColor = fillColor

  override def setStrokeColor(color: AppColor): AppColor = {
    strokeColor = color
    color
  }

  override def getStrokeColor: AppColor = strokeColor

  override def getWidth: Double = width

  override def getHeight: Double = height

  private def alphaStringFromCol(col: AppColor): String = "" + col.toRGB.alpha / 255.0


  override def fillRect(x: Double, y: Double, width: Double, height: Double): Unit =
    addSvgElement(svg.rect(bounds(x, y, width, height), getFillConfig()))


  override def drawRect(x: Double, y: Double, width: Double, height: Double, strokeWidth: Double = 1): Unit =
    addSvgElement(svg.rect(bounds(x, y, width, height), getStrokeConfig(strokeWidth)))


  private def calcArcStrings(x: Double, y: Double, diameter: Double, startAngle: Double, arcAngle: Double): (String, String) = {
    val radius = diameter / 2

    val (startX, startY) = AppCanvas.calcArcPoint(x, y, diameter, startAngle)
    val (endX, endY) = AppCanvas.calcArcPoint(x, y, diameter, startAngle + arcAngle)

    val largeArc = if (arcAngle > 180) "1" else "0"

    val arcString = f"M $startX $startY A $radius $radius 0 $largeArc 1 $endX $endY"
    val fillString = f"M $x $y L $startX $startY L $endX $endY" + arcString
    (arcString, fillString)
  }

  /**
   * Draws an Arc on the Canvas
   *
   * @param x           the x-Position of the circle-center
   * @param y           the y-Position of the circle-center
   * @param diameter    the diameter of the circle
   * @param startAngle  the start angle (0-360, 0 = top)
   * @param arcAngle    the arc angle (0-360, positive = clockwise)
   * @param strokeWidth the width of the stroke (-1 = default)
   */
  override def drawArc(x: Double, y: Double, diameter: Double, startAngle: Double, arcAngle: Double, strokeWidth: Double): Unit =
    addSvgElement(svg.path(
      svg.d := calcArcStrings(x, y, diameter, startAngle, arcAngle)._1,
      getStrokeConfig(strokeWidth)
    ))

  /**
   * Fills an Arc on the Canvas
   *
   * @param x          the x-Position of the circle-center
   * @param y          the y-Position of the circle-center
   * @param diameter   the diameter of the circle
   * @param startAngle the start angle (0-360, 0 = top)
   * @param arcAngle   the arc angle (0-360, positive = clockwise)
   */
  override def fillArc(x: Double, y: Double, diameter: Double, startAngle: Double, arcAngle: Double): Unit =
    addSvgElement(svg.path(
      svg.d := calcArcStrings(x, y, diameter, startAngle, arcAngle)._2,
      getFillConfig()
    ))


  override def drawLine(x1: Double, y1: Double, x2: Double, y2: Double, width: Double): Unit =
    addSvgElement(svg.line(
      svg.x1 := "" + x1,
      svg.y1 := "" + y1,
      svg.x2 := "" + x2,
      svg.y2 := "" + y2,
      getStrokeConfig(width)
    ))

  override def drawCubicBezier(
                                startX: Double,
                                startY: Double,
                                control1X: Double,
                                control1Y: Double,
                                control2X: Double,
                                control2Y: Double,
                                endX: Double,
                                endY: Double,
                                strokeWidth: Double,
                                dashPattern: Option[Seq[Double]]
                              ): Unit =
    addSvgElement(svg.path(
      svg.d := f"M $startX $startY C $control1X $control1Y $control2X $control2Y $endX $endY",
      getStrokeConfig(strokeWidth, dashPattern)
    ))

  override def drawCircle(x: Double, y: Double, diameter: Double, strokeWidth: Double): Unit =
    addSvgElement(svg.circle(circleGeometry(x, y, diameter), getStrokeConfig(strokeWidth)))

  override def fillCircle(x: Double, y: Double, diameter: Double): Unit =
    addSvgElement(svg.circle(circleGeometry(x, y, diameter), getFillConfig()))

  override def drawImage(x: Double, y: Double, width: Double, height: Double, image: AppImage, alphaUpTo255: Double = 255): Unit =
    addSvgElement(svg.image(
      bounds(x, y, width, height),
      svg.href := image.imageSourceString,
      svg.opacity := s"${alphaUpTo255 / 255.0}",
      svg.preserveAspectRatio := "none"
    ))

  private var selectedFont = AppFont.Aptos

  override def setFont(font: AppFont): Unit = selectedFont = font


  //src: url(data:font/woff2;charset=utf-8;base64,$base64Font) format('woff2');
  override def drawStringCentered(x: Double, y: Double, content: String): Unit =
    addSvgElement(svg.text(
      svg.x := "" + x,
      svg.y := "" + y,
      svg.textAnchor := "middle",
      svg.dominantBaseline := "middle",
      svg.fontFamily := selectedFont.name,
      svg.fontSize := s"${selectedFont.sizeInPx}px",
      svg.fill := fillColor.toRGB.toHex(),
      content
    ))

  override def clear(clearColor: AppColor): Unit = {
    elements.update(_ => List())
    val tmp = fillColor
    setFillColor(clearColor)
    fillRect(0, 0, width, height)
    setFillColor(tmp)
  }
}
