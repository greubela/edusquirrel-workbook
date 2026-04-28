package contentmanagement.webElements.shapes.meta

case class ShapeInfo[T: Fractional](
                                     val augInfo: AugmentInformation[T],
                                     val posInfo: PositionInformation[T],
                                     val dimInfo: ConfigDependentSizeConstraint[T]
                                   ) {


}
