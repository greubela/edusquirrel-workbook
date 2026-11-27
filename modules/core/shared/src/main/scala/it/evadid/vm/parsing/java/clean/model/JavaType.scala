package it.evadid.vm.parsing.java.clean.model

import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.Serializer.rawStringIO
import it.evadid.vm.parsing.TopLevelSplitter
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
  /** Java literals also nest inside generic type arguments. */
  private val literals = TopLevelSplitter(Set('[', '(', '{', '<'), Set(']', ')', '}', '>'))

  case object JAVA_INTEGER extends JavaType[BigInt]("int", Serializer.intDecimalIO, Serializer.intDecimalIO) with GenericNumericalInteger

  case object JAVA_INTEGER_HEX extends JavaType[BigInt]("int", Serializer.intHexIO, Serializer.intHexIO) with GenericNumericalInteger

  case object JAVA_INTEGER_OCT extends JavaType[BigInt]("int", Serializer.integerBaseIO(8, "0"), Serializer.intOctalIO) with GenericNumericalInteger

  case object JAVA_INTEGER_BIN extends JavaType[BigInt]("int", Serializer.intBinaryIO, Serializer.intBinaryIO) with GenericNumericalInteger

  case object JAVA_FLOAT extends JavaType[Double]("double", Serializer.fromImplicitRW[Double], Serializer.fromImplicitRW[Double]) with GenericNumericalFractional

  case object JAVA_STRING extends JavaType[String]("String", Serializer.fromImplicitRW[String], Serializer.fromImplicitRW[String])

  case object JAVA_BOOL extends JavaType[Boolean]("boolean", Serializer.fromImplicitRW[Boolean], Serializer.fromImplicitRW[Boolean])

  case object JAVA_ANY extends JavaType[String]("Object", rawStringIO, rawStringIO)

  final case class JAVA_LIST[Element](elementType: JavaType[Element]) extends JavaType[List[Element]](
    s"List<${elementType.typenameInCode}>",
    literals.collectionSerializer(elementType.serializerJavaValue, "List.of(", ")"),
    literals.collectionSerializer(elementType.serializerScalaValue, "List(", ")")
  )

  final case class JAVA_ARRAY[Element](elementType: JavaType[Element]) extends JavaType[List[Element]](
    s"${elementType.typenameInCode}[]",
    literals.collectionSerializer(elementType.serializerJavaValue, "{", "}"),
    literals.collectionSerializer(elementType.serializerScalaValue, "List(", ")")
  )

  final case class JAVA_UNPARSABLE_TYPE(str: String) extends JavaType[String](str, rawStringIO, rawStringIO)

}
