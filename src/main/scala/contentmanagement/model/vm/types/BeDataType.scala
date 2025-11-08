package contentmanagement.model.vm.types

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.language.AppLanguage.BlockDisplay
import contentmanagement.model.language.{LanguageMap, ProgrammingLanguage}
import contentmanagement.webElements.svg.shapes.BeShape
import BeShape.BeShapeContainerable
import contentmanagement.webElements.svg.shapes.datatypes.{BooleanShape, DateShape, DuckShape, NumericShape, StringShape, UnitShape}
import util.AlgebriteNumber

sealed trait BeDataType {

  def formatTypeForDisplay: LanguageMap[ProgrammingLanguage]

  def formatValueForDisplay(valueStr: String): LanguageMap[ProgrammingLanguage]

  def createShape: BeShape

  def createContainerShape: Option[BeShapeContainerable] = createShape match {
    case containerable: BeShapeContainerable => Some(containerable)
    case _ => None
  }

  def canTakeValuesFrom(other: BeDataType): BeDataTypeAssigningPossible

  def isValidLiteral(valueStr: String): Boolean

}


object BeDataType {

  sealed trait BeUnionType extends BeDataType {

  }

  object AnyType extends BeUnionType {
    def formatTypeForDisplay: LanguageMap[ProgrammingLanguage] = LanguageMap.universalMap("valueStr")

    def formatValueForDisplay(valueStr: String): LanguageMap[ProgrammingLanguage] = LanguageMap.universalMap("valueStr")

    def isValidLiteral(valueStr: String): Boolean = false

    def createShape: BeShape = DuckShape

    def canTakeValuesFrom(other: BeDataType): BeDataTypeAssigningPossible = AssigningPossibleWithSameType(this)
  }

  case class BeUnionAllowedTypes(dataTypes: Set[BeDataType]) extends BeUnionType {

    def formatTypeForDisplay: LanguageMap[ProgrammingLanguage] = LanguageMap.mkLanguageMap("[", ", ", "]", dataTypes.toList.map(_.formatTypeForDisplay))

    def formatValueForDisplay(valueStr: String): LanguageMap[ProgrammingLanguage] = LanguageMap.universalMap(valueStr)

    def createShape: BeShape = DuckShape

    def canTakeValuesFrom(other: BeDataType): BeDataTypeAssigningPossible = {
      other match {
        case AnyType => AssigningPossibleWithImplicitCast(this)
        case BeUnionAllowedTypes(otherTypes) => {
          val typeIntersection = BeDataType.allowedTypesIntersection(
            BeUnionAllowedTypes(Set(this)),
            BeUnionAllowedTypes(otherTypes)
          )
          if (typeIntersection.isEmpty) AssigningNotPossible()
          else if (typeIntersection.get == this) AssigningPossibleWithSameType(this)
          else AssigningPossibleWithImplicitCast(typeIntersection.get)
        }
        case BeDataTypeAtomic(_, _, _, _, otherAllowedImplicitCasts) => {
          if (dataTypes.contains(other)) AssigningPossibleWithImplicitCast(other)
          else AssigningNotPossible()
        }
      }
    }

    def isValidLiteral(valueStr: String): Boolean = dataTypes.exists(_.isValidLiteral(valueStr))
  }


  def allowedTypesIntersection(setA: BeUnionAllowedTypes, setB: BeUnionAllowedTypes): Option[BeDataType] = {
    val intersection = setA.dataTypes.intersect(setB.dataTypes)
    if (intersection.isEmpty) None
    else if (intersection.size == 1) Some(intersection.head)
    else Some(BeUnionAllowedTypes(intersection))
  }

  def typeIntersection(typeA: BeUnionType, typeB: BeUnionType): Option[BeDataType] = {
    if (typeA == AnyType) Some(typeB)
    else if (typeB == AnyType) Some(typeA)
    else if (typeA.isInstanceOf[BeUnionAllowedTypes] && typeB.isInstanceOf[BeUnionAllowedTypes]) {
      allowedTypesIntersection(typeA.asInstanceOf[BeUnionAllowedTypes], typeB.asInstanceOf[BeUnionAllowedTypes])
    } else ???
  }

  /*trait ClassDataType extends BeDataType {
    def definedClass: BeDefineClass
  }

  case class ClassDataTypeImpl(override val definedClass: BeDefineClass) extends ClassDataType {

  }*/


  case class BeDataTypeAtomic(
                               createShape: BeShapeContainerable,
                               pFormatTypeForDisplay: LanguageMap[ProgrammingLanguage],
                               pFormatValueForDisplay: String => LanguageMap[ProgrammingLanguage],
                               pIsValidLiteral: String => Boolean,
                               allowImplicitCastTo: Set[BeDataType] = Set()) extends BeDataType {


    def canTakeValuesFrom(other: BeDataType): BeDataTypeAssigningPossible = other match {
      case BeUnionAllowedTypes(otherTypes) => {
        if (otherTypes.contains(this)) AssigningPossibleWithImplicitCast(this)
        else AssigningNotPossible()
      }
      case AnyType => AssigningPossibleWithImplicitCast(this)
      case BeDataTypeAtomic(_, _, _, _, otherAllowedImplicitCasts) => {
        if (other == this) AssigningPossibleWithSameType(this)
        else if (otherAllowedImplicitCasts.contains(this)) AssigningPossibleWithImplicitCast(this)
        else AssigningNotPossible()
      }
    }

    def formatTypeForDisplay: LanguageMap[ProgrammingLanguage] = pFormatTypeForDisplay

    def formatValueForDisplay(valueStr: String): LanguageMap[ProgrammingLanguage] = {
      if (isValidLiteral(valueStr)) pFormatValueForDisplay(valueStr) else LanguageMap.universalMap(valueStr)
    }

    def isValidLiteral(valueStr: String): Boolean = pIsValidLiteral(valueStr)
  }


  val String = BeDataTypeAtomic(
    StringShape,
    LanguageMap.universalMap("str"),
    str => LanguageMap.universalMap('"' + str + '"'),
    str => true,
    Set())

  val Numeric = BeDataTypeAtomic(
    NumericShape,
    LanguageMap.universalMap("float"),
    str => {
      LanguageMap.combinedMap(List(
        LanguageMap.mapBasedLanguageMap(
          Map(BlockDisplay -> AlgebriteNumber.given_Fractional_AlgebriteNumber.parseString(str).get.toStringWithApprox)
        ),
        LanguageMap.universalMap(
          AlgebriteNumber.given_Fractional_AlgebriteNumber.parseString(str).get.toDouble.toString
        )
      ))
    },
    AlgebriteNumber.given_Fractional_AlgebriteNumber.parseString(_).nonEmpty,
    Set(String))

  val Boolean = BeDataTypeAtomic(
    BooleanShape,
    LanguageMap.universalMap("bool"),
    str => LanguageMap.universalMap(str),
    str => true, Set(String))

  val Date = BeDataTypeAtomic(DateShape,
    LanguageMap.universalMap("date"),
    str => LanguageMap.universalMap(str.toString),
    str => true,
    Set(String)
  )

  val Unit = BeDataTypeAtomic(UnitShape,
    LanguageMap.universalMap("None"),
    str => LanguageMap.universalMap(str.toString),
    str => false,
    Set(String))

  val Error = BeDataTypeAtomic(NumericShape,
    LanguageMap.universalMap("Error"),
    str => LanguageMap.universalMap(str.toString), str => false, Set()) // todo

  val allKnownTypesThatHaveLiterals: Set[BeDataType] = Set(Numeric, Boolean, String, Date, Unit)


  /*
    def getShape(possibleTypes: Set[BeDataType]): BeShapeContainerable = {
      if (possibleTypes.size == 1) possibleTypes.head.associatedShape
      else DuckShape
    }
  
    def validForType(possibleTypes: Set[BeDataType], actualType: BeDataType): Boolean = {
      if (possibleTypes.contains(actualType)) true
      else if (possibleTypes.contains(Unit) && actualType != Error) true // will be ignored
      else false
    }
  */
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