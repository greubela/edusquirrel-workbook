package contentmanagement.model.vm.code.controlStructures

import contentmanagement.model.language.AppLanguage.{Java, JavaScript, Lisp, Python, Rust}
import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.code.{BeControlStructure, BeExpression}
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeDataType, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.parents.BeBlockSequence
import util.CodeStringBuilder

case class BeSequence(shouldEvaluateToUnit: Boolean, body: List[BeExpression]) extends BeControlStructure {


  def allPossibleBodies: List[BeExpression] = body

  def getSyntaxErrors: Seq[BeInfo] = List() // whether it may be empty must be checked by the parent

  override def canEvaluateTo: Set[BeDataType] = if (shouldEvaluateToUnit || body.isEmpty) Set(BeDataType.Unit) else body.last.canEvaluateTo

  override def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock = BeBlockSequence(this, parentPos)

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
    programmingLanguage match {
      case Python => body.map(_.getInLanguage(programmingLanguage, humanLanguage)).mkString("\n")
      case Java | JavaScript | Rust =>
        body.map(_.getInLanguage(programmingLanguage, humanLanguage)).mkString("\n")
      case Lisp =>
        if (body.isEmpty) "(progn)"
        else {
          val builder = CodeStringBuilder("(progn")
            .changeIntLevel(1)
          val withBody = body.foldLeft(builder) { (acc, expr) =>
            acc.appendAsLines(expr.getInLanguage(programmingLanguage, humanLanguage))
          }
          withBody.changeIntLevel(-1)
            .appendNextLine(")")
            .toString
        }
      case _ => {
        var res = CodeStringBuilder(s"BeSequence(")
          .changeIntLevel(2)
          .appendNextLine(s"//always unit=$shouldEvaluateToUnit")
          .changeIntLevel(-1)
        if (body.nonEmpty) res = res.changeForEach(body, (old, curExpr) => old.appendAsLines(curExpr.toString))
        else res = res.appendNextLine("[no body]")
        res.changeIntLevel(-1)
          .appendNextLine(")").toString
      }
    }
  }

  override def getChildren: List[(BeChildRole, BeExpression)] =
    body.zipWithIndex.map((curExpr, curIndex) => (BeChildRole.ExpressionInBody(curIndex), curExpr))


}

object BeSequence {

  def optionalUnitBody(body: List[BeExpression]) = BeSequence(true, body)


}