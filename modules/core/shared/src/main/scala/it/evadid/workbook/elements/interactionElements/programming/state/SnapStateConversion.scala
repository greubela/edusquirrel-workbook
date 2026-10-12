package it.evadid.workbook.elements.interactionElements.programming.state

import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.workbook.elements.interactionElements.programming.state.snap.{SnapControlFlow, SnapInputCodec, SnapTurtleCatalog, SnapXmlParser}


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
      case None if selector == "doIf" || selector == "doIfElse" =>
        val children = SnapXmlParser.children(block.inner)
        val condition = children.find(_.tag != "script").map(value).getOrElse("False")
        val bodies = children.filter(_.tag == "script")
        def body(index: Int): String = nonEmptyBody(
          bodies.lift(index).toList.flatMap(s => statements(s.inner, indentation + 4)), indentation + 4)
        val thenPart = prefix + s"if $condition:\n" + body(0)
        if selector == "doIfElse" then thenPart + "\n" + prefix + "else:\n" + body(1)
        else thenPart
      case None if selector == "doUntil" =>
        val children = SnapXmlParser.children(block.inner)
        val condition = children.find(_.tag != "script").map(value).getOrElse("True")
        val body = children.find(_.tag == "script").toList.flatMap(s => statements(s.inner, indentation + 4))
        prefix + s"while not ($condition):\n" + nonEmptyBody(body, indentation + 4)
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

