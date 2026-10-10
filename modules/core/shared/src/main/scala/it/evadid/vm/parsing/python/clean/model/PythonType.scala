package it.evadid.vm.parsing.python.clean.model

import it.evadid.core.util.io.Serializer
import it.evadid.vm.parsing.generic.abstractions.GenericAST.*
import it.evadid.vm.parsing.python.clean.model.PyAST.{PyExpression, PythonLiteral}


sealed trait PythonType[ScalaType] extends GenericAstType[ScalaType, PythonType[ScalaType], PythonLiteral[ScalaType]] with PyExpression {
  def typeStringInPython: String

  def serializerPythonValue: Serializer[ScalaType]

  override val serializeTargetLanguageValue: Serializer[ScalaType] = serializerPythonValue
  override val typenameInCode: String = typeStringInPython

  protected def handleLiteralCreation(literalString: String): PythonLiteral[ScalaType] = PythonLiteral(literalString, this)
}

object PythonType {
  import it.evadid.vm.parsing.generic.abstractions.AstTypeDescriptor

  given [T]: upickle.default.ReadWriter[PythonType[T]] =
    upickle.default.readwriter[AstTypeDescriptor].bimap(
      value => describe(value), descriptor => restore(descriptor).asInstanceOf[PythonType[T]])

  private def describe(value: PythonType[?]): AstTypeDescriptor = value match {
    case _: PYTHON_INTEGER => AstTypeDescriptor("integer")
    case _: PYTHON_INTEGER_HEX => AstTypeDescriptor("integer_hex")
    case _: PYTHON_INTEGER_OCT => AstTypeDescriptor("integer_oct")
    case _: PYTHON_INTEGER_BIN => AstTypeDescriptor("integer_bin")
    case _: PYTHON_FLOAT => AstTypeDescriptor("float")
    case _: PYTHON_STRING => AstTypeDescriptor("string")
    case _: PYTHON_BOOL => AstTypeDescriptor("bool")
    case _: PYTHON_NONE => AstTypeDescriptor("none")
    case _: PYTHON_ANY => AstTypeDescriptor("any")
    case value: PYTHON_LIST[?] => AstTypeDescriptor("list", List(describe(value.elementType)))
    case value: PYTHON_ARRAY[?] => AstTypeDescriptor("array", List(describe(value.elementType)))
    case value: PYTHON_SET[?] => AstTypeDescriptor("set", List(describe(value.elementType)))
    case value: PYTHON_OPTIONAL[?] => AstTypeDescriptor("optional", List(describe(value.child)))
    case value: PYTHON_DICT[?, ?] => AstTypeDescriptor("dict", List(describe(value.keyType), describe(value.valueType)))
    case value: PYTHON_UNION_TYPE[?, ?] => AstTypeDescriptor("union_type", List(describe(value.a), describe(value.b)))
    case value: PYTHON_UNPARSABLE_TYPE => AstTypeDescriptor("unparsable", label = value.str)
    case other => throw new IllegalArgumentException(s"Unsupported AST type: ${other.getClass.getName}")
  }

  private def restore(value: AstTypeDescriptor): PythonType[?] = {
    val arity = value.kind match {
      case "dict" | "union_type" => 2
      case "list" | "array" | "set" | "optional" => 1
      case _ => 0
    }
    require(value.arguments.size == arity, s"Invalid AST type arguments for ${value.kind}")
    value.kind match {
      case "integer" => new PYTHON_INTEGER
      case "integer_hex" => new PYTHON_INTEGER_HEX
      case "integer_oct" => new PYTHON_INTEGER_OCT
      case "integer_bin" => new PYTHON_INTEGER_BIN
      case "float" => new PYTHON_FLOAT
      case "string" => new PYTHON_STRING
      case "bool" => new PYTHON_BOOL
      case "none" => new PYTHON_NONE
      case "any" => new PYTHON_ANY
      case "list" => new PYTHON_LIST(restore(value.arguments.head))
      case "array" => new PYTHON_ARRAY(restore(value.arguments.head))
      case "set" => new PYTHON_SET(restore(value.arguments.head))
      case "optional" => new PYTHON_OPTIONAL(restore(value.arguments.head))
      case "dict" => new PYTHON_DICT(restore(value.arguments.head), restore(value.arguments(1)))
      case "union_type" => PYTHON_UNION_TYPE(restore(value.arguments.head), restore(value.arguments(1)))
      case "unparsable" => new PYTHON_UNPARSABLE_TYPE(value.label)
      case other => throw new IllegalArgumentException(s"Unknown AST type: $other")
    }
  }

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

  case class PYTHON_UNION_TYPE[ScalaTypeA, ScalaTypeB](a: PythonType[ScalaTypeA], b: PythonType[ScalaTypeB]) extends PythonType[Either[ScalaTypeA, ScalaTypeB]] derives upickle.default.ReadWriter {

    override def typeStringInPython: String = a.typeStringInPython + "|" + b.typeStringInPython

    override def serializerPythonValue: Serializer[Either[ScalaTypeA, ScalaTypeB]] = Serializer.eitherPlainValueIO(a.serializeTargetLanguageValue, b.serializeTargetLanguageValue)

    override def serializerScalaValue: Serializer[Either[ScalaTypeA, ScalaTypeB]] = {
      Serializer.eitherProjectionIO(a.serializerScalaValue, b.serializerScalaValue)
    }

  }


  sealed class PYTHON_OPTIONAL[ScalaType](val child: PythonType[ScalaType]) extends PythonType[Option[ScalaType]] {

    override def typeStringInPython: String = child.typeStringInPython + "|None"

    override def serializerPythonValue: Serializer[Option[ScalaType]] = Serializer.optionPlainValueIO(child.serializerPythonValue)

    override def serializerScalaValue: Serializer[Option[ScalaType]] = Serializer.optionProjectionIO(child.serializerScalaValue)
  }

  sealed class PYTHON_ANY extends PythonTypeImpl[Any]("Any", Serializer.parseAnyAsUnderlyingString, Serializer.parseAnyAsUnderlyingString)

  sealed class PYTHON_UNPARSABLE_TYPE(val str: String) extends PythonTypeImpl[Any](str, Serializer.parseAnyAsUnderlyingString, Serializer.parseAnyAsUnderlyingString) {

  }

  //  val allAtomicTypes: List[PythonType] = List(PYTHON_INTEGER, PYTHON_FLOAT, PYTHON_COMPLEX, PYTHON_STRING, PYTHON_BOOL, PYTHON_NONE, PYTHON_ANY, PYTHON_FUNCTION)


}
