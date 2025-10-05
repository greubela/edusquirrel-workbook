package interactionPlugins.blockEnvironment.programming

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.color.{AppColor, RGBColor}
import contentmanagement.model.geometry.Bounds
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.programming.rendering.ShapeFactory


enum BeDataType(val color: AppColor, val shapeFactory: Bounds[Double] => AppSvgElement) {
  case Numeric extends BeDataType(RGBColor.darkGreen, ShapeFactory.buildNumericShape)
  case Boolean extends BeDataType(RGBColor.green, ShapeFactory.buildBooleanShape)
  case String extends BeDataType(RGBColor.yellow, ShapeFactory.buildStringShape)
  case Date extends BeDataType(RGBColor.red, ShapeFactory.buildDateShape)
  case Unit extends BeDataType(RGBColor.yellow, ShapeFactory.buildUnitShape)
  case BlockDescription extends BeDataType(RGBColor.transparent, ShapeFactory.buildRectangle)
}

/*
sealed trait BeBlockType {
  def color: AppColor
  def shapeFactory: (Double, Double, Seq[L.Modifier[L.SvgElement]]) => L.SvgElement
}
enum BeFunctionTypes(val color: AppColor, val shapeFactory: (Double, Double, Seq[L.Modifier[L.SvgElement]]) => L.SvgElement)
  extends BeBlockType {
  case ExistingFunction extends BeFunctionTypes(RGBColor.yellow, ShapeFactory.buildTurtleUnitShape)
  case StartBlock extends BeFunctionTypes(RGBColor.black, ShapeFactory.buildStarterShape)
  case CustomFunction extends BeFunctionTypes(RGBColor.black, ShapeFactory.buildStarterShape)
}
*/