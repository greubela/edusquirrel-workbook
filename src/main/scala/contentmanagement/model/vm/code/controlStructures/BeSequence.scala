package contentmanagement.model.vm.code.controlStructures

import contentmanagement.model.language.AppLanguage.*
import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.code.tree.{BeExpressionNode, BeExpressionReference, BeExtensionPoint}
import contentmanagement.model.vm.code.{BeControlStructure, BeExpression}
import contentmanagement.model.vm.io.BeExpressionIO
import contentmanagement.model.vm.simulation.{BeExpressionExecutor, BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.static.BeExpressionStaticInformation
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeDataType.AnyType
import contentmanagement.model.vm.types.BeScope.InSequenceScope
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import interactionPlugins.blockEnvironment.programming.blockdisplay.control.BeBlockSequence
import util.CodeStringBuilder

case class BeSequenceInfo(mustEvaluateTo: Option[BeDataType], maxBodyElements: Option[Int] = None)

case class BeSequence(body: List[BeExpression], sequenceInfo: BeSequenceInfo) extends BeControlStructure {

  def allPossibleBodies: List[BeExpression] = body
  
  private val myRef = this


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
        if (sequenceInfo.mustEvaluateTo.nonEmpty && !sequenceInfo.mustEvaluateTo.get.canTakeValuesFrom(body.last.expressionStaticInformation.staticType).possibleWithoutSyntaxErrors)
          Some(BeExtensionPoint(false, getChildPosFor(bodyWithExtensions.size), BeDataType.Unit))
        else None
      }
      bodyWithExtensions ++ lastExtendAnyOption ++ lastExtendCorrectOption
    }

   // println("BeSequence::getChildren " + body.size + " -> " + res.size)

    res
  }



  override def expressionStaticInformation: BeExpressionStaticInformation = new BeExpressionStaticInformation {
    override def staticType: BeDataType = sequenceInfo.mustEvaluateTo.getOrElse(body.lastOption.map(_.expressionStaticInformation.staticType).getOrElse(BeDataType.Error))

    override def staticValue: Option[BeDataValue] = body.lastOption.flatMap(_.expressionStaticInformation.staticValue)
        
    override def syntaxErrors: Seq[BeInfo] = List()

    override def hasSideEffects: Boolean = false

  }

  override def expressionIO: BeExpressionIO = new BeExpressionIO {  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
    programmingLanguage match {
      case Python => body.map(_.expressionIO.getInLanguage(programmingLanguage, humanLanguage)).mkString("\n")
      case Java | JavaScript | Rust =>
        body.map(_.expressionIO.getInLanguage(programmingLanguage, humanLanguage)).mkString("\n")
      case Lisp =>
        if (body.isEmpty) "(progn)"
        else {
          val builder = CodeStringBuilder("(progn")
            .changeIntLevel(1)
          val withBody = body.foldLeft(builder) { (acc, expr) =>
            acc.appendAsLines(expr.expressionIO.getInLanguage(programmingLanguage, humanLanguage))
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

    override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = {
      val orderedChildren = newChildren.collect {
        case (BeChildRole.ExpressionInSequence(nr), expr) => nr -> expr
      }.sortBy(_._1)

      if (orderedChildren.isEmpty) myRef
      else copy(body = orderedChildren.map(_._2))
    }
    
    override def createBlock(): BeBlock = BeBlockSequence(myRef)
  }

  override def expressionExecutor(simulatorConfig: BeSimulatorConfig, stateBeforeExecution: BeSimulatorState): BeExpressionExecutor = new BeExpressionExecutor(simulatorConfig, stateBeforeExecution, this) {
    override protected def childExpressionsToExecute(stateBeforeExecution: BeSimulatorState): List[BeExpression] = body

    override protected def applySideEffectsOfThisBlock(stateBeforeExecution: BeSimulatorState, childrenResults: List[(BeSimulatorState, BeDataValue)]): BeSimulatorState = stateBeforeExecution

    override protected def executeThisBlockInSimulatorAndGetValue(stateBeforeExecution: BeSimulatorState, childrenResults: List[(BeSimulatorState, BeDataValue)]): (BeSimulatorState, BeDataValue) =
      (stateBeforeExecution, BeDataValueUnit())
  }









  
}

object BeSequence {

  def optionalBody(body: List[BeExpression]) = BeSequence(body, BeSequenceInfo(None, None))

  def conditionalBody(body: List[BeExpression]) = BeSequence(body, BeSequenceInfo(Some(BeDataType.Boolean), Some(1)))

}