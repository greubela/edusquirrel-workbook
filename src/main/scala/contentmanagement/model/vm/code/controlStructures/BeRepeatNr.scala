package contentmanagement.model.vm.code.controlStructures

import contentmanagement.model.language.AppLanguage.{Java, JavaScript, Lisp, Python, Rust}
import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.{BeControlStructure, BeExpression}
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import util.CodeStringBuilder

case class BeRepeatNr(amount: Int, body: BeExpression) extends BeControlStructure {

  override def allPossibleBodies: List[BeExpression] = List(body)

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String =

    val bodyString = body.getInLanguage(programmingLanguage, humanLanguage)

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


  /*override def executeRecursiveInSimulator(simulatorState: BeSimulatorState): BeUseValue = {
    var res = simulatorState
    0.to(amount).map(curNr => {
      res = body.executeRecursiveInSimulator(simulatorState)
    })
    res
  }*/

  override def getSyntaxErrors: Seq[BeInfo] = {
    val amountError =
      if (amount < 0)
        List(BeInfo(LanguageMap.universalMap("repeat count must be zero or positive"), BeInfo.SyntaxError.InvalidLiteralValue))
      else List()
    amountError ++ body.getSyntaxErrors
  }

  override def createBlock(config: BeDisplayConfig, childPos: BeChildPosition): BeBlock =
    throw new NotImplementedError("Block rendering is not implemented for BeRepeatNr")

  override def getChildren: List[(BeChildRole, BeExpression)] = List((BeChildRole.BodySequence(0), body))
}
