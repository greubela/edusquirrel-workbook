package todomove.webElementsOld.webElements.shapes.meta

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Signal
import it.evadid.core.datastructures.geometry.Bounds
import todomove.webElementsOld.webElements.shapes.BeShapeNew

case class AugmentInformation[T: Fractional](
                               baseAmends: Seq[L.Modifier[L.SvgElement]],
                               signalAmends: Seq[Signal[L.Modifier[L.SvgElement]]],
                               doOnRendering: Seq[(Bounds[T], BeShapeNew[T]) => Any]
                             ) {

  def combineWith(other: AugmentInformation[T]): AugmentInformation[T] =
    AugmentInformation(baseAmends ++ other.baseAmends, signalAmends ++ other.signalAmends, doOnRendering ++ other.doOnRendering)

}
