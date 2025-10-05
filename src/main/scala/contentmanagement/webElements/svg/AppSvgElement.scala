package contentmanagement.webElements.svg

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.color.{AppColor, RGBColor}
import contentmanagement.model.geometry.Bounds

sealed trait AppSvgElementRenderingAdditions {
  case object showCoordinateSystem

  case object showBoundingBox
}

sealed trait AppSvgElementPathRenderingAdditions extends AppSvgElementRenderingAdditions {
  case object showPathControlPoints

  case object showPathControlPointPositionText

  case object showPathControlPointLines
}

sealed trait AppSvgElementRectangleRenderingAdditions extends AppSvgElementRenderingAdditions {
  case object showDimension

  case object showCornerPoints

  case object showCornerPointsPositionText
}

sealed trait AppSvgElementCircleRenderingAdditions extends AppSvgElementRenderingAdditions {
  case object showCenterPoint

  case object showCenterPointPositionText

  case object showRadiusLine

  case object showRadiusLineLengthText
}

sealed trait AppSvgElementLineRenderingAddition extends AppSvgElementRenderingAdditions {
  case object showLineStartPoint

  case object showLineStartPointPositionText

  case object showLineEndPoint

  case object showLineEndPointPositionText
}

trait AppSvgElement {

  def staticBoundingBox: Bounds[Double]

  def asLaminar(stroke: AppColor , fill: AppColor): L.SvgElement

  def renderAsLaminar(shapeMods: Seq[L.Modifier[L.SvgElement]]): L.SvgElement

  def renderAsGroupWithAdditions(additions: List[AppSvgElementRenderingAdditions], shapeMods: Seq[L.Modifier[L.SvgElement]]): L.SvgElement

  def asSimpleSvg(): L.SvgElement = {
    val boundingBox = staticBoundingBox
    svg.svg(
      svg.viewBox := s"${boundingBox.startPoint.x.toInt} ${boundingBox.startPoint.y.toInt} ${boundingBox.dimension.width.toInt} ${boundingBox.dimension.height.toInt}",
      svg.width := boundingBox.dimension.width.toInt + "",
      svg.height := boundingBox.dimension.height.toInt + "",
      asLaminar(RGBColor.black, RGBColor.red)
    )
  }

}
