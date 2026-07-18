package it.evadid.vm.parsing.generic.abstractions

import it.evadid.core.util.io.Serializer
import it.evadid.vm.parsing.generic.abstractions.GenericAST.{GenericAstType, NamedElement}

trait GenericAST {

  def allNamedElements(): Map[String, NamedElement] =
    traversePreOrderWithListener {
      case (ne: NamedElement) => Some(ne.name, ne.asInstanceOf[NamedElement])
    }.flatten.toMap

  def traversePreOrderWithListener[T](onNodeVisited: GenericAST => T): Set[T] = {
    Set[T](onNodeVisited(this)) ++ getChildren().flatMap(_.traversePreOrderWithListener(onNodeVisited))
  }

  def getChildren(): Seq[GenericAST] = Seq()


}

object GenericAST {


  trait GenericAstType[
    ScalaType,
    TargetType <: GenericAstType[ScalaType, TargetType, LiteralType],
    LiteralType <: GenericAstLiteral[ScalaType, TargetType, LiteralType]
  ] {
    type UnderlyingScalaType = ScalaType

    //type UnderlyingScalaType = ScalaType

    def typenameInCode: String

    def serializeTargetLanguageValue: Serializer[ScalaType]

    def serializerScalaValue: Serializer[ScalaType]

    def createLiteralUnsafe(literalString: String): LiteralType = {
      val roundtrip = serializeTargetLanguageValue.serialize(serializeTargetLanguageValue.deserialize(literalString))
      handleLiteralCreation(roundtrip)
    }

    protected def handleLiteralCreation(literalString: String): LiteralType


  }

  trait GenericAstLiteral[
    ScalaType,
    TargetType <: GenericAstType[ScalaType, TargetType, LiteralType],
    LiteralType <: GenericAstLiteral[ScalaType, TargetType, LiteralType]
  ] {
    def literalValue: String

    def literalType: TargetType
  }
  /*
    abstract class GenericOptionType[
      ScalaType,
      ChildType <: GenericAstType[ScalaType, TargetType, LiteralType],
      TargetType <: GenericAstType[Option[ScalaType], TargetType, LiteralType],
      LiteralType <: GenericAstLiteral[Option[ScalaType], TargetType, LiteralType]
    ](childType: ChildType) extends GenericAstType[Option[ScalaType], TargetType, AssociatedLiteralType] {

      override def typenameInCode: String = convertToOptionType(childType.typenameInCode)

      def convertToOptionType(childType: String): String


    }

    abstract class GENERIC_UNION_TYPE[
      ScalaTypeA,
      ScalaTypeB,
      ChildTypeA <: GenericAstType[ScalaTypeA, ChildTypeA, AssociatedLiteralType],
      ChildTypeB <: GenericAstType[ScalaTypeB, ChildTypeB, AssociatedLiteralType],
      Self <: GENERIC_UNION_TYPE[ScalaTypeA, ScalaTypeB, ChildTypeA, ChildTypeB, Self],
      AssociatedLiteralType <: GenericAstLiteral[Either[ScalaTypeA, ScalaTypeB], AssociatedLiteralType, Self]
    ](a: ChildTypeA, b: ChildTypeB) extends GenericAstType[Either[ScalaTypeA, ScalaTypeB], Self] {

      override def typenameInCode: String = combineTypeStringsToFullType(a.typenameInCode, b.typenameInCode)

      def combineTypeStringsToFullType(typeA: String, typeB: String): String

      override def serializerScalaValue: Serializer[Either[ScalaTypeA, ScalaTypeB]] = Serializer.eitherProjectionIO(a.serializerScalaValue, b.serializerScalaValue)
    }
  */

  trait GenericNumericalType

  trait GenericNumericalFractional extends GenericNumericalType

  trait GenericNumericalInteger extends GenericNumericalType


  trait NamedElement {
    def name: String
  }

  trait GenericASTListener {
    def onNodeVisited(node: GenericAST): Unit = {
    }

  }


}
