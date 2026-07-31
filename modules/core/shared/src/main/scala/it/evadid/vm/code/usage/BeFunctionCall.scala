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

case class BeFunctionCall(funcDef: BeDefineFunction, parameterValueMap: Map[BeDefineVariable, BeExpression]) extends BeExpression {

  private lazy val parameterWithValues: List[(BeDefineVariable, Option[BeExpression])] = funcDef.inputs.map(curInput => (curInput, parameterValueMap.get(curInput)))

  override lazy val staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation {
    override def staticType: BeDataType = funcDef.outputs.map(_.variableType).getOrElse(BeDataType.Unit)

    override def staticValue: Option[BeDataValue] = None

    override def syntaxErrors: Seq[BeInfo] = parameterWithValues.filter(_._2.isEmpty).map(_._1).map(curVal => {
      BeInfo(LanguageMap.universalMap("Missing value for parameter " + curVal.name), BeInfo.SyntaxError.MissingValue)
    })

    override def hasSideEffects: Boolean = false
  }

  override lazy val structureInfo: BeExpressionStructureInfo[?] = new BeExpressionStructureInfo[BeFunctionCall](this) {
    override def withReplacedChildren(newChildren: Map[BeChildRole, BeExpression]): BeFunctionCall = {
      val updatedMap = newChildren.foldLeft(parameterValueMap) {
        case (values, (FunctionParameter(index), expression)) => funcDef.inputs.lift(index).map(values.updated(_, expression)).getOrElse(values)
        case (values, _) => values
      }
      copy(parameterValueMap = updatedMap)
    }

    override def toJavaStyleLines(myInfo: BeChildInfo): Seq[BeSegmentedCodeElement] = asExpressionLine(ControlFlowDown, myInfo)

    override def getChildrenAndExtension(myScope: BeScope): Seq[BeExpressionNode] = parameterWithValues.zipWithIndex.collect {
      case ((_, Some(value)), index) => BeExpressionReference(BeChildInfo(FunctionParameter(index), myScope), value)
    }
  }


 /* override def getChildren(withExtensions: Boolean, parentScope: BeScope): List[BeExpressionNode] = {
    parameterWithValues.zipWithIndex.filter(_._1._2.nonEmpty).map((tup, index) => (tup._1, tup._2.get, index)).map((parVar, parVal, parNr) => {
      BeExpressionReference(BeChildInfo(FunctionParameter(parNr), parentScope), parVal)
    })
  }*/

  override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = {
    val replacements = newChildren.collect { case (FunctionParameter(nr), expr) => nr -> expr }
    if (replacements.isEmpty) BeFunctionCall.this
    else {
      val updatedMap = replacements.foldLeft(parameterValueMap) { case (acc, (nr, expr)) =>
        funcDef.inputs.lift(nr).map(parameter => acc.updated(parameter, expr)).getOrElse(acc)
      }
      copy(parameterValueMap = updatedMap)
    }
  }

}
