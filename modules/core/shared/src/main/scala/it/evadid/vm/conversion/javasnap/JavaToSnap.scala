package it.evadid.vm.conversion.javasnap

import it.evadid.vm.conversion.expressionpython.JavaTurtlePythonExport
import it.evadid.vm.conversion.expressionsnap.SnapProjectXml
import it.evadid.vm.conversion.snapexpression.SnapControlFlow
import it.evadid.vm.conversion.snapexpression.SnapInputCodec
import it.evadid.vm.conversion.snapjava.SnapToJava
import it.evadid.vm.conversion.snapxml.SnapXmlParser

import it.evadid.vm.conversion.javaexpression.checked.{JavaTurtleResolution as R, JavaTurtleVmPrograms as P}
import it.evadid.vm.parsing.python.clean.model.PyAST.*
import it.evadid.vm.parsing.python.clean.model.PythonType.*
import it.evadid.workbook.elements.interactionElements.programming.state.*

import it.evadid.vm.conversion.javaediting.JavaEditingMetadata.*

object JavaToSnap {
  def toSnap(program: P.Program): Either[String, ProgrammingStateSnapXml] = attempt {
    val (meta, names) = metadata(program)
    def literal(value: String): String = s"<l>${SnapInputCodec.escapeXml(value)}</l>"
    def block(selector: String, inputs: String): String = s"<block s=\"$selector\">$inputs</block>"
    def boolean(flag: Boolean): String = block("reportBoolean", s"<l><bool>$flag</bool></l>")
    def stored(value: String, kind: R.ValueType): String = if numeric(kind) then block("reportJavaCell", value) else value
    def expression(value: R.Expression): String = value match {
      case R.IntLiteral(number) => literal(number.toString)
      case R.DoubleLiteral(number) => literal(it.evadid.vm.conversion.expressionpython.JavaTurtlePythonExport.doubleLiteral(number))
      case R.BooleanLiteral(flag) => boolean(flag)
      case R.Read(variable) =>
        if numeric(variable.valueType) then block("reportJavaRead", literal(names(variable.id)))
        else s"<block var=\"${SnapInputCodec.escapeXml(names(variable.id))}\"/>"
      case R.Update(variable, operator, prefix) => block("reportJavaUpdate", literal(names(variable.id)) +
        literal(if operator == R.UpdateOperator.Increment then "1" else "-1") + boolean(prefix) + boolean(variable.valueType == R.ValueType.DoubleValue))
      case R.Widen(inner) => block("reportJavaDouble", literal("pos") + expression(inner) + literal("0"))
      case R.Group(inner) => expression(inner)
      case R.Unary(R.UnaryOperator.Not, inner) => block("reportNot", expression(inner))
      case R.Unary(operator, inner) => block(if value.valueType == R.ValueType.DoubleValue then "reportJavaDouble" else "reportJavaInt",
        literal(if operator == R.UnaryOperator.Negate then "neg" else "pos") + expression(inner) + literal("0"))
      case R.Binary(operator, left, right) if arithmetic(operator) => block(if value.valueType == R.ValueType.DoubleValue then "reportJavaDouble" else "reportJavaInt",
        literal(operation(operator)) + expression(left) + expression(right))
      case R.Binary(operator, left, right) if numeric(left.valueType) && numeric(right.valueType) => block("reportJavaCompare",
        literal(comparison(operator)) + expression(left) + expression(right) + boolean(left.valueType == R.ValueType.DoubleValue || right.valueType == R.ValueType.DoubleValue))
      case R.Binary(operator, left, right) => block(SnapControlFlow.OperatorToSnapReporter(BinaryTokens(operator)).selector,
        "<list>" + expression(left) + expression(right) + "</list>")
      case R.ShortCircuit(operator, left, right) => block(if operator == R.ShortCircuitOperator.And then "reportVariadicAnd" else "reportVariadicOr",
        "<list>" + expression(left) + expression(right) + "</list>")
    }
    def statements(body: R.Block): String = body.statements.map {
      case R.Empty => ""
      case R.Return => block("doStopThis", "<l><option>this block</option></l>")
      case R.Declare(variable, initial) => initial.fold("")(value => block("doSetVar", literal(names(variable.id)) + stored(expression(widened(value, variable.valueType)), variable.valueType)))
      case R.Assign(variable, operator, value) => block("doSetVar", literal(names(variable.id)) + stored(expression(assignment(variable, operator, value)), variable.valueType))
      case R.Evaluate(R.Update(variable, operator, _)) => block("doSetVar", literal(names(variable.id)) + stored(expression(R.Update(variable, operator, true)), variable.valueType))
      case R.Evaluate(_) => reject("This discarded expression cannot be represented as a Snap command.")
      case R.Call(R.CallTarget.Turtle(command), args) => block(if command == R.TurtleCommand.Forward then "doJavaForward" else "doJavaTurnRight", args.map(expression).mkString)
      case R.Call(R.CallTarget.Helper(id), args) =>
        val method = meta.method(program.bindings.source.methods.find(_.id == id).get.name)
        s"<custom-block s=\"${SnapInputCodec.escapeXml(method.callSpec)}\">${args.zip(method.parameters).map((value, parameter) => stored(expression(value), parameter.kind)).mkString}</custom-block>"
      case R.If(condition, positive, negative) => block(if negative.isDefined then "doIfElse" else "doIf",
        expression(condition) + "<script>" + statements(positive) + "</script>" + negative.fold("<list></list>")(body => "<script>" + statements(body) + "</script>"))
      case R.While(condition, body) => block("doUntil", block("reportNot", expression(condition)) + "<script>" + statements(body) + "</script>")
      case R.For(init, condition, update, body) =>
        val updates = R.Block(update.statements.map {
          case R.Evaluate(R.Update(variable, operator, _)) => R.Assign(variable, if operator == R.UpdateOperator.Increment then R.AssignmentOperator.Add else R.AssignmentOperator.Subtract, R.IntLiteral(1))
          case other => other
        })
        statements(init) + block("doUntil", block("reportNot", condition.fold(boolean(true))(expression)) +
          "<script>" + statements(body) + (if completesNormally(body) then statements(updates) else "") + "</script>")
    }.mkString
    val definitions = program.bindings.source.methods.map { method =>
      val description = meta.method(method.name)
      val declarations = if description.locals.isEmpty then "" else block("doDeclareVariables",
        "<list>" + description.locals.map(variable => literal(variable.name)).mkString + "</list>")
      val inputs = description.parameters.map(variable => s"<input type=\"${if variable.kind == R.ValueType.BooleanValue then "%b" else "%n"}\"></input>").mkString
      s"<block-definition s=\"${SnapInputCodec.escapeXml(description.spec)}\" type=\"command\" category=\"variables\"><inputs>$inputs</inputs><script>$declarations${statements(method.body)}</script></block-definition>"
    }.mkString
    val base = SnapProjectXml.empty
    val withNotes = base.replace("<notes></notes>", "<notes>" + SnapInputCodec.escapeXml(NotesPrefix + meta.json) + "</notes>")
    val scene = SnapXmlParser.elements(withNotes, "scene").head
    val blocks = SnapXmlParser.child(scene.inner, "blocks").get
    val blocksAt = scene.start + scene.outer.indexOf('>') + 1 + blocks.start
    val withDefinitions = withNotes.substring(0, blocksAt) + "<blocks>" + definitions + "</blocks>" + withNotes.substring(blocksAt + blocks.outer.length)
    val sprite = SnapXmlParser.elements(withDefinitions, "sprite").head
    val scripts = SnapXmlParser.child(sprite.inner, "scripts").get
    val scriptsAt = sprite.start + sprite.outer.indexOf('>') + 1 + scripts.start
    val start = "<scripts><script x=\"156\" y=\"66\"><block s=\"receiveGo\"></block><block s=\"doJavaReset\"></block><custom-block s=\"main\"></custom-block></script></scripts>"
    val xml = withDefinitions.substring(0, scriptsAt) + start + withDefinitions.substring(scriptsAt + scripts.outer.length)
    bounded(xml)
    val result = ProgrammingStateSnapXml(xml)
    SnapToJava.fromSnap(result).fold(reject, _ => result)
  }

}
