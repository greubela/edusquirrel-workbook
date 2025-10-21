package contentmanagement.webElements.svg.elements

import com.raquo.laminar.api.L
import contentmanagement.model.color.AppColor
import contentmanagement.model.geometry.Bounds
import contentmanagement.webElements.svg.{AppSvgElement, AppSvgElementRenderingAdditions}
import org.scalajs.dom.MouseEvent

case class AppRectangleSvgElement[T](bounds: Bounds[T]) extends AppSvgElement{

  override def staticBoundingBox: Bounds[Double] = ???

  override def asLaminar(stroke: AppColor, fill: AppColor): L.SvgElement = ???

  override def renderAsLaminar(shapeMods: Seq[L.Modifier[L.SvgElement]]): L.SvgElement = ???

  override def renderWithController(shapeMods: Seq[L.Modifier[L.SvgElement]], onClick: MouseEvent => Any, onDragStart: MouseEvent => Any, onDropped: MouseEvent => Any): L.SvgElement = ???

  override def renderAsGroupWithAdditions(additions: List[AppSvgElementRenderingAdditions], shapeMods: Seq[L.Modifier[L.SvgElement]]): L.SvgElement = ???
}
