package it.evadid.vm.parsing.python.clean.model

import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.Serializer.rawStringIO
import it.evadid.vm.parsing.generic.abstractions.GenericAST.*
import it.evadid.vm.parsing.python.clean.model.PyAST.{PyExpression, PythonLiteral}
import upickle.default.*

sealed trait PythonType[ScalaType] extends GenericAstType[ScalaType, PythonType[ScalaType], PythonLiteral[ScalaType]] with PyExpression  {
  def typeStringInPython: String

  def serializerPythonValue: Serializer[ScalaType]

  override val serializeTargetLanguageValue: Serializer[ScalaType] = serializerPythonValue
  override val typenameInCode: String = typeStringInPython

  protected def handleLiteralCreation(literalString: String): PythonLiteral[ScalaType] = PythonLiteral(literalString, this)
}

object PythonType {
  abstract class PythonTypeImpl[ScalaType](
                                            val typeStringInPython: String,
                                            val serializerPythonValue: Serializer[ScalaType],
                                            override val serializerScalaValue: Serializer[ScalaType]
                                          ) extends GenericAstType[ScalaType, PythonType[ScalaType], PythonLiteral[ScalaType]] with PythonType[ScalaType] {
    override val serializeTargetLanguageValue: Serializer[ScalaType] = serializerPythonValue
    override val typenameInCode: String = typeStringInPython
  }

  case object PYTHON_INTEGER extends PythonTypeImpl[BigInt]("int", Serializer.intDecimalIO, Serializer.intDecimalIO) with GenericNumericalInteger

  case object PYTHON_INTEGER_HEX extends PythonTypeImpl[BigInt]("int", Serializer.intHexIO, Serializer.intHexIO) with GenericNumericalInteger

  case object PYTHON_INTEGER_OCT extends PythonTypeImpl[BigInt]("int", Serializer.intOctalIO, Serializer.intOctalIO) with GenericNumericalInteger

  case object PYTHON_INTEGER_BIN extends PythonTypeImpl[BigInt]("int", Serializer.intBinaryIO, Serializer.intBinaryIO) with GenericNumericalInteger

  case object PYTHON_FLOAT extends PythonTypeImpl[Double]("float", Serializer.fromImplicitRW[Double], Serializer.fromImplicitRW[Double]) with GenericNumericalFractional

  // case object PYTHON_COMPLEX extends PythonType("complex") with Numeric

  case object PYTHON_STRING extends PythonTypeImpl[String]("str", Serializer.stringLiteralIO(), Serializer.stringLiteralIO())

  case object PYTHON_BOOL extends PythonTypeImpl[Boolean]("bool", Serializer.pythonBooleanIO, Serializer.fromImplicitRW[Boolean])

  case object PYTHON_NONE extends PythonTypeImpl[Option[Unit]]("None", Serializer.noneParser(), Serializer.noneParser())

  final case class PYTHON_LIST[Element](elementType: PythonType[Element]) extends PythonTypeImpl[List[Element]](
    s"list[${elementType.typeStringInPython}]",
    PythonCollectionSerializers.collectionSerializer(elementType.serializerPythonValue, "[", "]"),
    PythonCollectionSerializers.collectionSerializer(elementType.serializerScalaValue, "List(", ")")
  )

  final case class PYTHON_ARRAY[Element](elementType: PythonType[Element]) extends PythonTypeImpl[List[Element]](
    s"array[${elementType.typeStringInPython}]",
    PythonCollectionSerializers.collectionSerializer(elementType.serializerPythonValue, "[", "]"),
    PythonCollectionSerializers.collectionSerializer(elementType.serializerScalaValue, "List(", ")")
  )

  final case class PYTHON_SET[Element](elementType: PythonType[Element]) extends PythonTypeImpl[Set[Element]](
    s"set[${elementType.typeStringInPython}]",
    PythonCollectionSerializers.setSerializer(elementType.serializerPythonValue),
    PythonCollectionSerializers.setSerializer(elementType.serializerScalaValue)
  )

  final case class PYTHON_DICT[Key, Value](keyType: PythonType[Key], valueType: PythonType[Value]) extends PythonTypeImpl[Map[Key, Value]](
    s"dict[${keyType.typeStringInPython}, ${valueType.typeStringInPython}]",
    PythonCollectionSerializers.dictSerializer(keyType.serializerPythonValue, valueType.serializerPythonValue),
    PythonCollectionSerializers.dictSerializer(keyType.serializerScalaValue, valueType.serializerScalaValue)
  )

  case class PYTHON_UNION_TYPE[ScalaTypeA, ScalaTypeB](a: PythonType[ScalaTypeA], b: PythonType[ScalaTypeB]) extends PythonType[Either[ScalaTypeA, ScalaTypeB]] {

    override def typeStringInPython: String = a.typeStringInPython + "|" + b.typeStringInPython

    override def serializerPythonValue: Serializer[Either[ScalaTypeA, ScalaTypeB]] = Serializer.eitherPlainValueIO(a.serializeTargetLanguageValue, b.serializeTargetLanguageValue)

    override def serializerScalaValue: Serializer[Either[ScalaTypeA, ScalaTypeB]] = {
      Serializer.eitherProjectionIO(a.serializerScalaValue, b.serializerScalaValue)
    }

  }

  final case class PYTHON_OPTIONAL[ScalaType](child: PythonType[ScalaType]) extends PythonType[Option[ScalaType]] {

    override def typeStringInPython: String = child.typeStringInPython + "|None"

    override def serializerPythonValue: Serializer[Option[ScalaType]] = Serializer.optionPlainValueIO(child.serializerPythonValue)

    override def serializerScalaValue: Serializer[Option[ScalaType]] = Serializer.optionProjectionIO(child.serializerScalaValue)
  }

  case object PYTHON_ANY extends PythonTypeImpl[String]("Any", rawStringIO, rawStringIO)

  final case class PYTHON_UNPARSABLE_TYPE(str: String) extends PythonTypeImpl[String](str, rawStringIO, rawStringIO) {

  }

  //  val allAtomicTypes: List[PythonType] = List(PYTHON_INTEGER, PYTHON_FLOAT, PYTHON_COMPLEX, PYTHON_STRING, PYTHON_BOOL, PYTHON_NONE, PYTHON_ANY, PYTHON_FUNCTION)

}
