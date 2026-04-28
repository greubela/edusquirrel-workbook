package contentmanagement.webElements.svg


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