package it.evadid.vm.code.usage

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.defining.BeDefineFunction.Operator
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import it.evadid.vm.controlflow.ControlFlowType.ControlFlowDown
import it.evadid.vm.io.{BeExpressionStructureInfo, BeSegmentedCodeElement}
import it.evadid.vm.naming.CodeRepresentationConfig
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.BeChildRole.FunctionParameter
import it.evadid.vm.types.*
case class BeFunctionCall(target: BeCallTarget, parameterValueMap: Map[BeDefineVariable, BeExpression]) extends BeExpression {

  def funcDef: BeDefineFunction = target.definition

  /** Argument values in parameter order. */
  def arguments: List[BeExpression] = funcDef.inputs.flatMap(parameterValueMap.get)

  def isOperator: Boolean = funcDef.functionTypeInfo.funcType.isInstanceOf[Operator]

  private lazy val parameterWithValues: List[(BeDefineVariable, Option[BeExpression])] = funcDef.inputs.map(curInput => (curInput, parameterValueMap.get(curInput)))

  override lazy val staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation {
    override def staticType: BeDataType = target match {
      case BeCallTarget.Constructor(definition) => BeDataType.ClassType(definition.functionTypeInfo.isMethodInClass.get.name)
      case _ => funcDef.outputs.map(_.variableType).getOrElse(BeDataType.Unit)
    }

    override def staticValue: Option[BeDataValue] = None

    override def syntaxErrors: Seq[BeInfo] = parameterWithValues.filter(_._2.isEmpty).map(_._1).map(curVal => {
      BeInfo(LanguageMap.universalMap("Missing value for parameter " + curVal.name), BeInfo.SyntaxError.MissingValue)
    })

    override def hasSideEffects: Boolean = true
  }

  override lazy val structureInfo: BeExpressionStructureInfo[?] = new BeExpressionStructureInfo[BeFunctionCall](this) {
    override def withReplacedChildren(newChildren: Map[BeChildRole, BeExpression]): BeFunctionCall = {
      val replacements = newChildren.collect { case (FunctionParameter(nr), expr) => nr -> expr }
      val updatedMap = replacements.foldLeft(parameterValueMap) { case (acc, (nr, expr)) =>
        funcDef.inputs.lift(nr).map(parameter => acc.updated(parameter, expr)).getOrElse(acc)
      }
      val updatedTarget = target match {
        case BeCallTarget.InstanceMethod(receiver, definition) =>
          BeCallTarget.InstanceMethod(newChildren.getOrElse(BeChildRole.Receiver, receiver), definition)
        case other => other
      }
      copy(target = updatedTarget, parameterValueMap = updatedMap)
    }

    override def toJavaStyleLines(myInfo: BeChildInfo): Seq[BeSegmentedCodeElement] = asExpressionLine(ControlFlowDown, myInfo)

    override def getChildrenAndExtension(myScope: BeScope): Seq[BeExpressionNode] = target.receiver.toSeq.map(receiver => BeExpressionReference(BeChildInfo(BeChildRole.Receiver, myScope), receiver)) ++ parameterWithValues.zipWithIndex.collect {
      case ((_, Some(value)), index) => BeExpressionReference(BeChildInfo(FunctionParameter(index), myScope), value)
    }
  }


}

object BeFunctionCall {
  def fromDefinition(definition: BeDefineFunction, parameterValueMap: Map[BeDefineVariable, BeExpression]): BeFunctionCall =
    BeFunctionCall(BeCallTarget.forDefinition(definition), parameterValueMap)
}
