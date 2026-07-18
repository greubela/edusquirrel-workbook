package it.evadid.vm.parsing.java.clean

import fastparse.{P, *}
import it.evadid.core.util.io.Serializer
import it.evadid.vm.parsing.generic.CodeLexer.*
import it.evadid.vm.parsing.generic.abstractions.GenericAST.*
import it.evadid.vm.parsing.java.clean.JavaAST.JavaLiteral


private abstract class JavaType[ScalaType](
                                            typeStringInJava: String,
                                            val serializerJavaValue: Serializer[ScalaType],
                                            override val serializerScalaValue: Serializer[ScalaType]
                                          ) extends GenericAstType[ScalaType, JavaType[ScalaType], JavaLiteral[ScalaType]] {
  override val serializeTargetLanguageValue: Serializer[ScalaType] = serializerJavaValue
  override val typenameInCode: String = typeStringInJava

  override protected def handleLiteralCreation(literalString: String): JavaLiteral[ScalaType] = JavaLiteral(literalString, this)
}

object JavaType {

  sealed class JAVA_INTEGER extends JavaType[BigInt]("int", Serializer.intDecimalIO, Serializer.intDecimalIO) with GenericNumericalInteger

  sealed class JAVA_INTEGER_HEX extends JavaType[BigInt]("int", Serializer.intHexIO, Serializer.intHexIO) with GenericNumericalInteger

  sealed class JAVA_INTEGER_OCT extends JavaType[BigInt]("int", Serializer.intOctalIO, Serializer.intOctalIO) with GenericNumericalInteger

  sealed class JAVA_INTEGER_BIN extends JavaType[BigInt]("int", Serializer.intBinaryIO, Serializer.intBinaryIO) with GenericNumericalInteger

  sealed class JAVA_FLOAT extends JavaType[Double]("double", Serializer.floatIO, Serializer.floatIO) with GenericNumericalFractional

  sealed class JAVA_STRING extends JavaType[String]("String", Serializer.stringLiteralIO(), Serializer.stringLiteralIO())

  sealed class JAVA_BOOL extends JavaType[Boolean]("boolean", Serializer.booleanIO, Serializer.booleanIO)

  sealed class JAVA_ANY extends JavaType[Any]("Object", Serializer.parseAnyAsUnderlyingString, Serializer.parseAnyAsUnderlyingString)


  sealed class JAVA_UNPARSABLE_TYPE(str: String) extends JavaType[Any](str, Serializer.parseAnyAsUnderlyingString, Serializer.parseAnyAsUnderlyingString)




}
