package interactionPlugins.blockEnvironment.programming

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import interactionPlugins.blockEnvironment.programming.rendering.*
import interactionPlugins.blockEnvironment.programming.rendering.BeShape.*

enum BeDataType(val associatedShape: BeShape, val formatStringForDisplay: String => String) {
  case Numeric extends BeDataType(BeShape.NumericShape, _.toString)
  case Boolean extends BeDataType(BeShape.BooleanShape, _.toString)
  case String extends BeDataType(BeShape.StringShape, str => '"' + str + '"')
  case Date extends BeDataType(BeShape.DateShape, _.toString)
  case Unit extends BeDataType(BeShape.FunctionCallShape, _.toString)
  case Any extends BeDataType(BeShape.DuckShape,  _.toString) // 🦆
}

object BeDataType {
  def AnyType: Set[BeDataType] = Set(Numeric, Boolean, String, Date, Unit)

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