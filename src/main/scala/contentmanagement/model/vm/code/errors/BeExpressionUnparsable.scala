package contentmanagement.model.vm.code.errors

import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import contentmanagement.model.vm.io.BeExpressionIO
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.static.BeExpressionStaticInformation
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.ConditionInControlStructure
import contentmanagement.model.vm.types.BeScope.InSequenceScope
import interactionPlugins.blockEnvironment.config.BeTreeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import interactionPlugins.blockEnvironment.programming.blockdisplay.other.{BeBlockUnparsable, BeBlockUnsupported}

case class BeExpressionUnparsable(originalSource: String, message: String) extends BeExpression {

  override def expressionStaticInformation: BeExpressionStaticInformation = new BeExpressionStaticInformation() {

    def staticType: BeDataType = BeDataType.Error

    def staticValue: Option[BeDataValue] = None

    def syntaxErrors: Seq[BeInfo] = List(BeInfo(LanguageMap.universalMap(message), BeInfo.SyntaxError.UnparsableBlock))

    def hasSideEffects: Boolean = false
  }

  override def expressionIO: BeExpressionIO = new BeExpressionIO(){
    def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = originalSource

    def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = BeExpressionUnparsable.this

    def createBlock(): BeBlock = BeBlockUnparsable(BeExpressionUnparsable.this)
  }


  
  override def getChildren(withExtensions: Boolean, myScope: BeScope): List[BeExpressionNode] = List()



}
