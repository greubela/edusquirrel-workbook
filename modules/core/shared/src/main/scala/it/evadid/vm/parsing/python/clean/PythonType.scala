package it.evadid.vm.parsing.python.clean

import it.evadid.core.util.io.Serializer
import it.evadid.vm.parsing.generic.abstractions.GenericAST.*
import it.evadid.vm.parsing.python.clean.PyAST.{PyExpression, PythonLiteral}


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
