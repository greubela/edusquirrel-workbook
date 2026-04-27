package contentmanagement.webElements.svg

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.codecs.StringAsIsCodec
import com.raquo.laminar.nodes.ReactiveHtmlElement
import contentmanagement.webElements.svg.atomarElements.{AppCircleSvgElement, AppPathSvgElement, AppRectangleSvgElement, AppTextSvgElement}
import contentmanagement.webElements.svg.compositeElements.{AppDecoratedSvgElement, AppGroupSvgElement}

import scala.math.BigDecimal.RoundingMode
import com.raquo.laminar.keys.{StyleProp, SvgAttr}
import datastructures.web.font.AppFont
import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}

// todo: verify overlays and finish for other elements... move to subclasses?
trait AppSvgElement {

  def staticBoundingBox: Bounds[Double]

  def mods: Seq[L.Modifier[L.SvgElement]]

  def signalMods: Seq[Signal[L.Modifier[L.SvgElement]]]

  def addMods(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement

  def addSignalMods(newMods: Seq[Signal[L.Modifier[L.SvgElement]]]): AppSvgElement

  def addModsToAll(newMods: Seq[L.Modifier[L.SvgElement]]): AppSvgElement

  def removeAllMods(): AppSvgElement

  def map(func: AppSvgElement => AppSvgElement): AppSvgElement

  def withOverlayInformation: AppDecoratedSvgElement =
    this match {
      case path: AppPathSvgElement[?] =>
        AppSvgElement.decoratePath(path, this)
      case _ =>
        withOverlayInformation(_ => List(), _ => List())
    }


  def withOverlayInformation(
      overlays: Var[Boolean] => List[AppSvgElement],
      underlays: Var[Boolean] => List[AppSvgElement],
      overlayMods: Seq[L.Modifier[L.SvgElement]] = AppSvgElement.DefaultOverlayMods,
      underlayMods: Seq[L.Modifier[L.SvgElement]] = AppSvgElement.DefaultUnderlayMods
  ): AppDecoratedSvgElement = {
    val isHovered = Var(false)
    val visibilitySignal = isHovered.signal.map(AppSvgElement.overlayVisibilityModifier)

    val mainWithHover = this.addMods(
      Seq(
        onPointerEnter.mapTo(true) --> isHovered.writer,
        onPointerLeave.mapTo(false) --> isHovered.writer
      )
    )

    val overlayElementsRaw = overlays(isHovered)
    val overlayElements: List[AppSvgElement] =
      if (overlayElementsRaw.isEmpty) List()
      else
        List(
          AppGroupSvgElement(
            overlayElementsRaw.map(_.addMods(overlayMods)),
            mods = AppSvgElement.DefaultOverlayContainerMods ++ AppSvgElement.overlayContainerHoverMods(isHovered),
            signalMods = List(visibilitySignal)
          )
        )

    val underlayElementsRaw = underlays(isHovered)
    val underlayElements: List[AppSvgElement] =
      if (underlayElementsRaw.isEmpty) List()
      else
        List(
          AppGroupSvgElement(
            underlayElementsRaw.map(_.addMods(underlayMods)),
            mods = AppSvgElement.DefaultUnderlayContainerMods,
            signalMods = List(visibilitySignal)
          )
        )

    AppDecoratedSvgElement(mainWithHover, overlayElements, underlayElements)
  }

  def renderWithMods: L.SvgElement = {
    val element: L.SvgElement = renderBeforeMods.amend(mods)

    def signalMod(sig: Signal[L.Modifier[L.SvgElement]]): L.Modifier[L.SvgElement] = {
      sig --> L.Observer[L.Modifier[L.SvgElement]](m => element.amend(m))
    }

    element.amend(signalMods.map(signalMod))
  }


  def renderBeforeMods: L.SvgElement

  def toPlainDisplayDiv: L.HtmlElement = L.div(
    svg.svg(
      svg.width := "" + staticBoundingBox.width,
      svg.height := "" + staticBoundingBox.height,
      svg.x := "0",
      svg.y := "0",
      renderWithMods
    )
  )

  /*
  def makeClickable(onClick: MouseEvent => Any): AppSvgElement =
  addMods(List(
  L.onClick --> { event => onClick(event) },
  onContextMenu.preventDefault --> { event => {} }
  ))

  def makeDroppable(onElementDropped: MouseEvent => Any): AppSvgElement =
  addMods(List(
  L.onDragOver.preventDefault --> (_ => ()), // allow dropping on this element
  L.onDrop.preventDefault --> (e => onElementDropped(e))
  ))

  def makeMouseAware(onEnter: MouseEvent => Any, onLeave: MouseEvent => Any): AppSvgElement =
  addMods(List(
  L.onPointerEnter --> (e => onEnter(e)),
  L.onPointerLeave --> (e => {
    println("!?!?!?!?!? -> " + onLeave)
    onLeave(e)
  })
  ))
  */
  def flatten: List[AppSvgElement]
}
object AppSvgElement {
  // ---- custom SVG attributes (note the third param = None) ----
  private val pointerEventsAttr   = SvgAttr("pointer-events",   StringAsIsCodec, None)
  private val strokeDasharrayAttr = SvgAttr("stroke-dasharray", StringAsIsCodec, None)
  private val cursorAttr          = SvgAttr("cursor",           StringAsIsCodec, None)

  // ---- convenience modifiers using those attrs ----
  private val pointerEventsNone: L.Modifier[L.SvgElement] =
    pointerEventsAttr := "none"
  private val pointerEventsVisiblePainted: L.Modifier[L.SvgElement] =
    pointerEventsAttr := "visiblePainted"

  val DefaultOverlayContainerMods: Seq[L.Modifier[L.SvgElement]] =
    List(svg.visibility := "hidden")

  val DefaultUnderlayContainerMods: Seq[L.Modifier[L.SvgElement]] =
    List(svg.visibility := "hidden", pointerEventsNone)

  val DefaultOverlayMods: Seq[L.Modifier[L.SvgElement]] =
    List(svg.fill := "#e53935", svg.stroke := "#e53935", svg.strokeWidth := "2")

  // Replace L.style("stroke-dasharray") with our custom attr
  val DefaultUnderlayMods: Seq[L.Modifier[L.SvgElement]] =
    List(svg.stroke := "#e53935", svg.fill := "none", strokeDasharrayAttr := "4 2")

  private[svg] def overlayVisibilityModifier(isVisible: Boolean): L.Modifier[L.SvgElement] =
    if (isVisible) svg.visibility := "visible" else svg.visibility := "hidden"

  private[svg] def overlayContainerHoverMods(isHovered: Var[Boolean]): Seq[L.Modifier[L.SvgElement]] =
    Seq(
      pointerEventsVisiblePainted,
      onPointerEnter.mapTo(true)  --> isHovered.writer,
      onPointerLeave.mapTo(false) --> isHovered.writer
    )

  private[svg] def hoverPersistenceMods(isHovered: Var[Boolean]): Seq[L.Modifier[L.SvgElement]] =
    Seq(
      pointerEventsVisiblePainted,
      onPointerEnter.mapTo(true)  --> isHovered.writer,
      onPointerLeave.mapTo(false) --> isHovered.writer
    )

  private val coordinateFont: AppFont = AppFont.Aptos.copy(sizeInPx = 14)
  private val coordinatePadding: Double = 6.0
  private val coordinateOffsetX: Double = 14.0
  private val coordinateMinDimension: Dimension[Double] = Dimension(48.0, 24.0)
  private val coordinateBackgroundMods: Seq[L.Modifier[L.SvgElement]] =
    List(svg.fill := "#ffffff", svg.stroke := "#424242", svg.strokeWidth := "1", svg.rx := "4", svg.ry := "4")
  private val coordinateTextMods: Seq[L.Modifier[L.SvgElement]] =
    List(svg.fill := "#212121")
  private val cornerPointRadius: Double = 4.5

  private val cornerPointMods: Seq[L.Modifier[L.SvgElement]] =
    List(svg.fill := "#e53935", svg.stroke := "#b71c1c", svg.strokeWidth := "1.5", cursorAttr := "pointer")

  private val controlLineMods: Seq[L.Modifier[L.SvgElement]] =
    List(svg.stroke := "#d32f2f", strokeDasharrayAttr := "6 4", svg.strokeWidth := "1.5", svg.opacity := "0.75", pointerEventsNone)

  private val highlightPathMods: Seq[L.Modifier[L.SvgElement]] =
    List(svg.stroke := "#ff7043", svg.strokeWidth := "6", svg.fill := "none", svg.opacity := "0.5", pointerEventsNone)

  private def decoratePath[T](path: AppPathSvgElement[T], original: AppSvgElement): AppDecoratedSvgElement =
    original.withOverlayInformation(
      overlays = isHovered => pathCornerPointOverlays(path, isHovered),
      underlays = isHovered => pathUnderlays(path),
      overlayMods = Seq.empty,
      underlayMods = Seq.empty
    )

  private def pathUnderlays[T](path: AppPathSvgElement[T]): List[AppSvgElement] = {
    val highlight = path.removeAllMods().addMods(highlightPathMods)
    val controls = path.controlLines.map(_.removeAllMods().addMods(controlLineMods))
    highlight :: controls
  }

  private def pathCornerPointOverlays[T](path: AppPathSvgElement[T], isHovered: Var[Boolean]): List[AppSvgElement] =
    path.cornerPoints.map { point =>
      val pointDouble = point.toDouble
      val label = coordinateLabel(pointDouble, isHovered)
      AppCircleSvgElement(pointDouble, cornerPointRadius)
        .addMods(cornerPointMods ++ hoverPersistenceMods(isHovered))
        .withOverlayInformation(
          overlays = _ => List(label),
          underlays = _ => List(),
          overlayMods = Seq.empty,
          underlayMods = Seq.empty
        )
    }

  private def coordinateLabel(point: Point[Double], isHovered: Var[Boolean]): AppSvgElement = {
    val coordinateText = s"${formatCoordinateComponent(point.x)}, ${formatCoordinateComponent(point.y)}"
    val measured = coordinateFont.measureText(coordinateText).toDouble
    val contentDimension = Dimension[Double](
      measured.width + coordinatePadding * 2,
      measured.height + coordinatePadding * 2
    ).ensureAtLeastAsBigAs(coordinateMinDimension)
    val startPoint = Point[Double](point.x + coordinateOffsetX, point.y - contentDimension.height / 2)
    val bounds = Bounds(startPoint, contentDimension)
    val background = AppRectangleSvgElement(bounds).addMods(coordinateBackgroundMods)
    val text = AppTextSvgElement(coordinateText, bounds, coordinateFont).addMods(coordinateTextMods)
    AppGroupSvgElement(List(background, text)).addMods(hoverPersistenceMods(isHovered))
  }

  private def formatCoordinateComponent(value: Double): String = {
    val decimal = BigDecimal(value).setScale(4, RoundingMode.HALF_UP).bigDecimal.stripTrailingZeros()
    decimal.toPlainString
  }
}

