package workbook.workbookHtmlElements.visualization

import contentmanagement.model.color.RGBColor

final case class VisualizationConfig(
    marginX: Double = 24,
    marginY: Double = 24,
    horizontalLayerSpacing: Double = 120,
    verticalNodeSpacing: Double = 40,
    sectionPaddingX: Double = 18,
    sectionPaddingY: Double = 18,
    sectionMinWidth: Double = 160,
    titleHeight: Double = 24,
    titleSpacing: Double = 12,
    emptySectionPlaceholderHeight: Double = 24,
    bubbleHeight: Double = 26,
    bubbleSpacing: Double = 12,
    bubbleMinWidth: Double = 40,
    bubbleMaxWidth: Double = 160,
    backgroundColor: RGBColor = RGBColor(255, 255, 255),
    requiredColor: RGBColor = RGBColor(0, 88, 124),
    recommendedColor: RGBColor = RGBColor(0, 88, 124),
    sectionFillColor: RGBColor = RGBColor(224, 236, 242),
    sectionBorderColor: RGBColor = RGBColor(38, 72, 94),
    bubbleFillColor: RGBColor = RGBColor(0, 88, 124),
    bubbleBorderColor: RGBColor = RGBColor(0, 88, 124),
    titleColor: RGBColor = RGBColor(21, 41, 54),
    labelColor: RGBColor = RGBColor(70, 70, 70),
    arrowHeadLength: Double = 12,
    arrowHeadWidth: Double = 8,
    edgeStrokeWidth: Double = 2,
    edgeVerticalSpacing: Double = 14,
    arrowEndOffset: Double = 6,
    arrowStartOffset: Double = 6,
    edgeCurveControlFraction: Double = 0.35,
    edgeCurveMinControlOffset: Double = 40,
    edgeCurveVerticalBendFactor: Double = 0.25,
    recommendedDashPattern: Seq[Double] = Seq(10.0, 6.0)
)
