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
  override def toSnapXml: ProgrammingStateSnapXml = ProgrammingStateSnapXml.fromProgram(BeProgram(expression))
  override def toPython: ProgrammingStatePythonString =
    ProgrammingStatePythonString(SnapTurtlePythonBridge.printedPython(expression))
  override def toJava: ProgrammingStateJavaString =
    ProgrammingStateJavaString(expression.structureInfo.toStringInLanguage(Java, English, false))

  def deriveTurtleCommands: List[TurtleCommand[Double]] = BeExpressionToTurtleCommands(expression)
  def executedTurtleCommands(): List[TurtleCommand[Double]] = deriveTurtleCommands

  override val toString: String = s"ProgrammingStateBeExpression(${expression.toString.take(300)})"
}

final case class ProgrammingStateSnapXml(val snapXml: String) extends ProgrammingState {
  /** Drop regenerated preview/pen images without changing scripts or authored costumes. */
  def removeBloatFromXml: ProgrammingStateSnapXml = {
    val cleaned = ProgrammingStateSnapXml.removeGeneratedImages(snapXml)
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

/** Small, shared Snap reader used by the state conversion boundary. It deliberately accepts the
  * canonical primitive subset emitted by [[SnapProjectXml]]; richer legacy projects remain the
  * responsibility of the legacy importer until that importer is moved into core.
  */
private object SnapStateConversion {
  def expressionFromXml(xml: String): BeExpression = {
    val definitions = SnapXmlParser.elements(xml, "block-definition").map(renderDefinition)
    val scripts = SnapXmlParser.elements(xml, "scripts")
      .flatMap(container => SnapXmlParser.children(container.inner).filter(_.tag == "script"))
      .flatMap(script => statements(script.inner, 0))
    if definitions.isEmpty && scripts.isEmpty && xml.contains("<block") then
      throw IllegalArgumentException("Snap project contains blocks outside the supported state-conversion subset")
    BeProgram.fromPythonString((definitions ++ scripts).mkString("\n\n")).fullProgram
  }

  private def statements(source: String, indentation: Int): List[String] =
    SnapXmlParser.children(source).collect {
      case block if block.tag == "block" && block.attrOrEmpty("s") != "receiveGo" =>
        renderBlock(block, indentation)
      case block if block.tag == "custom-block" => renderCall(block, indentation)
    }

  private def renderBlock(block: SnapXmlParser.Element, indentation: Int): String = {
    val selector = block.attrOrEmpty("s")
    val prefix = " " * indentation
    SnapTurtleCatalog.primitiveBySnapSelector.get(selector) match
      case Some(primitive) =>
        val args = SnapXmlParser.children(block.inner).map(value).take(primitive.arity)
        prefix + primitive.pythonName + "(" + args.mkString(", ") + ")"
      case None if selector == "doRepeat" =>
        val children = SnapXmlParser.children(block.inner)
        val count = children.find(_.tag != "script").map(value).getOrElse("0")
        val body = children.find(_.tag == "script").toList.flatMap(s => statements(s.inner, indentation + 4))
        prefix + s"for _ in range($count):\n" + nonEmptyBody(body, indentation + 4)
      case None if selector == "doFor" =>
        val children = SnapXmlParser.children(block.inner)
        val values = children.filter(_.tag != "script")
        val name = values.headOption.map(literalText).getOrElse("i")
        val start = values.lift(1).map(value).getOrElse("1")
        val end = values.lift(2).map(value).getOrElse("1")
        val body = children.find(_.tag == "script").toList.flatMap(s => statements(s.inner, indentation + 4))
        prefix + s"for $name in range($start, $end + 1):\n" + nonEmptyBody(body, indentation + 4)
      case None if selector == "doSetVar" || selector == "doChangeVar" =>
        val children = SnapXmlParser.children(block.inner)
        val name = children.headOption.map(literalText).getOrElse("x")
        val rhs = children.lift(1).map(value).getOrElse("0")
        if selector == "doChangeVar" then prefix + s"$name = $name + $rhs"
        else prefix + s"$name = $rhs"
      case None => throw IllegalArgumentException(s"Unsupported Snap selector in state conversion: $selector")
  }

  private def renderDefinition(definition: SnapXmlParser.Element): String = {
    val spec = definition.attrOrEmpty("s")
    val name = SnapTurtleCatalog.pythonNameFromCustomSpec(spec)
    val params = SnapTurtleCatalog.inputNamesFromSpec(spec)
    val body = SnapXmlParser.children(definition.inner).find(_.tag == "script")
      .toList.flatMap(script => statements(script.inner, 4))
    s"def $name(${params.mkString(", ")}):\n${nonEmptyBody(body, 4)}"
  }

  private def renderCall(block: SnapXmlParser.Element, indentation: Int): String = {
    val name = if block.tag == "custom-block" then
      SnapTurtleCatalog.pythonNameFromCustomSpec(block.attrOrEmpty("s"))
    else SnapTurtleCatalog.canonicalPythonName(block.attrOrEmpty("s"))
    val args = SnapXmlParser.children(block.inner).map(value)
    " " * indentation + s"$name(${args.mkString(", ")})"
  }

  private def value(element: SnapXmlParser.Element): String =
    element.attr("var") match
      case Some(name) => name
      case None if element.tag == "l" || element.tag == "color" => argument(element)
      case None if element.tag == "custom-block" => renderCall(element, 0)
      case None if element.tag == "list" =>
        SnapXmlParser.children(element.inner).map(value).mkString("(", ", ", ")")
      case None if element.tag == "block" =>
        val selector = element.attrOrEmpty("s")
        if selector == "reportTrue" then "True"
        else if selector == "reportFalse" then "False"
        else {
          val operators = SnapControlFlow.OperatorToSnapReporter.map { (op, reporter) => reporter.selector -> op }
          operators.get(selector) match
            case Some("not") => s"not ${SnapXmlParser.children(element.inner).headOption.map(value).getOrElse("True")}"
            case Some(op) =>
              val args = SnapXmlParser.children(element.inner).flatMap { child =>
                if child.tag == "list" then SnapXmlParser.children(child.inner).map(value) else List(value(child))
              }
              args.mkString("(", s" $op ", ")")
            case None => renderCall(element, 0)
        }
      case None => "0"

  private def literalText(element: SnapXmlParser.Element): String =
    SnapXmlParser.unescape(element.inner).trim

  private def nonEmptyBody(body: List[String], indentation: Int): String =
    if body.nonEmpty then body.mkString("\n") else " " * indentation + "pass"

  private def argument(element: SnapXmlParser.Element): String =
    if element.tag == "color" then SnapInputCodec.pythonQuotedColorFromRaw(element.inner).getOrElse("\"black\"")
    else {
      val value = SnapXmlParser.unescape(element.inner).trim
      if value.matches("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)") || value == "True" || value == "False" then value
      else SnapInputCodec.quotePython(value)
    }
}


object ProgrammingStateSnapXml {
  private def removeGeneratedImages(xml: String): String = {
    val removableTags = Set("pentrails", "pentrail", "thumbnail")
    val result = new StringBuilder
    var keptFrom = 0
    var cursor = 0
    while cursor < xml.length do
      val opening = xml.indexOf('<', cursor)
      if opening < 0 then cursor = xml.length
      else if xml.startsWith("<!--", opening) || xml.startsWith("<![CDATA[", opening) || xml.startsWith("<?", opening) then
        val terminator = if xml.startsWith("<!--", opening) then "-->"
          else if xml.startsWith("<![CDATA[", opening) then "]]>" else "?>"
        val end = xml.indexOf(terminator, opening + 2)
        cursor = if end < 0 then xml.length else end + terminator.length
      else {
        var nameEnd = opening + 1
        while nameEnd < xml.length && (xml.charAt(nameEnd).isLetterOrDigit || "_-:.".contains(xml.charAt(nameEnd))) do
          nameEnd += 1
        val tag = xml.substring(opening + 1, nameEnd)
        if removableTags.contains(tag) then
          // Reuse Snap's matching-close scanner, including nested and self-closing nodes.
          SnapXmlParser.parseElementAt(xml, opening) match
            case Some(element) =>
              result.append(xml.substring(keptFrom, opening))
              cursor = element.end
              keptFrom = cursor
            case _ => cursor = opening + 1
        else cursor = opening + 1
      }
    result.append(xml.substring(keptFrom)).toString
  }

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
