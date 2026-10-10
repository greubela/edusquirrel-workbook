package it.evadid.workbook.elements.interactionElements.programming.state

import it.evadid.core.datastructures.language.AppLanguage.{English, Java}
import it.evadid.core.datastructures.vectorShapes.svg.BeExpressionToTurtleCommands
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.workbook.elements.interactionElements.programming.*
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.{ProgrammingStateJavaString, ProgrammingStatePythonString, ProgrammingStateSnapXMLWithAdditionalFloatingObjects, ProgrammingStateSnapXml}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingStateBeExpression
import it.evadid.workbook.elements.interactionElements.programming.state.snap.{ProgrammingStateSnapXmlHelper, SnapTurtlePythonBridge}
import upickle.default.*

/** The source representation currently edited by a programming exercise. */
sealed trait ProgrammingState derives ReadWriter {
  def toBeExpressionState: ProgrammingStateBeExpression

  def toSnapXml: ProgrammingStateSnapXml

  def toPython: ProgrammingStatePythonString

  def toJava: ProgrammingStateJavaString

  def fingerprint(): String = this match
    case ProgrammingStateBeExpression(expression) => s"expression:${expression.toString}"
    case ProgrammingStateSnapXml(xml) => s"snap:$xml"
    case ProgrammingStateSnapXMLWithAdditionalFloatingObjects(xml, objects) =>
      s"snap-floating:$xml\u0000${objects.mkString("\u0000")}"
    case ProgrammingStatePythonString(code) => s"python:$code"
    case ProgrammingStateJavaString(code) => s"java:$code"
}

object ProgrammingState {

  final case class ProgrammingStateBeExpression(expression: BeExpression) extends ProgrammingState {
    override def toBeExpressionState: ProgrammingStateBeExpression = this

    override def toSnapXml: ProgrammingStateSnapXml = ProgrammingStateSnapXmlHelper.fromProgram(BeProgram(expression))

    override def toPython: ProgrammingStatePythonString =
      ProgrammingStatePythonString(SnapTurtlePythonBridge.printedPython(expression))

    override def toJava: ProgrammingStateJavaString =
      ProgrammingStateJavaString(expression.structureInfo.toStringInLanguage(Java, English, false))

    def deriveTurtleCommands: List[TurtleCommand[Double]] = BeExpressionToTurtleCommands(expression)

    def executedTurtleCommands(): List[TurtleCommand[Double]] = deriveTurtleCommands

    override val toString: String = s"ProgrammingStateBeExpression(${expression.toString.take(300)})"

  }

  object ProgrammingState {

    final case class ProgrammingStateSnapXml(val snapXml: String) extends ProgrammingState {
      /** Drop regenerated preview/pen images without changing scripts or authored costumes. */
      def removeBloatFromXml: ProgrammingStateSnapXml = {
        val cleaned = ProgrammingStateSnapXmlHelper.removeGeneratedImages(snapXml)
        if cleaned == snapXml then this else copy(snapXml = cleaned)
      }

      override def toBeExpressionState: ProgrammingStateBeExpression =
        ProgrammingStateBeExpression(SnapStateConversion.expressionFromXml(snapXml))

      override def toSnapXml: ProgrammingStateSnapXml = this

      override def toPython: ProgrammingStatePythonString = toBeExpressionState.toPython

      override def toJava: ProgrammingStateJavaString = toBeExpressionState.toJava

      override val toString: String = s"ProgrammingStateSnapXml(${snapXml.toString.take(300)})"
    }

    final case class ProgrammingStateSnapXMLWithAdditionalFloatingObjects(
                                                                           val snapXml: String,
                                                                           additionalFloatingObjects: List[String]
                                                                         ) extends ProgrammingState {
      override def toBeExpressionState: ProgrammingStateBeExpression = ProgrammingStateSnapXml(snapXml).toBeExpressionState

      override def toSnapXml: ProgrammingStateSnapXml = ProgrammingStateSnapXml(snapXml)

      override def toPython: ProgrammingStatePythonString = toBeExpressionState.toPython

      override def toJava: ProgrammingStateJavaString = toBeExpressionState.toJava

    }

    final case class ProgrammingStatePythonString(code: String) extends ProgrammingState {
      override def toBeExpressionState: ProgrammingStateBeExpression =
        ProgrammingStateBeExpression(BeProgram.fromPythonString(code).fullProgram)

      override def toSnapXml: ProgrammingStateSnapXml =
        SnapTurtlePythonBridge.applyPython(code).fold(message => throw IllegalArgumentException(message), identity)

      override def toPython: ProgrammingStatePythonString = this

      override def toJava: ProgrammingStateJavaString = toBeExpressionState.toJava

      override val toString: String = s"ProgrammingStatePythonString(${code.toString.take(300)})"
    }

    final case class ProgrammingStateJavaString(code: String) extends ProgrammingState {
      override def toBeExpressionState: ProgrammingStateBeExpression =
        ProgrammingStateBeExpression(JavaToBeExpressionParser.parse(code))

      override def toSnapXml: ProgrammingStateSnapXml = toBeExpressionState.toSnapXml

      override def toPython: ProgrammingStatePythonString = toBeExpressionState.toPython

      override def toJava: ProgrammingStateJavaString = this

      override val toString: String = s"ProgrammingStateJavaString(${code.toString.take(300)})"
    }

  }

}
