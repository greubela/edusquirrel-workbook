package contentmanagement.model.vm.code.controlStructures

import contentmanagement.model.language.AppLanguage.*
import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import contentmanagement.model.vm.code.{BeControlStructure, BeExpression}
import contentmanagement.model.vm.io.BeExpressionIO
import contentmanagement.model.vm.static.BeExpressionStaticInformation
import contentmanagement.model.vm.types.BeScope.InSequenceScope
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import util.CodeStringBuilder

case class BeRepeatNr(amount: Int, body: BeSequence) extends BeControlStructure {

  override def allPossibleBodies: List[BeExpression] = List(body)

  override def expressionStaticInformation: BeExpressionStaticInformation = new BeExpressionStaticInformation() {
    def staticType: BeDataType = BeDataType.Unit

    def staticValue: Option[BeDataValue] = Some(BeDataValueUnit())

    def syntaxErrors: Seq[BeInfo] = {
        if (amount < 0)          List(BeInfo(LanguageMap.universalMap("repeat count must be zero or positive"), BeInfo.SyntaxError.InvalidLiteralValue))
        else List()
    }


    def hasSideEffects: Boolean = false
  }

  override def expressionIO: BeExpressionIO = new BeExpressionIO() {

    def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {

      val bodyString = body.expressionIO.getInLanguage(programmingLanguage, humanLanguage)

      programmingLanguage match {
        case Python => {
          CodeStringBuilder().appendNextLine(s"for _ in range($amount):")
            .changeIntLevel(1)
            .appendAsLines(bodyString)
            .toString
        }
        case Java => {
          CodeStringBuilder().appendNextLine(s"for(int TECHNICAL_HELPER_VARIABLE = 0; TECHNICAL_HELPER_VARIABLE < $amount; TECHNICAL_HELPER_VARIABLE++){")
            .changeIntLevel(1)
            .appendAsLines(bodyString)
            .changeIntLevel(-1)
            .appendNextLine("}")
            .toString
        }
        case JavaScript => {
          CodeStringBuilder().appendNextLine(s"for (let be_index = 0; be_index < $amount; be_index++) {")
            .changeIntLevel(1)
            .appendAsLines(bodyString)
            .changeIntLevel(-1)
            .appendNextLine("}")
            .toString
        }
        case Rust => {
          CodeStringBuilder().appendNextLine(s"for _ in 0..$amount {")
            .changeIntLevel(1)
            .appendAsLines(bodyString)
            .changeIntLevel(-1)
            .appendNextLine("}")
            .toString
        }
        case Lisp => {
          CodeStringBuilder().appendNextLine(s"(dotimes (be-index $amount)")
            .changeIntLevel(1)
            .appendAsLines(bodyString)
            .changeIntLevel(-1)
            .appendNextLine(")")
            .toString
        }
        case _ => {
          CodeStringBuilder().appendNextLine(s"REPEAT/NR(")
            .changeIntLevel(1)
            .appendNextLine(s"$amount,")
            .appendAsLines(bodyString)
            .changeIntLevel(-1)
            .appendNextLine(")")
            .toString
        }
      }
    }

    def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = {
      val newBody = newChildren.collectFirst {
        case (BeChildRole.BodySequence(0), seq: BeSequence) => seq
      }.getOrElse(body)

      copy(body = newBody)
    }

    override def createBlock(): BeBlock =
      throw new NotImplementedError("Block rendering is not implemented for BeRepeatNr")

  }
  
  override def getChildren(withExtensions: Boolean, parentScope: BeScope): List[BeExpressionNode] = List(
    BeExpressionReference(BeChildPosition(BeChildRole.BodySequence(0), InSequenceScope(body, parentScope)), body)
  )

}
