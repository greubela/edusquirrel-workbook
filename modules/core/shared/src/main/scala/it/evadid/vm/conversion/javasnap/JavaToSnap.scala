package it.evadid.vm.conversion.javasnap

import it.evadid.vm.conversion.expressionpython.JavaTurtlePythonExport
import it.evadid.vm.conversion.expressionsnap.SnapProjectXml
import it.evadid.vm.conversion.snapexpression.SnapControlFlow
import it.evadid.vm.conversion.snapexpression.SnapInputCodec
import it.evadid.vm.conversion.snapjava.SnapToJava
import it.evadid.vm.conversion.snapxml.SnapXmlParser

import it.evadid.vm.conversion.javaexpression.checked.{JavaTurtleResolution as R, JavaTurtleVmPrograms as P}
import it.evadid.workbook.elements.interactionElements.programming.state.*

import it.evadid.vm.conversion.javaediting.JavaEditingMetadata.*

object JavaToSnap {
  private def expressionsIn(value: R.Expression): Iterator[R.Expression] = Iterator(value) ++ (value match {
    case R.Widen(inner) => expressionsIn(inner)
    case R.Group(inner) => expressionsIn(inner)
    case R.Unary(_, inner) => expressionsIn(inner)
    case R.Binary(_, left, right) => expressionsIn(left) ++ expressionsIn(right)
    case R.ShortCircuit(_, left, right) => expressionsIn(left) ++ expressionsIn(right)
    case _ => Iterator.empty
  })

  private def statementsIn(body: R.Block): Iterator[R.Statement] = body.statements.iterator.flatMap { statement =>
    Iterator(statement) ++ (statement match {
      case R.If(_, positive, negative) => statementsIn(positive) ++ negative.iterator.flatMap(statementsIn)
      case R.While(_, loop) => statementsIn(loop)
      case R.For(init, _, update, loop) => statementsIn(init) ++ statementsIn(update) ++ statementsIn(loop)
      case _ => Iterator.empty
    })
  }

  private def ownExpressions(statement: R.Statement): Iterator[R.Expression] = (statement match {
    case R.Declare(_, initial) => initial.toSeq
    case R.Assign(_, _, value) => Seq(value)
    case R.Evaluate(value) => Seq(value)
    case R.Call(_, args) => args
    case R.If(condition, _, _) => Seq(condition)
    case R.While(condition, _) => Seq(condition)
    case R.For(_, condition, _, _) => condition.toSeq
    case _ => Nil
  }).iterator.flatMap(expressionsIn)

  private def mentioned(body: R.Block): Set[R.VariableId] = statementsIn(body).flatMap { statement =>
    (statement match {
      case R.Declare(variable, _) => Iterator(variable.id)
      case R.Assign(variable, _, _) => Iterator(variable.id)
      case _ => Iterator.empty
    }) ++ ownExpressions(statement).collect { case R.Read(variable) => variable.id; case R.Update(variable, _, _) => variable.id }
  }.toSet

  private def written(body: R.Block): Set[R.VariableId] = statementsIn(body).flatMap { statement =>
    (statement match {
      case R.Declare(variable, _) => Iterator(variable.id)
      case R.Assign(variable, _, _) => Iterator(variable.id)
      case _ => Iterator.empty
    }) ++ ownExpressions(statement).collect { case R.Update(variable, _, _) => variable.id }
  }.toSet

  private def ungrouped(value: R.Expression): R.Expression = value match {
    case R.Group(inner) => ungrouped(inner)
    case other => other
  }

  /** `for (int i = 0; i < n; i++)` whose counter the body never uses and whose bound it never changes is Snap's
   * `repeat n`. SnapToJava emits exactly this loop for a repeat, so the block survives a round trip. */
  private def countingLoop(loop: R.For): Option[(R.Variable, R.Expression)] = loop match {
    case R.For(R.Block(Vector(R.Declare(counter, Some(R.IntLiteral(0))))), Some(condition), R.Block(Vector(update)), body) =>
      ungrouped(condition) match {
        case R.Binary(R.BinaryOperator.Less, R.Read(read), limit) if read.id == counter.id &&
            counter.valueType == R.ValueType.IntValue && limit.valueType == R.ValueType.IntValue =>
          val increments = update match {
            case R.Evaluate(R.Update(target, R.UpdateOperator.Increment, _)) => target.id == counter.id
            case R.Assign(target, R.AssignmentOperator.Add, R.IntLiteral(1)) => target.id == counter.id
            case _ => false
          }
          val limitVariables = expressionsIn(limit).collect { case R.Read(variable) => variable.id }.toSet
          val pure = !expressionsIn(limit).exists(_.isInstanceOf[R.Update])
          Option.when(increments && pure && !limitVariables(counter.id) && !mentioned(body)(counter.id) &&
            (limitVariables & written(body)).isEmpty)(counter -> limit)
        case _ => None
      }
    case _ => None
  }

  def toSnap(program: P.Program): Either[String, ProgrammingStateSnapXml] = attempt {
    val (fullMeta, names) = metadata(program)
    // Counters of loops shown as repeat do not exist in Snap; SnapToJava recreates them.
    val repeatCounters = program.bindings.source.methods.map { method =>
      method.name -> statementsIn(method.body).collect { case loop: R.For => countingLoop(loop) }.flatten.map(counter => names(counter._1.id)).toSet
    }.toMap
    val meta = fullMeta.copy(methods = fullMeta.methods.map(method =>
      method.copy(variables = method.variables.filterNot(variable => repeatCounters.getOrElse(method.name, Set.empty)(variable.name)))))
    def literal(value: String): String = s"<l>${SnapInputCodec.escapeXml(value)}</l>"
    def block(selector: String, inputs: String): String = s"<block s=\"$selector\">$inputs</block>"
    def boolean(flag: Boolean): String = block("reportBoolean", s"<l><bool>$flag</bool></l>")
    def variadic(selector: String, operands: String*): String = block(selector, "<list>" + operands.mkString + "</list>")
    def variable(target: R.Variable): String = s"<block var=\"${SnapInputCodec.escapeXml(names(target.id))}\"/>"
    // Only standard palette blocks. Java's int/double typing is restored from the method metadata and the
    // literal spelling (`2` is an int, `2.0` a double), so widening and typed operations need no own blocks.
    def expression(value: R.Expression): String = value match {
      case R.IntLiteral(number) => literal(number.toString)
      case R.DoubleLiteral(number) => literal(JavaTurtlePythonExport.doubleLiteral(number))
      case R.BooleanLiteral(flag) => boolean(flag)
      case R.Read(target) => variable(target)
      case R.Update(_, _, _) => reject("Snap has no block for ++ or -- inside an expression. Move the update into its own statement.")
      case R.Widen(inner) => expression(inner)
      case R.Group(inner) => expression(inner)
      case R.Unary(R.UnaryOperator.Not, inner) => block("reportNot", expression(inner))
      case R.Unary(R.UnaryOperator.Negate, inner) => block("reportMonadic", "<l><option>neg</option></l>" + expression(inner))
      case R.Unary(_, inner) => expression(inner)
      case R.Binary(operator, left, right) if arithmetic(operator) =>
        val selector = SnapControlFlow.OperatorToSnapReporter(BinaryTokens(operator)).selector
        if operator == R.BinaryOperator.Add || operator == R.BinaryOperator.Multiply then variadic(selector, expression(left), expression(right))
        else block(selector, expression(left) + expression(right))
      case R.Binary(operator, left, right) =>
        variadic(SnapControlFlow.OperatorToSnapReporter(BinaryTokens(operator)).selector, expression(left), expression(right))
      case R.ShortCircuit(operator, left, right) =>
        variadic(if operator == R.ShortCircuitOperator.And then "reportVariadicAnd" else "reportVariadicOr", expression(left), expression(right))
    }
    // Snap loops run *until* a condition: `while (!(c))` is `repeat until c`, any other `while (c)` is `until not c`.
    def until(condition: R.Expression): String = ungrouped(condition) match {
      case R.Unary(R.UnaryOperator.Not, inner) => expression(inner)
      case _ => block("reportNot", expression(condition))
    }
    def set(target: R.Variable, value: R.Expression): String = block("doSetVar", literal(names(target.id)) + expression(value))
    def change(target: R.Variable, value: String): String = block("doChangeVar", literal(names(target.id)) + value)
    def statements(body: R.Block, stop: String): String = body.statements.map {
      case R.Empty => ""
      case R.Return => block("doStopThis", s"<l><option>$stop</option></l>")
      case R.Declare(target, initial) => initial.fold("")(value => set(target, value))
      case R.Assign(target, R.AssignmentOperator.Add, value) => change(target, expression(value))
      case R.Assign(target, operator, value) => set(target, assignment(target, operator, value))
      case R.Evaluate(R.Update(target, operator, _)) => change(target, literal(if operator == R.UpdateOperator.Increment then "1" else "-1"))
      case R.Evaluate(_) => reject("This discarded expression cannot be represented as a Snap command.")
      case R.Call(R.CallTarget.Turtle(command), args) => block(command.snapSelector, args.map(expression).mkString)
      case R.Call(R.CallTarget.Helper(id), args) =>
        val method = meta.method(program.bindings.source.methods.find(_.id == id).get.name)
        ensure(method.name != meta.entry.name, "main() is the green-flag script and cannot be called as a block.")
        s"<custom-block s=\"${SnapInputCodec.escapeXml(method.callSpec)}\">${args.map(expression).mkString}</custom-block>"
      case R.If(condition, positive, negative) => block(if negative.isDefined then "doIfElse" else "doIf",
        expression(condition) + "<script>" + statements(positive, stop) + "</script>" +
          negative.fold("<list></list>")(other => "<script>" + statements(other, stop) + "</script>"))
      case loop: R.For if countingLoop(loop).isDefined =>
        block("doRepeat", expression(countingLoop(loop).get._2) + "<script>" + statements(loop.body, stop) + "</script>")
      case R.While(condition, loop) => block("doUntil", until(condition) + "<script>" + statements(loop, stop) + "</script>")
      case R.For(init, condition, update, loop) =>
        val updates = R.Block(update.statements.map {
          case R.Evaluate(R.Update(target, operator, _)) => R.Assign(target, if operator == R.UpdateOperator.Increment then R.AssignmentOperator.Add else R.AssignmentOperator.Subtract, R.IntLiteral(1))
          case other => other
        })
        statements(init, stop) + block("doUntil", condition.fold(block("reportNot", boolean(true)))(until) +
          "<script>" + statements(loop, stop) + (if completesNormally(loop) then statements(updates, stop) else "") + "</script>")
    }.mkString
    def body(method: R.Method): String = {
      val description = meta.method(method.name)
      val declarations = if description.locals.isEmpty then "" else block("doDeclareVariables",
        "<list>" + description.locals.map(local => literal(local.name)).mkString + "</list>")
      declarations + statements(method.body, if method.name == meta.entry.name then "this script" else "this block")
    }
    // main() is the green-flag script; every other method becomes a global custom command block.
    val (mains, helpers) = program.bindings.source.methods.partition(_.name == meta.entry.name)
    val definitions = helpers.map { method =>
      val description = meta.method(method.name)
      val inputs = description.parameters.map(variable => s"<input type=\"${if variable.kind == R.ValueType.BooleanValue then "%b" else "%n"}\"></input>").mkString
      s"<block-definition s=\"${SnapInputCodec.escapeXml(description.spec)}\" type=\"command\" category=\"variables\"><inputs>$inputs</inputs><script>${body(method)}</script></block-definition>"
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
    val start = "<scripts><script x=\"156\" y=\"66\"><block s=\"receiveGo\"></block>" + mains.map(body).mkString + "</script></scripts>"
    val xml = withDefinitions.substring(0, scriptsAt) + start + withDefinitions.substring(scriptsAt + scripts.outer.length)
    bounded(xml)
    val result = ProgrammingStateSnapXml(xml)
    SnapToJava.fromSnap(result).fold(reject, _ => result)
  }

}
