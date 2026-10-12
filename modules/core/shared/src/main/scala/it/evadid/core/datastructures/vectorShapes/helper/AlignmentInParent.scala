package it.evadid.core.datastructures.vectorShapes.helper

import upickle.default.*

sealed trait AlignmentInParent

object AlignmentInParent {

  private val namedAlignments: Map[String, AlignmentInParent] = Map(
    "DistortionAlignment" -> DistortionAlignment,
    "TopLeft" -> TopLeft, "TopCenter" -> TopCenter, "TopRight" -> TopRight,
    "MiddleLeft" -> MiddleLeft, "MiddleCenter" -> MiddleCenter, "MiddleRight" -> MiddleRight,
    "BottomLeft" -> BottomLeft, "BottomCenter" -> BottomCenter, "BottomRight" -> BottomRight)

  given ReadWriter[AlignmentInParent] = readwriter[String].bimap(
    alignment => alignment match {
      case DistortionAlignment => "DistortionAlignment"
      case position: PositionInParent => position.vertical.toString + position.horizontal.toString
    },
    name => namedAlignments.getOrElse(name, throw new IllegalArgumentException(s"Unknown alignment: $name")))

  object DistortionAlignment extends AlignmentInParent


  enum HorizontalAlignment derives ReadWriter:
    case Left, Center, Right

  enum VerticalAlignment derives ReadWriter:
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


