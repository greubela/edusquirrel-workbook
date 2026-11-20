package it.evadid.core.datastructures.vectorShapes.blocks

import it.evadid.core.datastructures.color.{AppColor, AppColorPalette, RGBColor, RGBYColorPalette}
import it.evadid.core.datastructures.font.AppFont
import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.language.AppLanguage.{English, HumanLanguage}

/** Sizes, font, palette and display language of block and control-flow drawings.
  * `segmentSize` is the unit of every control-flow width, height and stroke.
  */
final case class BlockRenderingConfig(
    font: AppFont = AppFont.defaultFont,
    paddingSmall: Dimension[Double] = Dimension(2.0, 2.0),
    palette: RGBYColorPalette = AppColorPalette.defaultRGBYPalette25,
    language: HumanLanguage = English,
    segmentSize: Double = 10
) {
  lazy val paints: BlockPaints = BlockPaints(palette, segmentSize)
}

/** Fill, stroke and stroke style of one drawn element. */
final case class BlockPaint(fill: AppColor, stroke: AppColor, strokeWidth: Double = 1, dashArray: Option[String] = None,
                            fillEnabled: Boolean = true)

/** The editor's colour roles. */
final case class BlockPaints(palette: RGBYColorPalette, segmentSize: Double) {
  private def gray(index: Int) = palette.grayscale(index)
  private val flowStroke = segmentSize / 5.0

  val text: BlockPaint = BlockPaint(gray(0), gray(0))
  /** Variable names set only the outline; their fill is the SVG default, black. */
  val invertedText: BlockPaint = BlockPaint(RGBColor.black, gray(4))
  val libraryLabel: BlockPaint = BlockPaint(palette.yellows(2), RGBColor.transparent)
  val variableDefinition: BlockPaint = BlockPaint(gray(1), gray(0))
  val variableUse: BlockPaint = BlockPaint(gray(4), gray(3))
  val muted: BlockPaint = BlockPaint(gray(4), gray(4))
  val mutedFunction: BlockPaint = BlockPaint(palette.yellows(4), palette.yellows(4))
  val error: BlockPaint = BlockPaint(palette.reds(3), palette.reds(1))
  val destination: BlockPaint = BlockPaint(palette.greens(0), palette.greens(0))
  val accepting: BlockPaint = BlockPaint(palette.greens(4), palette.greens(4))
  val literal: BlockPaint = BlockPaint(RGBColor.white, gray(1))
  val function: BlockPaint = BlockPaint(palette.yellows(3), palette.yellows(2))
  val control: BlockPaint = BlockPaint(palette.yellows(2), palette.yellows(0))
  val controlFlowBackground: BlockPaint = control
  val activeDecoration: BlockPaint = BlockPaint(gray(0), RGBColor.transparent)
  val inactiveDecoration: BlockPaint = BlockPaint(palette.yellows(3), RGBColor.transparent)
  val flowSymbol: BlockPaint = BlockPaint(gray(4), gray(0), flowStroke)

  def flow(stroke: FlowStroke): BlockPaint = stroke match {
    case FlowStroke.Active => BlockPaint(RGBColor.transparent, gray(0), flowStroke)
    case FlowStroke.Inactive => BlockPaint(RGBColor.transparent, palette.yellows(4), flowStroke, Some("1,1"))
    case FlowStroke.ActiveTrue => BlockPaint(RGBColor.transparent, palette.greens(0), flowStroke)
    case FlowStroke.InactiveTrue => BlockPaint(RGBColor.transparent, palette.greens(2), flowStroke, Some("1,1"))
    case FlowStroke.ActiveFalse => BlockPaint(RGBColor.transparent, palette.reds(0), flowStroke)
    case FlowStroke.InactiveFalse => BlockPaint(RGBColor.transparent, palette.reds(2), flowStroke, Some("1,1"))
  }
}

/** Line styles of control-flow paths: the taken path, an alternative, and condition branches. */
enum FlowStroke {
  case Active, Inactive, ActiveTrue, InactiveTrue, ActiveFalse, InactiveFalse
}
