package it.evadid.vm.types

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.AutoSerializable
import it.evadid.core.util.io.serializer.AutoSerializable.{AutoSerializableMainType, AutoSerializableSingleton, AutoSerializableSubType}
import upickle.ReadWriter
import upickle.default.*

sealed trait BeDataType {

  def formatTypeForDisplay: LanguageMap[ProgrammingLanguage]

  def formatValueForDisplay(valueStr: String): LanguageMap[ProgrammingLanguage]


  def canTakeValuesFrom(other: BeDataType): BeDataTypeAssigningPossible

  def isValidLiteral(valueStr: String): Boolean

}


object BeDataType {

  private given rwAtom: ReadWriter[BeDataTypeAtomic] = AutoSerializable.getReadWriter[BeDataTypeAtomic, BeSerializableAtomicType](using macroRW)

  // Atomic and singleton codecs write JSON strings. They cannot be merged by
  // macroRW, which requires case-class readers for every sealed-trait member.
  given rw: ReadWriter[BeDataType] = readwriter[ujson.Value].bimap[BeDataType](
    {
      case AnyType => ujson.Str("Singleton(AnyType$)")
      case atomic: BeDataTypeAtomic => writeJs(atomic)(using rwAtom)
      case union: BeUnionAllowedTypes => writeJs(union)
    },
    {
      case ujson.Str("Singleton(AnyType$)") => AnyType
      case json: ujson.Str => read[BeDataTypeAtomic](json)(using rwAtom)
      case json: ujson.Obj => read[BeUnionAllowedTypes](json)
      case _ => throw new IllegalArgumentException("Invalid data type representation")
    }
  )

  given rwUnion: ReadWriter[BeUnionType] = rw.bimap[BeUnionType](
    union => union,
    {
      case union: BeUnionType => union
      case _ => throw new IllegalArgumentException("Expected a union data type")
    }
  )

  sealed trait BeUnionType extends BeDataType {

  }


  case object AnyType extends BeUnionType with AutoSerializableSingleton[AnyType.type] {
    private val displayMap: LanguageMap[ProgrammingLanguage] = LanguageMap.universalMap("Any") /*LanguageMap.concatLanguageMaps[
      ProgrammingLanguage
    ](
      List(
        LanguageMap.mapBasedLanguageMap(Map(Python -> "Any", Java -> "Object")),
        LanguageMap.universalMap("Any")
      )
    )*/

    def formatTypeForDisplay: LanguageMap[ProgrammingLanguage] = displayMap

    def formatValueForDisplay(valueStr: String): LanguageMap[ProgrammingLanguage] = LanguageMap.universalMap(valueStr)

    def isValidLiteral(valueStr: String): Boolean = false


    def canTakeValuesFrom(other: BeDataType): BeDataTypeAssigningPossible = AssigningPossibleWithSameType(this)
  }

  case class BeUnionAllowedTypes(dataTypes: Set[BeDataType]) extends BeUnionType derives ReadWriter {

    def formatTypeForDisplay: LanguageMap[ProgrammingLanguage] = {
      val members = dataTypes.toList.sortBy(_.formatTypeForDisplay.getInLanguage(Python))
      members.map(_.formatTypeForDisplay).reduceOption { (left, right) =>
        LanguageMap.concatLanguageMaps(
          LanguageMap.concatLanguageMaps(left, LanguageMap.universalMap[ProgrammingLanguage]("|")), right
        )
      }.getOrElse(LanguageMap.universalMap[ProgrammingLanguage](""))
    }

    def formatValueForDisplay(valueStr: String): LanguageMap[ProgrammingLanguage] = LanguageMap.universalMap(valueStr)


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
        case BeDataTypeAtomic(_, _, _, otherAllowedImplicitCasts) => {
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


  private lazy val namedAtomicTypes = List(
    "String" -> String, "Numeric" -> Numeric, "Int" -> Int,
    "Boolean" -> Boolean, "Date" -> Date, "Unit" -> Unit, "Error" -> Error
  )

  lazy val allAtomic = namedAtomicTypes.map(_._2)


  case class BeSerializableAtomicType(name: String) extends AutoSerializableSubType[BeDataTypeAtomic, BeSerializableAtomicType] {
    lazy val toTypedMainType: BeDataTypeAtomic =
      namedAtomicTypes.find(_._1 == name).map(_._2)
        // Earlier atomic representations used the same class name for every
        // type; retain their original first-match interpretation as String.
        .orElse(if (name == "BeDataTypeAtomic") Some(String) else None).get
  }

  case class BeDataTypeAtomic(
                               val pFormatTypeForDisplay: LanguageMap[ProgrammingLanguage],
                               val pFormatValueForDisplay: String => LanguageMap[ProgrammingLanguage],
                               val pIsValidLiteral: String => Boolean,
                               val allowImplicitCastTo: Set[BeDataType] = Set()
                             ) extends BeDataType with AutoSerializableMainType[BeDataTypeAtomic, BeSerializableAtomicType] {

    lazy val rwSub: ReadWriter[BeSerializableAtomicType] = macroRW

    lazy val toSerializableSubType: BeSerializableAtomicType =
      BeSerializableAtomicType(namedAtomicTypes.find(_._2 eq this).map(_._1)
        .getOrElse(throw new IllegalArgumentException("Cannot serialize an unregistered atomic data type")))

    def canTakeValuesFrom(other: BeDataType): BeDataTypeAssigningPossible = other match {
      case BeUnionAllowedTypes(otherTypes) => {
        if (otherTypes.contains(this)) AssigningPossibleWithImplicitCast(this)
        else AssigningNotPossible()
      }
      case AnyType => AssigningPossibleWithImplicitCast(this)
      case BeDataTypeAtomic(_, _, _, otherAllowedImplicitCasts) => {
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
    LanguageMap.mapBasedLanguageMap(Map(Python -> "str", Java -> "String", Cpp -> "String")),
    str => {
      val trimmed = str.trim
      val alreadyQuoted =
        (trimmed.length >= 2 && ((trimmed.head == '"' && trimmed.last == '"') || (trimmed.head == '\'' && trimmed.last == '\''))) ||
          (trimmed.length >= 6 && ((trimmed.startsWith("\"\"\"") && trimmed.endsWith("\"\"\"")) || (trimmed.startsWith("'''") && trimmed.endsWith("'''"))))
      val value = if (alreadyQuoted) trimmed else s"\"$str\""
      LanguageMap.universalMap(value)
    },
    str => {
      // In the block editor we treat the content of a string literal as valid even if it is not quoted,
      // because the formatter is responsible for adding quotes in text-based languages.
      // (Otherwise Serial.print/println would emit unquoted strings.)
      true
    },
    Set())

  val Numeric = BeDataTypeAtomic(
    LanguageMap.mapBasedLanguageMap(Map(Python -> "float", Java -> "double")),
    str => LanguageMap.universalMap(BigDecimal(str.trim).toDouble.toString),
    str => scala.util.Try(BigDecimal(str.trim)).isSuccess,
    Set(String))

  // Separate integer type, useful for languages like C++ where int/float matters.
  // Can be implicitly cast to Numeric (float) and String.
  val Int = BeDataTypeAtomic(
    LanguageMap.mapBasedLanguageMap(Map(Python -> "int", Java -> "int", Cpp -> "int")),
    str => LanguageMap.universalMap(str.trim),
    str => scala.util.Try(BigInt(str.trim)).isSuccess,
    Set(Numeric, String)
  )

  val Boolean = BeDataTypeAtomic(
    LanguageMap.mapBasedLanguageMap(Map(Python -> "bool", Java -> "boolean")),
    str => LanguageMap.universalMap(str),
    str => {
      val trimmed = str.trim
      trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("false")
    },
    Set(String)
  )

  val Date = BeDataTypeAtomic(
    LanguageMap.mapBasedLanguageMap(Map(Python -> "date", Java -> "Date")),
    str => LanguageMap.universalMap(str.toString),
    str => {
      val trimmed = str.trim
      val isoDatePattern = """\d{4}-\d{2}-\d{2}""".r
      isoDatePattern.matches(trimmed)
    },
    Set(String)
  )

  val Unit = BeDataTypeAtomic(
    LanguageMap.mapBasedLanguageMap(Map(Python -> "None", Java -> "void")),
    str => LanguageMap.universalMap(str.toString),
    str => false,
    Set(String))

  val Error = BeDataTypeAtomic(LanguageMap.universalMap("Error"),
    str => LanguageMap.universalMap(str.toString), str => false, Set()) // todo

  val allKnownTypesThatHaveLiterals: Set[BeDataType] = Set(Int, Numeric, Boolean, String, Date, Unit)


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
  case Cus tomFunction extends BeFunctionTypes(RGBColor.black, ShapeFactory.buildStarterShape)
}
*/
