package todomove.datastructures.core.vm.code.controlStructures

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.util.CodeStringBuilder
import todomove.datastructures.core.vm.code.tree.{BeExpressionNode, BeExpressionReference, BeExtensionPoint}
import todomove.datastructures.core.vm.code.{BeControlStructure, BeExpression}
import todomove.datastructures.core.vm.io.BeExpressionIO
import todomove.datastructures.core.vm.simulation.{BeExpressionExecutor, BeSimulatorConfig, BeSimulatorState}
import todomove.datastructures.core.vm.static.BeExpressionStaticInformation
import todomove.datastructures.core.vm.types.*
import todomove.datastructures.core.vm.types.BeScope.InSequenceScope

case class BeSequenceInfo(mustEvaluateTo: Option[BeDataType], maxBodyElements: Option[Int] = None)

case class BeSequence(body: List[BeExpression], sequenceInfo: BeSequenceInfo) extends BeControlStructure {

  def allPossibleBodies: List[BeExpression] = body

  private val myRef = this

  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation {
    override def staticType: BeDataType = sequenceInfo.mustEvaluateTo.getOrElse(body.lastOption.map(_.staticInformationExpression.staticType).getOrElse(BeDataType.Error))

    override def staticValue: Option[BeDataValue] = body.lastOption.flatMap(_.staticInformationExpression.staticValue)
  }

  override def expressionIO: BeExpressionIO = new BeExpressionIO {
    override def toStringInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage, skipUnparsable: Boolean = false): String = {
      programmingLanguage match {
        case Python => body.map(_.expressionIO.toStringInLanguage(programmingLanguage, humanLanguage, skipUnparsable)).mkString("\n")
        case Java | JavaScript | Rust =>
          body.map(_.expressionIO.toStringInLanguage(programmingLanguage, humanLanguage, skipUnparsable)).mkString("\n")
        case Cpp =>
          // In C++, semicolons belong to statements, not expression contexts.
          // This sequence type is also used for conditions / expressions (e.g. if/while condition),
          // so we only auto-append semicolons for statement-like sequences.
          if (sequenceInfo.mustEvaluateTo.nonEmpty) {
            body.map(_.expressionIO.toStringInLanguage(programmingLanguage, humanLanguage, skipUnparsable)).mkString("\n")
          } else {
            body
              .map(_.expressionIO.toStringInLanguage(programmingLanguage, humanLanguage, skipUnparsable))
              .map { rendered =>
                val trimmed = rendered.trim
                if (trimmed.isEmpty) rendered
                else if (trimmed.contains("\n")) rendered
                else if (trimmed.endsWith(";") || trimmed.endsWith("}")) rendered
                else rendered + ";"
              }
              .mkString("\n")
          }
        case Lisp =>
          if (body.isEmpty) "(progn)"
          else {
            val builder = CodeStringBuilder("(progn")
              .changeIntLevel(1)
            val withBody = body.foldLeft(builder) { (acc, expr) =>
              acc.appendAsLines(expr.expressionIO.toStringInLanguage(programmingLanguage, humanLanguage, skipUnparsable))
            }
            withBody.changeIntLevel(-1)
              .appendNextLine(")")
              .toString
          }
        case _ => {
          var res = CodeStringBuilder(s"BeSequence(")
            .changeIntLevel(2)
            .appendNextLine(s"//info:=$sequenceInfo")
            .changeIntLevel(-1)
          if (body.nonEmpty) res = res.changeForEach(body, (old, curExpr) => old.appendAsLines(curExpr.toString))
          else res = res.appendNextLine("[no body]")
          res.changeIntLevel(-1)
            .appendNextLine(")").toString
        }
      }
    }


  }

  override def expressionExecutor(simulatorConfig: BeSimulatorConfig, stateBeforeExecution: BeSimulatorState): BeExpressionExecutor = new BeExpressionExecutor(simulatorConfig, stateBeforeExecution, this) {
    override protected def childExpressionsToExecute(stateBeforeExecution: BeSimulatorState): List[BeExpression] = body

    override protected def applySideEffectsOfThisBlock(stateBeforeExecution: BeSimulatorState, childrenResults: List[(BeSimulatorState, BeDataValue)]): BeSimulatorState = stateBeforeExecution

    override protected def executeThisBlockInSimulatorAndGetValue(stateBeforeExecution: BeSimulatorState, childrenResults: List[(BeSimulatorState, BeDataValue)]): (BeSimulatorState, BeDataValue) =
      (stateBeforeExecution, BeDataValueUnit())
  }


  override def getChildren(withExtensions: Boolean, myScope: BeScope): List[BeExpressionNode] = {

    def getChildPosFor(nr: Int): BeChildPosition = BeChildPosition(BeChildRole.ExpressionInSequence(nr), InSequenceScope(this, myScope))

    val res = if (!withExtensions || sequenceInfo.maxBodyElements.nonEmpty && body.size >= sequenceInfo.maxBodyElements.get) body.zipWithIndex.map((curExpr, curNr) =>
      BeExpressionReference(getChildPosFor(curNr), curExpr)
    )
    else {
      val bodyWithExtensions: List[BeExpressionNode] = body.zipWithIndex.flatMap((curExpr, curNr) => List(
        BeExtensionPoint(false, getChildPosFor(2 * curNr), BeDataType.Unit),
        BeExpressionReference(getChildPosFor(2 * curNr + 1), curExpr)
      ))

      def lastExtendAnyOption: Option[BeExtensionPoint] = {
        if (sequenceInfo.maxBodyElements.isEmpty || sequenceInfo.maxBodyElements.get > body.size)
          Some(BeExtensionPoint(false, getChildPosFor(bodyWithExtensions.size), BeDataType.Unit))
        else None
      }

      def lastExtendCorrectOption: Option[BeExtensionPoint] = {
        if (sequenceInfo.mustEvaluateTo.nonEmpty && !sequenceInfo.mustEvaluateTo.get.canTakeValuesFrom(body.last.staticInformationExpression.staticType).possibleWithoutSyntaxErrors)
          Some(BeExtensionPoint(false, getChildPosFor(bodyWithExtensions.size), BeDataType.Unit))
        else None
      }

      bodyWithExtensions ++ lastExtendAnyOption ++ lastExtendCorrectOption
    }

    // println("BeSequence::getChildren " + body.size + " -> " + res.size)

    res
  }

  override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = {
    val orderedChildren = newChildren.collect {
      case (BeChildRole.ExpressionInSequence(nr), expr) => nr -> expr
    }.sortBy(_._1)

    if (orderedChildren.isEmpty) myRef
    else copy(body = orderedChildren.map(_._2))
  }
}

object BeSequence {

  def optionalBody(body: List[BeExpression]) = BeSequence(body, BeSequenceInfo(None, None))

  def conditionalBody(body: List[BeExpression]) = BeSequence(body, BeSequenceInfo(Some(BeDataType.Boolean), Some(1)))

}
