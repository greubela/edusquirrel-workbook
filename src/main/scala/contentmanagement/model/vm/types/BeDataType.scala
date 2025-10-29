package contentmanagement.model.vm.types

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import interactionPlugins.blockEnvironment.programming.shapes.BeShape
import interactionPlugins.blockEnvironment.programming.shapes.BeShape.BeShapeContainerable
import interactionPlugins.blockEnvironment.programming.shapes.datatypes.*

enum BeDataType(val associatedShape: BeShapeContainerable, val formatStringForDisplay: String => String, val isValidLiteral: String => Boolean) {
  case Numeric extends BeDataType(NumericShape, _.toString, BeDataType.canParseAsNumeric)
  case Boolean extends BeDataType(BooleanShape, _.toString, str => true)
  case String extends BeDataType(StringShape, str => '"' + str + '"', str => true)
  case Date extends BeDataType(DateShape, _.toString, str => true)
  case Unit extends BeDataType(UnitShape, _.toString, str => false)
  case Error extends BeDataType(NumericShape, _.toString, str => false) // todo
}


object BeDataType {

  def allPossibleTypesForLiteral(literalStr: String): Set[BeDataType] =
    Set(Numeric, Boolean, String, Date, Unit).filter(_.isValidLiteral(literalStr))
  

  private def canParseAsNumeric(str: String): Boolean = {
    try {
      Some(str.toDouble)
      true
    } catch {
      case _: Throwable => false
    }
  }

  def AnyType: Set[BeDataType] = Set(Numeric, Boolean, String, Date, Unit)

  def getShape(possibleTypes: Set[BeDataType]): BeShapeContainerable = {
    if (possibleTypes.size == 1) possibleTypes.head.associatedShape
    else DuckShape
  }

  def validForType(possibleTypes: Set[BeDataType], actualType: BeDataType): Boolean = {
    if (possibleTypes.contains(actualType)) true
    else if (possibleTypes.contains(Unit) && actualType != Error) true // will be ignored
    else false
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