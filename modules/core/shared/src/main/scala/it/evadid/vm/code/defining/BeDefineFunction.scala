package it.evadid.vm.code.defining

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.util.CodeStringBuilderMutable
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.BeDefineFunction.*
import it.evadid.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import it.evadid.vm.code.abstractions.{BeDefineStructure, BeExpression}
import it.evadid.vm.controlflow.ControlFlowType.ControlFlowDown
import it.evadid.vm.io.{BeExpressionStructureInfo, BeSegmentedCodeElement}
import it.evadid.vm.naming.{BeEntityName, CodeRepresentationConfig}
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.*
import it.evadid.vm.types.BeChildRole.BodySequence
import it.evadid.vm.types.BeScope.InSequenceScope

case class BeDefineFunction(
                             inputs: List[BeDefineVariable],
                             outputs: Option[BeDefineVariable],
                             body: BeSequence,
                             functionTypeInfo: BeFunctionTypeInfo
                           ) extends BeDefineStructure {

  /*
  toSnapPattern
   */

  override lazy val staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation() {
    /*
        if (!body.canEvaluateTo.exists(curPossibleReturnValue => BeDataType.validForType(body.canEvaluateTo, curPossibleReturnValue))) {
          List(BeInfo(LanguageMap.universalMap("Function Signature Requires [" + canEvaluateTo.mkString(", ") + "] but body returns one of [" + body.canEvaluateTo + "]"), BeInfo.SyntaxError.TypeMismatch))
        } else {
          List()
        }
      }*/

    override def hasSideEffects: Boolean = true
  }

  override lazy val structureInfo: BeExpressionStructureInfo[?] = new BeExpressionStructureInfo[BeDefineFunction](this) {
    override def withReplacedChildren(newChildren: Map[BeChildRole, BeExpression]): BeDefineFunction =
      newChildren.get(BodySequence(0)).collect { case sequence: BeSequence => copy(body = sequence) }.getOrElse(BeDefineFunction.this)

    override def toJavaStyleLines(myInfo: BeChildInfo): Seq[BeSegmentedCodeElement] =
      asExpressionLine(ControlFlowDown, myInfo)

    override def getChildrenAndExtension(myScope: BeScope): Seq[BeExpressionNode] = List(
      BeExpressionReference(BeChildInfo(BodySequence(0), InSequenceScope(body, myScope)), body)
    )
  }




  /*
  override val toString: String = {

    s"""BeDefineStaticFunction(
       |  ${inputs.map(_.canEvaluateTo.toString).mkString("(", ", ", ")")} => ${outputs.map(_.toString).getOrElse("()")},
       |  $body
       |)""".stripMargin
  }*/

  /*override def getChildren(withExtensions: Boolean, myScope: BeScope): List[BeExpressionNode] = {
    List(
      BeExpressionReference(BeChildInfo(BeChildRole.BodySequence(0), InSequenceScope(body, myScope)), body),
    )
  }*/

  override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = {
    newChildren.collectFirst {
      case (BodySequence(0), expr) => expr
    }.map(replacement => copy(body = replacement.asInstanceOf[BeSequence])).getOrElse(BeDefineFunction.this)
  }

}

object BeDefineFunction {

  case class BeFunctionTypeInfo(isMethodInClass: Option[BeDefineClass], isNamed: Option[BeEntityName], funcType: BeFunctionType) {

    def displayName: BeEntityName = isNamed.getOrElse(BeEntityName.fromUniversalNameInParts("λ"))

    def displayNamePosition: Int = funcType match {
      case Operator(pos) => pos
      case _ => 0
    }

  }

  sealed trait BeFunctionType

  case class Lambda() extends BeFunctionType

  case class Method() extends BeFunctionType

  case class Function() extends BeFunctionType

  case class Operator(nameBeforeChildNr: Int) extends BeFunctionType

  def methodFunctionInfo(methodInClass: BeDefineClass, name: BeEntityName): BeFunctionTypeInfo = {
    BeFunctionTypeInfo(Some(methodInClass), Some(name), Method())
  }

  def lambdaFunctionInfo(): BeFunctionTypeInfo = {
    BeFunctionTypeInfo(None, None, Lambda())
  }

  def functionInfo(name: BeEntityName): BeFunctionTypeInfo = {
    BeFunctionTypeInfo(None, Some(name), Function())
  }

  def operatorInfo(symbol: String, position: Int): BeFunctionTypeInfo = {
    BeFunctionTypeInfo(None, Some(BeEntityName.fromLiteral(symbol)), Operator(position))
  }

}
