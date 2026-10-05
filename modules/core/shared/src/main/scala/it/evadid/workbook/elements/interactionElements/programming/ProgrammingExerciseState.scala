package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.core.datastructures.language.AppLanguage.{English, Java}
import it.evadid.core.datastructures.vectorShapes.svg.BeExpressionToTurtleCommands
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import upickle.default.*

/** The source representation currently edited by a programming exercise. */
sealed trait ProgrammingState derives ReadWriter {
  def toBeExpressionState: ProgrammingStateBeExpression
  def toSnapXml: ProgrammingStateSnapXml
  def toPython: ProgrammingStatePythonString
  def toJava: ProgrammingStateJavaString

  /** Compatibility spelling; new code should make the target representation explicit. */
  final def toBeExpression: ProgrammingStateBeExpression = toBeExpressionState
}

final case class ProgrammingStateBeExpression(expression: BeExpression) extends ProgrammingState {
  override def toBeExpressionState: ProgrammingStateBeExpression = this
  override def toSnapXml: ProgrammingStateSnapXml = ProgrammingExerciseState.fromProgram(BeProgram(expression))
  override def toPython: ProgrammingStatePythonString =
    ProgrammingStatePythonString(SnapTurtlePythonBridge.printedPython(expression))
  override def toJava: ProgrammingStateJavaString =
    ProgrammingStateJavaString(expression.structureInfo.toStringInLanguage(Java, English, false))

  def deriveTurtleCommands: List[TurtleCommand[Double]] = BeExpressionToTurtleCommands(expression)
  def executedTurtleCommands(): List[TurtleCommand[Double]] = deriveTurtleCommands
}

final case class ProgrammingStateSnapXml(val snapXml: String) extends ProgrammingState {
  override def toBeExpressionState: ProgrammingStateBeExpression =
    ProgrammingStateBeExpression(SnapStateConversion.expressionFromXml(snapXml))
  override def toSnapXml: ProgrammingStateSnapXml = this
  override def toPython: ProgrammingStatePythonString = toBeExpressionState.toPython
  override def toJava: ProgrammingStateJavaString = toBeExpressionState.toJava
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
}
final case class ProgrammingStateJavaString(code: String) extends ProgrammingState {
  override def toBeExpressionState: ProgrammingStateBeExpression =
    ProgrammingStateBeExpression(JavaToBeExpressionParser.parse(code))
  override def toSnapXml: ProgrammingStateSnapXml = toBeExpressionState.toSnapXml
  override def toPython: ProgrammingStatePythonString = toBeExpressionState.toPython
  override def toJava: ProgrammingStateJavaString = this
}

/** Small, shared Snap reader used by the state conversion boundary. It deliberately accepts the
  * canonical primitive subset emitted by [[SnapProjectXml]]; richer legacy projects remain the
  * responsibility of the legacy importer until that importer is moved into core.
  */
private object SnapStateConversion {
  def expressionFromXml(xml: String): BeExpression = {
    val scripts = SnapXmlParser.elements(xml, "scripts")
      .flatMap(container => SnapXmlParser.children(container.inner).filter(_.tag == "script"))
      .flatMap(script => statements(script.inner, 0))
    if scripts.isEmpty && xml.contains("<block") then
      throw IllegalArgumentException("Snap project contains blocks outside the supported state-conversion subset")
    BeProgram.fromPythonString(scripts.mkString("\n")).fullProgram
  }

  private def statements(source: String, indentation: Int): List[String] =
    SnapXmlParser.children(source).collect {
      case block if block.tag == "block" && block.attrOrEmpty("s") != "receiveGo" =>
        renderBlock(block, indentation)
    }

  private def renderBlock(block: SnapXmlParser.Element, indentation: Int): String = {
    val selector = block.attrOrEmpty("s")
    val prefix = " " * indentation
    SnapTurtleCatalog.primitiveBySnapSelector.get(selector) match
      case Some(primitive) =>
        val args = SnapXmlParser.children(block.inner).filter(e => e.tag == "l" || e.tag == "color")
          .map(argument).take(primitive.arity)
        prefix + primitive.pythonName + "(" + args.mkString(", ") + ")"
      case None if selector == "doRepeat" =>
        val children = SnapXmlParser.children(block.inner)
        val count = children.find(_.tag == "l").map(argument).getOrElse("0")
        val body = children.find(_.tag == "script").toList.flatMap(s => statements(s.inner, indentation + 4))
        prefix + s"for _ in range($count):\n" + body.mkString("\n")
      case None => throw IllegalArgumentException(s"Unsupported Snap selector in state conversion: $selector")
  }

  private def argument(element: SnapXmlParser.Element): String =
    if element.tag == "color" then SnapInputCodec.pythonQuotedColorFromRaw(element.inner).getOrElse("\"black\"")
    else {
      val value = SnapXmlParser.unescape(element.inner).trim
      if value.matches("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)") || value == "True" || value == "False" then value
      else SnapInputCodec.quotePython(value)
    }
}

/**
 * Source-compatible name for the former, Snap-only exercise state. New code
 * should accept [[ProgrammingState]] and narrow only at an editor boundary.
 */
type ProgrammingExerciseState = ProgrammingStateSnapXml

object ProgrammingExerciseState {
  def apply(snapXml: String): ProgrammingStateSnapXml = ProgrammingStateSnapXml(snapXml)
  def unapply(state: ProgrammingStateSnapXml): Some[String] = Some(state.snapXml)

  /** @param previousXml XML being replaced; custom block definitions are merged forward */
  def fromProgram(
      program: BeProgram,
      canvasLayout: SnapCanvasLayout = SnapCanvasLayout.empty,
      previousXml: String = ""
  ): ProgrammingStateSnapXml =
    ProgrammingStateSnapXml(SnapCustomBlockMerge.applyProgram(program, canvasLayout, previousXml))

  def mini: ProgrammingStateSnapXml = ProgrammingStateSnapXml(SnapProjectXml.mini)
  def empty: ProgrammingStateSnapXml = ProgrammingStateSnapXml(SnapProjectXml.empty)
  def fingerprint(state: ProgrammingStateSnapXml): String = state.snapXml
}

object ProgrammingState {
  def fingerprint(state: ProgrammingState): String = state match
    case ProgrammingStateBeExpression(expression) => s"expression:${expression.toString}"
    case ProgrammingStateSnapXml(xml) => s"snap:$xml"
    case ProgrammingStateSnapXMLWithAdditionalFloatingObjects(xml, objects) =>
      s"snap-floating:$xml\u0000${objects.mkString("\u0000")}"
    case ProgrammingStatePythonString(code) => s"python:$code"
    case ProgrammingStateJavaString(code) => s"java:$code"
}
