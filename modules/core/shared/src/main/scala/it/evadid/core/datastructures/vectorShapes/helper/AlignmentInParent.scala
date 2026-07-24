package it.evadid.core.datastructures.vectorShapes.helper

import it.evadid.core.datastructures.vectorShapes.helper.AlignmentInParent.VerticalAlignment.*
import it.evadid.core.datastructures.vectorShapes.helper.AlignmentInParent.HorizontalAlignment.*

sealed trait AlignmentInParent

object AlignmentInParent {

  object DistortionAlignment extends AlignmentInParent


  enum HorizontalAlignment:
    case Left, Center, Right

  enum VerticalAlignment:
    case Top, Middle, Bottom


  sealed class PositionInParent(val vertical: VerticalAlignment, val horizontal: HorizontalAlignment) extends AlignmentInParent


  object TopLeft extends PositionInParent(Top, Left)

  object TopCenter extends PositionInParent(Top, Center)

  object TopRight extends PositionInParent(Top, Right)


  object MiddleLeft extends PositionInParent(Middle, Left)

  object MiddleCenter extends PositionInParent(Middle, Center)

  object MiddleRight extends PositionInParent(Middle, Right)


  object BottomLeft extends PositionInParent(Bottom, Left)

  object BottomCenter extends PositionInParent(Bottom, Center)

  object BottomRight extends PositionInParent(Bottom, Right)

}


