package it.evadid.vm.parsing.java.clean.model

import it.evadid.core.util.io.Serializer
import it.evadid.vm.parsing.generic.abstractions.GenericAST.*
import it.evadid.vm.parsing.java.clean.model.JavaAST.JavaLiteral


abstract class JavaType[ScalaType](
                                            typeStringInJava: String,
                                            val serializerJavaValue: Serializer[ScalaType],
                                            override val serializerScalaValue: Serializer[ScalaType]
                                          ) extends GenericAstType[ScalaType, JavaType[ScalaType], JavaLiteral[ScalaType]] {
  override val serializeTargetLanguageValue: Serializer[ScalaType] = serializerJavaValue
  override val typenameInCode: String = typeStringInJava

  override protected def handleLiteralCreation(literalString: String): JavaLiteral[ScalaType] = JavaLiteral(literalString, this)
}

object JavaType {
  import it.evadid.vm.parsing.generic.abstractions.AstTypeDescriptor

  given [T]: upickle.default.ReadWriter[JavaType[T]] =
    upickle.default.readwriter[AstTypeDescriptor].bimap(
      value => describe(value), descriptor => restore(descriptor).asInstanceOf[JavaType[T]])

  private def describe(value: JavaType[?]): AstTypeDescriptor = value match {
    case _: JAVA_INTEGER => AstTypeDescriptor("integer")
    case _: JAVA_INTEGER_HEX => AstTypeDescriptor("integer_hex")
    case _: JAVA_INTEGER_OCT => AstTypeDescriptor("integer_oct")
    case _: JAVA_INTEGER_BIN => AstTypeDescriptor("integer_bin")
    case _: JAVA_FLOAT => AstTypeDescriptor("float")
    case _: JAVA_STRING => AstTypeDescriptor("string")
    case _: JAVA_BOOL => AstTypeDescriptor("bool")
    case _: JAVA_ANY => AstTypeDescriptor("any")
    case value: JAVA_LIST[?] => AstTypeDescriptor("list", List(describe(value.elementType)))
    case value: JAVA_ARRAY[?] => AstTypeDescriptor("array", List(describe(value.elementType)))
    case value: JAVA_UNPARSABLE_TYPE => AstTypeDescriptor("unparsable", label = value.str)
    case other => throw new IllegalArgumentException(s"Unsupported AST type: ${other.getClass.getName}")
  }

  private def restore(value: AstTypeDescriptor): JavaType[?] = {
    val arity = value.kind match {
      case "list" | "array" => 1
      case _ => 0
    }
    require(value.arguments.size == arity, s"Invalid AST type arguments for ${value.kind}")
    value.kind match {
      case "integer" => new JAVA_INTEGER
      case "integer_hex" => new JAVA_INTEGER_HEX
      case "integer_oct" => new JAVA_INTEGER_OCT
      case "integer_bin" => new JAVA_INTEGER_BIN
      case "float" => new JAVA_FLOAT
      case "string" => new JAVA_STRING
      case "bool" => new JAVA_BOOL
      case "any" => new JAVA_ANY
      case "list" => new JAVA_LIST(restore(value.arguments.head))
      case "array" => new JAVA_ARRAY(restore(value.arguments.head))
      case "unparsable" => new JAVA_UNPARSABLE_TYPE(value.label)
      case other => throw new IllegalArgumentException(s"Unknown AST type: $other")
    }
  }


  sealed class JAVA_INTEGER extends JavaType[BigInt]("int", Serializer.intDecimalIO, Serializer.intDecimalIO) with GenericNumericalInteger

  sealed class JAVA_INTEGER_HEX extends JavaType[BigInt]("int", Serializer.intHexIO, Serializer.intHexIO) with GenericNumericalInteger

  sealed class JAVA_INTEGER_OCT extends JavaType[BigInt]("int", Serializer.integerBaseIO(8, "0", Map("0o" -> 8, "O" -> 8)), Serializer.intOctalIO) with GenericNumericalInteger

  sealed class JAVA_INTEGER_BIN extends JavaType[BigInt]("int", Serializer.intBinaryIO, Serializer.intBinaryIO) with GenericNumericalInteger

  sealed class JAVA_FLOAT extends JavaType[Double]("double", Serializer.floatIO, Serializer.floatIO) with GenericNumericalFractional

  sealed class JAVA_STRING extends JavaType[String]("String", Serializer.stringLiteralIO(), Serializer.stringLiteralIO())

  sealed class JAVA_BOOL extends JavaType[Boolean]("boolean", Serializer.booleanIO, Serializer.booleanIO)

  sealed class JAVA_ANY extends JavaType[Any]("Object", Serializer.parseAnyAsUnderlyingString, Serializer.parseAnyAsUnderlyingString)

  sealed class JAVA_LIST[Element](val elementType: JavaType[Element]) extends JavaType[List[Element]](
    s"List<${elementType.typenameInCode}>",
    JavaCollectionSerializers.collectionSerializer(elementType.serializerJavaValue, "List.of(", ")"),
    JavaCollectionSerializers.collectionSerializer(elementType.serializerScalaValue, "List(", ")")
  )

  sealed class JAVA_ARRAY[Element](val elementType: JavaType[Element]) extends JavaType[List[Element]](
    s"${elementType.typenameInCode}[]",
    JavaCollectionSerializers.collectionSerializer(elementType.serializerJavaValue, "{", "}"),
    JavaCollectionSerializers.collectionSerializer(elementType.serializerScalaValue, "List(", ")")
  )


  sealed class JAVA_UNPARSABLE_TYPE(val str: String) extends JavaType[Any](str, Serializer.parseAnyAsUnderlyingString, Serializer.parseAnyAsUnderlyingString)




}
