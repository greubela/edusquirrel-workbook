package contentmanagement.webElements.shapes.meta

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Signal
import contentmanagement.webElements.shapesNew.BeShapeNew
import it.evadid.core.datastructures.geometry.Bounds

case class AugmentInformation[T: Fractional](
                               baseAmends: Seq[L.Modifier[L.SvgElement]],
                               signalAmends: Seq[Signal[L.Modifier[L.SvgElement]]],
                               doOnRendering: Seq[(Bounds[T], BeShapeNew[T]) => Any]
                             ) {

  def combineWith(other: AugmentInformation[T]): AugmentInformation[T] =
    AugmentInformation(baseAmends ++ other.baseAmends, signalAmends ++ other.signalAmends, doOnRendering ++ other.doOnRendering)

}
