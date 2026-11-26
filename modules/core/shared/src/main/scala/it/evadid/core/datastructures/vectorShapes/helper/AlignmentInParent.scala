package it.evadid.core.datastructures.vectorShapes.helper


sealed trait AlignmentInParent

object AlignmentInParent {

  object DistortionAlignment extends AlignmentInParent

  enum HorizontalAlignment:
    case Left, Center, Right

  enum VerticalAlignment:
    case Top, Middle, Bottom

  sealed class PositionInParent(val vertical: VerticalAlignment, val horizontal: HorizontalAlignment) extends AlignmentInParent

  object TopLeft extends PositionInParent(VerticalAlignment.Top, HorizontalAlignment.Left)

  object TopCenter extends PositionInParent(VerticalAlignment.Top, HorizontalAlignment.Center)

  object TopRight extends PositionInParent(VerticalAlignment.Top, HorizontalAlignment.Right)

  object MiddleLeft extends PositionInParent(VerticalAlignment.Middle, HorizontalAlignment.Left)

  object MiddleCenter extends PositionInParent(VerticalAlignment.Middle, HorizontalAlignment.Center)

  object MiddleRight extends PositionInParent(VerticalAlignment.Middle, HorizontalAlignment.Right)

  object BottomLeft extends PositionInParent(VerticalAlignment.Bottom, HorizontalAlignment.Left)

  object BottomCenter extends PositionInParent(VerticalAlignment.Bottom, HorizontalAlignment.Center)

  object BottomRight extends PositionInParent(VerticalAlignment.Bottom, HorizontalAlignment.Right)

}

