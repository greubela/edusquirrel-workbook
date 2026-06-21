package it.evadid.workbook.vm.code.controlStructures

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.core.util.CodeStringBuilder
import it.evadid.workbook.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import it.evadid.workbook.vm.code.{BeControlStructure, BeExpression}
import it.evadid.workbook.vm.io.BeExpressionIO
import it.evadid.workbook.vm.static.BeExpressionStaticInformation
import it.evadid.workbook.vm.types.BeScope.InSequenceScope
import it.evadid.workbook.vm.types.{BeChildPosition, BeChildRole, BeInfo, BeScope}

case class BeRepeatNr(amount: Int, body: BeSequence) extends BeControlStructure {

  override def allPossibleBodies: List[BeExpression] = List(body)

  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation() {

    override def syntaxErrors: Seq[BeInfo] = {
      if (amount < 0) List(BeInfo(LanguageMap.universalMap("repeat count must be zero or positive"), BeInfo.SyntaxError.InvalidLiteralValue))
      else List()
    }


  }

  override def expressionIO: BeExpressionIO = new BeExpressionIO() {

    override def toStringInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage, skipUnparsable: Boolean = false): String = {

      val bodyString = body.expressionIO.toStringInLanguage(programmingLanguage, humanLanguage, skipUnparsable)

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
        case Cpp => {
          CodeStringBuilder().appendNextLine(s"for(int be_index = 0; be_index < $amount; be_index++){")
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



  }


  override def getChildren(withExtensions: Boolean, parentScope: BeScope): List[BeExpressionNode] = List(
    BeExpressionReference(BeChildPosition(BeChildRole.BodySequence(0), InSequenceScope(body, parentScope)), body)
  )

  override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = {
    val newBody = newChildren.collectFirst {
      case (BeChildRole.BodySequence(0), seq: BeSequence) => seq
    }.getOrElse(body)

    copy(body = newBody)
  }
}
