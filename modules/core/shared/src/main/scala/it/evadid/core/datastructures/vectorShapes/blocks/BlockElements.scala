package it.evadid.core.datastructures.vectorShapes.blocks

import it.evadid.core.datastructures.color.AppColor
import it.evadid.core.datastructures.font.AppFont
import it.evadid.core.datastructures.geometry.{AspectRatio, Bounds, Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.{AppElementDimensioned, AppElementMeasured, AppElementPositioned, AppShapeComposition}
import it.evadid.core.datastructures.vectorShapes.abstractions.{AppShapeCompositeControl, AppShapeElement, DrawingRoutine}
import it.evadid.core.datastructures.vectorShapes.atomar.{AppShapeDrawingRoutineElement, AppShapeTextElement, TextPlacement}
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeElementConfig, AppShapeRenderingConfig}
import it.evadid.core.datastructures.vectorShapes.helper.{AlignmentInParent, RenderingDimension}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPath.BuilderBasedSvgPath
import it.evadid.core.datastructures.vectorShapes.svg.{SvgPath, SvgPathBuilder}
import it.evadid.util.logging.Logger
import it.evadid.vm.editing.{BeNodePath, BeSequenceSlot}

/** What an interactive element of a block drawing stands for. */
enum BlockRole {
  /** A statement that can be dragged to another slot. */
  case Statement(path: BeNodePath)
  /** A placeholder accepting dropped statements. */
  case Slot(slot: BeSequenceSlot)
  /** A value (parameter, repeat count, assigned value) edited as an expression. */
  case Value(path: BeNodePath)
}

/** Paint, font and editor role of one block element. Blocks manage their own spacing: no default padding. */
final case class BlockElementConfig(paint: BlockPaint, font: AppFont, padding: Dimension[Double] = Dimension(0.0, 0.0),
                                    role: Option[BlockRole] = None) extends AppShapeElementConfig[Double] {
  override def useCustomPadding: Option[Dimension[Double]] = Some(padding)
  override def colorStroke: AppColor = paint.stroke
  override def colorFill: AppColor = paint.fill
  override def colorFont: AppColor = paint.fill
  override def strokeWidth: Double = paint.strokeWidth
  override def fillEnabled: Boolean = paint.fillEnabled
  override def strokeDashArray: Option[String] = paint.dashArray
  override def onMouseClicked(leftButton: Boolean): Unit = ()
}

/** Blocks lay themselves out; the framework adds no gaps or padding. */
object BlockElements {
  val renderingConfig: AppShapeRenderingConfig[Double] = AppShapeRenderingConfig[Double](
    defaultPadding = Dimension(0.0, 0.0), defaultGapBetweenElements = Dimension(0.0, 0.0),
    gapBetweenConsecutiveShapes = Dimension(0.0, 0.0))

  /** A path filling the bounds it is given; `naturalSize` is its size when nothing is allocated around it. */
  final case class BoundsRoutine(draw: Bounds[Double] => SvgPathBuilder[Double]) extends DrawingRoutine[Double] {
    override def renderPath(logger: Logger, bounds: Bounds[Double]): SvgPath = BuilderBasedSvgPath(bounds, draw(bounds))
    override def appendPathToBuilder(logger: Logger, builder: SvgPathBuilder[Double], targetDimension: Dimension[Double]): SvgPathBuilder[Double] =
      draw(Bounds(builder.current, targetDimension))
    override def hasDesiredAspectRatio: Option[AspectRatio] = None
  }

  def path(draw: Bounds[Double] => SvgPathBuilder[Double], naturalSize: Dimension[Double], config: BlockElementConfig): AppShapeElement[Double] =
    AppShapeDrawingRoutineElement(BoundsRoutine(draw), config, Some(naturalSize))

  /** A path already in absolute coordinates; it is drawn wherever its element is placed. */
  def fixedPath(builder: SvgPathBuilder[Double], config: BlockElementConfig): AppShapeElement[Double] =
    path(_ => builder, builder.requiresDimension, config)

  /** Text centered in its measured size plus the small padding. */
  def text(value: String, rendering: BlockRenderingConfig, paint: BlockPaint): AppShapeElement[Double] =
    AppShapeTextElement(value, BlockElementConfig(paint, rendering.font, rendering.paddingSmall), TextPlacement.Centered)

  def composite(arrangement: BlockArrangement, children: List[AppShapeElement[Double]],
                config: BlockElementConfig): AppShapeElement[Double] =
    AppShapeComposition(BlockComposite(arrangement), config, children)

  /** The laid-out drawing at its natural size, with the origin at the top left. */
  def layout(element: AppShapeElement[Double]): AppShapeElement.AppElementRendered[Double] =
    element.renderWithMinimumDimension(renderingConfig)

  def size(element: AppShapeElement[Double]): Dimension[Double] = layout(element).outerBounds.dimension

  /** Every element with an editor role and its absolute bounds, outer elements first. */
  def roles(rendered: AppShapeElement.AppElementRendered[Double]): List[(BlockRole, Bounds[Double])] = {
    val own = rendered.elementConfig match {
      case BlockElementConfig(_, _, _, Some(role)) => List(role -> rendered.outerBounds)
      case _ => Nil
    }
    own ++ rendered.children.flatMap(roles)
  }
}

/** Positions children whose minimum sizes are known; bounds are relative to the composite's origin. */
trait BlockArrangement {
  def minimum(children: List[Dimension[Double]]): Dimension[Double]
  def place(children: List[Dimension[Double]], size: Dimension[Double]): List[Bounds[Double]]
}

/** Adapts a [[BlockArrangement]] to the shape framework's measure/dimension/position passes. */
final case class BlockComposite(arrangement: BlockArrangement) extends AppShapeCompositeControl[Double] {
  override def desiredAspectRatioAndAlignment: Option[(AspectRatio, AlignmentInParent)] = None

  override def calculateMyMinimumDimension(children: List[AppElementMeasured[Double]], config: AppShapeElementConfig[Double],
                                           rendering: AppShapeRenderingConfig[Double]): RenderingDimension[Double] =
    RenderingDimension.fromRawDimensionAndConfig(arrangement.minimum(children.map(_.minimumDimension.fullDimension)), config, rendering)

  override def calculateChildrenDimensions(children: List[AppElementMeasured[Double]], mySize: RenderingDimension[Double],
                                           config: AppShapeElementConfig[Double], rendering: AppShapeRenderingConfig[Double]): List[AppElementDimensioned[Double]] =
    arrangement.place(children.map(_.minimumDimension.fullDimension), mySize.rawDimension).zip(children).map { (bounds, child) =>
      child.withTargetDimension(RenderingDimension.fromFullDimensionAndConfig(bounds.dimension, child.baseElement.elementConfig, rendering))
    }

  override def calculateChildrenPositions(children: List[AppElementDimensioned[Double]], mySize: RenderingDimension[Double],
                                          config: AppShapeElementConfig[Double], rendering: AppShapeRenderingConfig[Double]): List[AppElementPositioned[Double]] =
    arrangement.place(children.map(_.compositionMeasurd.minimumDimension.fullDimension), mySize.rawDimension).zip(children)
      .map((bounds, child) => child.withOffset(bounds.startPoint))
}

enum Horizontal {
  case Left, Center, Right
  def offset(free: Double): Double = math.max(0.0, this match {
    case Left => 0.0
    case Center => free / 2
    case Right => free
  })
}

enum Vertical {
  case Top, Center, Bottom
  def offset(free: Double): Double = math.max(0.0, this match {
    case Top => 0.0
    case Center => free / 2
    case Bottom => free
  })
}

object BlockArrangement {

  /** A container outline (first child) sized around its content (second child), drawn at its natural size. */
  final case class AroundChild(container: BlockContainer, rendering: BlockRenderingConfig) extends BlockArrangement {
    def minimum(children: List[Dimension[Double]]): Dimension[Double] = container.sizeAround(children(1), rendering)
    def place(children: List[Dimension[Double]], size: Dimension[Double]): List[Bounds[Double]] = {
      val outer = container.sizeAround(children(1), rendering)
      List(Bounds(Point(0.0, 0.0), outer), Bounds(container.childOffset(children(1), outer, rendering), children(1)))
    }
  }

  /** Children left to right at their natural sizes, optionally with small padding around and between them. */
  final case class Row(rendering: BlockRenderingConfig, usePadding: Boolean = true,
                       horizontal: Horizontal = Horizontal.Center, vertical: Vertical = Vertical.Center) extends BlockArrangement {
    private def pad = if usePadding then rendering.paddingSmall else Dimension(0.0, 0.0)
    def minimum(children: List[Dimension[Double]]): Dimension[Double] =
      Dimension(children.map(_.width).sum + pad.width * (children.size + 1),
        children.map(_.height).maxOption.getOrElse(0.0) + pad.height * 2).ensureAtLeastAsBigAs(rendering.paddingSmall)
    def place(children: List[Dimension[Double]], size: Dimension[Double]): List[Bounds[Double]] = {
      val height = children.map(_.height).maxOption.getOrElse(0.0)
      val left = horizontal.offset(size.width - children.map(_.width).sum - pad.width * (children.size + 1)) + pad.width
      val top = vertical.offset(size.height - height - pad.height * 2) + pad.height
      val starts = children.scanLeft(left)((x, child) => x + child.width + pad.width)
      children.zip(starts).map((child, x) => Bounds(Point(x, top + vertical.offset(height - child.height)), child))
    }
  }

  /** Children top to bottom at their natural sizes, optionally with small padding around and between them. */
  final case class Column(rendering: BlockRenderingConfig, usePadding: Boolean = true,
                          horizontal: Horizontal = Horizontal.Center, vertical: Vertical = Vertical.Center) extends BlockArrangement {
    private def pad = if usePadding then rendering.paddingSmall else Dimension(0.0, 0.0)
    def minimum(children: List[Dimension[Double]]): Dimension[Double] =
      Dimension(children.map(_.width).maxOption.getOrElse(0.0) + pad.width * 2,
        children.map(_.height).sum + pad.height * (children.size + 1)).ensureAtLeastAsBigAs(rendering.paddingSmall)
    def place(children: List[Dimension[Double]], size: Dimension[Double]): List[Bounds[Double]] = {
      val width = children.map(_.width).maxOption.getOrElse(0.0)
      val left = horizontal.offset(size.width - width - pad.width * 2) + pad.width
      val top = vertical.offset(size.height - children.map(_.height).sum - pad.height * (children.size + 1)) + pad.height
      val starts = children.scanLeft(top)((y, child) => y + child.height + pad.height)
      children.zip(starts).map((child, y) => Bounds(Point(left + horizontal.offset(width - child.width), y), child))
    }
  }

  /** Children stacked flush at the left, each at its natural size. */
  case object PlainColumn extends BlockArrangement {
    def minimum(children: List[Dimension[Double]]): Dimension[Double] =
      Dimension(children.map(_.width).maxOption.getOrElse(0.0), children.map(_.height).sum)
    def place(children: List[Dimension[Double]], size: Dimension[Double]): List[Bounds[Double]] =
      children.zip(children.scanLeft(0.0)(_ + _.height)).map((child, y) => Bounds(Point(0.0, y), child))
  }

  /** Children at precomputed bounds; the composite spans their extent. */
  final case class Fixed(bounds: List[Bounds[Double]]) extends BlockArrangement {
    def minimum(children: List[Dimension[Double]]): Dimension[Double] =
      Dimension(bounds.map(_.endX).maxOption.getOrElse(0.0), bounds.map(_.endY).maxOption.getOrElse(0.0))
    def place(children: List[Dimension[Double]], size: Dimension[Double]): List[Bounds[Double]] = bounds
  }

  /** Rows of a statement sequence: a control-flow column and expressions, or a nested structure spanning both. */
  final case class SequenceRows(rows: List[SequenceRow]) extends BlockArrangement {
    private def layout(children: List[Dimension[Double]]): List[Bounds[Double]] = {
      val (flowShapes, _) = split(children)
      val flowWidth = flowShapes.map(_.width).maxOption.getOrElse(0.0)
      rows.foldLeft((children, 0.0, List.empty[Bounds[Double]])) {
        case ((flow :: expression :: rest, y, placed), SequenceRow.FlowAndExpression) =>
          val height = math.max(flow.height, expression.height)
          (rest, y + height, placed ++ List(Bounds(Point(0.0, y), Dimension(flowWidth, height)),
            Bounds(Point(flowWidth, y), Dimension(expression.width, height))))
        case ((whole :: rest, y, placed), SequenceRow.Whole) => (rest, y + whole.height, placed :+ Bounds(Point(0.0, y), whole))
        case (_, row) => throw IllegalArgumentException(s"Missing child for sequence row $row.")
      }._3
    }
    private def split(children: List[Dimension[Double]]): (List[Dimension[Double]], List[Dimension[Double]]) =
      rows.foldLeft((children, List.empty[Dimension[Double]])) {
        case ((flow :: _ :: rest, flows), SequenceRow.FlowAndExpression) => (rest, flows :+ flow)
        case ((_ :: rest, flows), SequenceRow.Whole) => (rest, flows)
        case ((rest, flows), _) => (rest, flows)
      }.swap
    def minimum(children: List[Dimension[Double]]): Dimension[Double] = Fixed(layout(children)).minimum(children)
    def place(children: List[Dimension[Double]], size: Dimension[Double]): List[Bounds[Double]] = layout(children)
  }

  enum SequenceRow {
    case FlowAndExpression, Whole
  }
}

/** A block outline that surrounds content, with the spacing rules of each data-type shape. */
enum BlockContainer {
  case Rectangle, Literal, Numeric, Text, Boolean, Date, Unit, Duck, Comment

  /** The outline's size when it holds nothing. */
  def naturalSize: Dimension[Double] = this match {
    case Unit => Dimension(20.0, 8.0)
    case _ => Dimension(8.0, 8.0)
  }

  def draw(bounds: Bounds[Double], rendering: BlockRenderingConfig): SvgPathBuilder[Double] = this match {
    case Rectangle => BlockPaths.buildRectangle(bounds)
    case Literal => BlockPaths.buildLiteralShape(bounds)
    case Numeric => BlockPaths.buildNumericShape(bounds)
    case Text => BlockPaths.buildStringShape(bounds)
    case Boolean => BlockPaths.buildBooleanShape(bounds)
    case Date => BlockPaths.buildDateShape(bounds)
    case Unit => BlockPaths.buildUnitShape(bounds, BlockContainer.unitRadius)
    case Duck => BlockPaths.buildDuckShape(bounds)
    case Comment => BlockPaths.buildSpeechBubbleShape(bounds, rendering.segmentSize)
  }

  private def spaceBefore(child: Dimension[Double], rendering: BlockRenderingConfig): Dimension[Double] = {
    val pad = rendering.paddingSmall
    this match {
      case Rectangle => pad
      case Literal => pad.increaseSize(child.height / 2, 0).increaseSize(child.height / 5, 0)
      case Numeric | Text | Boolean | Date => pad.increaseSize(5, 0)
      case Unit =>
        val r = BlockContainer.unitRadius
        Dimension(2 * r + r - remainder(child.width, r) / 2, 2 * r + r - remainder(child.height, r) / 2)
      case Duck =>
        val minimum = child.ensureAtLeastAsBigAs(naturalSize).ensureWidth(child.height)
        Dimension(minimum.height / 25 * 15, minimum.height).increaseSize(pad).increaseSize(pad)
      case Comment => Dimension(rendering.segmentSize * 2, pad.height)
    }
  }

  private def spaceAfter(child: Dimension[Double], rendering: BlockRenderingConfig): Dimension[Double] = {
    val pad = rendering.paddingSmall
    this match {
      case Rectangle => pad
      case Literal => pad.increaseSize(child.height / 10, 0)
      case Numeric | Text | Boolean | Date => pad.increaseSize(5, 0)
      case Unit => spaceBefore(child, rendering)
      case Duck =>
        val minimum = child.ensureAtLeastAsBigAs(naturalSize).ensureWidth(child.height)
        Dimension(minimum.height / 25 * 10, 0.0).increaseSize(pad)
      case Comment => Dimension(rendering.segmentSize, pad.height)
    }
  }

  private def remainder(value: Double, divisor: Int): Double = value - divisor * (value / divisor).toInt

  def sizeAround(child: Dimension[Double], rendering: BlockRenderingConfig): Dimension[Double] = {
    val size = child.increaseSize(spaceBefore(child, rendering)).increaseSize(spaceAfter(child, rendering))
    if this == Duck then size.ensureWidth(child.height * 3) else size
  }

  def childOffset(child: Dimension[Double], outer: Dimension[Double], rendering: BlockRenderingConfig): Point[Double] = {
    val before = spaceBefore(child, rendering)
    val free = outer.decreaseSize(before).decreaseSize(spaceAfter(child, rendering)).decreaseSize(child)
    before.increaseSize(Dimension(free.width / 2, free.height / 2)).asPoint
  }
}

object BlockContainer {
  private val unitRadius = 2
}
