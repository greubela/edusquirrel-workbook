package it.evadid.vm.parsing.python.clean.model

import it.evadid.core.util.io.Serializer
import it.evadid.vm.parsing.generic.abstractions.GenericAST.*
import it.evadid.vm.parsing.python.clean.model.PyAST.{PyExpression, PythonLiteral}


private sealed trait PythonType[ScalaType] extends GenericAstType[ScalaType, PythonType[ScalaType], PythonLiteral[ScalaType]] with PyExpression {
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


  sealed class PYTHON_INTEGER extends PythonTypeImpl[BigInt]("int", Serializer.intDecimalIO, Serializer.intDecimalIO) with GenericNumericalInteger

  sealed class PYTHON_INTEGER_HEX extends PythonTypeImpl[BigInt]("int", Serializer.intHexIO, Serializer.intHexIO) with GenericNumericalInteger

  sealed class PYTHON_INTEGER_OCT extends PythonTypeImpl[BigInt]("int", Serializer.intOctalIO, Serializer.intOctalIO) with GenericNumericalInteger

  sealed class PYTHON_INTEGER_BIN extends PythonTypeImpl[BigInt]("int", Serializer.intBinaryIO, Serializer.intBinaryIO) with GenericNumericalInteger

  sealed class PYTHON_FLOAT extends PythonTypeImpl[Double]("float", Serializer.floatIO, Serializer.floatIO) with GenericNumericalFractional

  // case object PYTHON_COMPLEX extends PythonType("complex") with Numeric

  sealed class PYTHON_STRING extends PythonTypeImpl[String]("str", Serializer.stringLiteralIO(), Serializer.stringLiteralIO())

  sealed class PYTHON_BOOL extends PythonTypeImpl[Boolean]("bool", Serializer.pythonBooleanIO, Serializer.booleanIO)

  sealed class PYTHON_NONE extends PythonTypeImpl[Option[Unit]]("None", Serializer.noneParser(), Serializer.noneParser())

  sealed class PYTHON_LIST[Element](val elementType: PythonType[Element]) extends PythonTypeImpl[List[Element]](
    s"list[${elementType.typeStringInPython}]",
    PythonCollectionSerializers.collectionSerializer(elementType.serializerPythonValue, "[", "]"),
    PythonCollectionSerializers.collectionSerializer(elementType.serializerScalaValue, "List(", ")")
  )

  sealed class PYTHON_ARRAY[Element](val elementType: PythonType[Element]) extends PythonTypeImpl[List[Element]](
    s"array[${elementType.typeStringInPython}]",
    PythonCollectionSerializers.collectionSerializer(elementType.serializerPythonValue, "[", "]"),
    PythonCollectionSerializers.collectionSerializer(elementType.serializerScalaValue, "List(", ")")
  )

  sealed class PYTHON_SET[Element](val elementType: PythonType[Element]) extends PythonTypeImpl[Set[Element]](
    s"set[${elementType.typeStringInPython}]",
    PythonCollectionSerializers.setSerializer(elementType.serializerPythonValue),
    PythonCollectionSerializers.setSerializer(elementType.serializerScalaValue)
  )

  sealed class PYTHON_DICT[Key, Value](val keyType: PythonType[Key], val valueType: PythonType[Value]) extends PythonTypeImpl[Map[Key, Value]](
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


  sealed class PYTHON_OPTIONAL[ScalaType](child: PythonType[ScalaType]) extends PythonType[Option[ScalaType]] {

    override def typeStringInPython: String = child.typeStringInPython + "|None"

    override def serializerPythonValue: Serializer[Option[ScalaType]] = Serializer.optionPlainValueIO(child.serializerPythonValue)

    override def serializerScalaValue: Serializer[Option[ScalaType]] = Serializer.optionProjectionIO(child.serializerScalaValue)
  }

  sealed class PYTHON_ANY extends PythonTypeImpl[Any]("Any", Serializer.parseAnyAsUnderlyingString, Serializer.parseAnyAsUnderlyingString)

  sealed class PYTHON_UNPARSABLE_TYPE(str: String) extends PythonTypeImpl[Any](str, Serializer.parseAnyAsUnderlyingString, Serializer.parseAnyAsUnderlyingString) {

  }

  //  val allAtomicTypes: List[PythonType] = List(PYTHON_INTEGER, PYTHON_FLOAT, PYTHON_COMPLEX, PYTHON_STRING, PYTHON_BOOL, PYTHON_NONE, PYTHON_ANY, PYTHON_FUNCTION)


}
