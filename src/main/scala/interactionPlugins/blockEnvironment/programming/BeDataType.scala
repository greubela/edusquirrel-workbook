package interactionPlugins.blockEnvironment.programming

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import interactionPlugins.blockEnvironment.programming.rendering.*
import interactionPlugins.blockEnvironment.programming.rendering.*
import interactionPlugins.blockEnvironment.programming.rendering.shapes.BeShape.BeShapeContainerable
import interactionPlugins.blockEnvironment.programming.rendering.shapes.atomic.*

enum BeDataType(val associatedShape: BeShapeContainerable, val formatStringForDisplay: String => String) {
  case Numeric extends BeDataType(NumericShape, _.toString)
  case Boolean extends BeDataType(BooleanShape, _.toString)
  case String extends BeDataType(StringShape, str => '"' + str + '"')
  case Date extends BeDataType(DateShape, _.toString)
  case Unit extends BeDataType(FunctionCallShape, _.toString)
}

object BeDataType {
  def AnyType: Set[BeDataType] = Set(Numeric, Boolean, String, Date, Unit)

  def getShape(possibleTypes: Set[BeDataType]): BeShapeContainerable = {
    if(possibleTypes.size == 1)      possibleTypes.head.associatedShape
    else      DuckShape
  }
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