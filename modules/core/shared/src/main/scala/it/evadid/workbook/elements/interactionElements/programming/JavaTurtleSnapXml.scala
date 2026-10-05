package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R, JavaTurtleVmBindings as V, JavaTurtleVmExpressions as X, JavaTurtleVmPrograms as P}
import it.evadid.vm.simulation.java.JavaTurtleRuntime

object JavaTurtleSnapXml {
  enum Problem {
    case UnsupportedStatement, UnsupportedExpression, UnsupportedParameter, ExecutionLimit, InvalidTemplate
  }

  case class Diagnostic(problem: Problem, message: String)

  private type Result[A] = Either[Diagnostic, A]

  private val drawingSetup =
    """<block s="clear"></block><block s="up"></block><block s="gotoXY"><l>0</l><l>0</l></block><block s="setHeading"><l>90</l></block><block s="down"></block>"""

  def render(program: P.Program): Result[ProgrammingExerciseState] = {
    val helpers = program.root.methods.filterNot(_ eq program.root.entryPoint)
    for {
      _ <- traverse(helpers) { method =>
        if method.binding.parameters.forall(_.variable.valueType == R.ValueType.IntValue) then Right(())
        else reject(Problem.UnsupportedParameter, s"${method.binding.originalName}: Snap export currently supports only int parameters.")
      }
      definitions <- traverse(helpers) { method =>
        renderBody(method).map { body =>
          val inputs = method.binding.parameters.map(_ => "<input type=\"%n\"></input>").mkString
          val script = if body.nonEmpty then s"<script>$body</script>" else ""
          s"""<block-definition s="${escape(definitionSpec(method))}" type="command" category="variables"><header></header><code></code><translations></translations><inputs>$inputs</inputs>$script</block-definition>"""
        }
      }
      main <- renderBody(program.root.entryPoint)
      _ <- checkExecution(program)
      withDefinitions <- replaceInner(SnapProjectXml.empty, List("project", "scenes", "scene", "blocks"), definitions.mkString)
      xml <- replaceInner(withDefinitions, List("project", "scenes", "scene", "stage", "sprites", "sprite", "scripts"),
        s"""<script x="156" y="66"><block s="receiveGo"></block>$drawingSetup$main</script>""")
    } yield ProgrammingExerciseState(xml)
  }

  private def escape(value: String): String = SnapInputCodec.escapeXml(value)

  private def reject[A](problem: Problem, message: String): Result[A] = Left(Diagnostic(problem, message))

  private def traverse[A, B](values: Vector[A])(render: A => Result[B]): Result[Vector[B]] =
    values.foldLeft[Result[Vector[B]]](Right(Vector.empty)) { (previous, value) =>
      for { result <- previous; next <- render(value) } yield result :+ next
    }

  private def definitionSpec(method: P.Method): String =
    method.binding.originalName + method.binding.parameters.map(parameter => s" %${parameter.variable.name}").mkString

  private def callSpec(method: V.MethodBinding): String =
    SnapCustomBlockRules.blockSpec(
      method.originalName + method.parameters.map(parameter => s" %${parameter.variable.name}").mkString,
      _ => "%n"
    )

  private def renderBody(method: P.Method): Result[String] =
    traverse(method.body.statements) { statement => statement.node match {
      case P.Node.Empty => Right("")
      case P.Node.Call(target, arguments) =>
        traverse(arguments)(value => renderValue(value.expression, method)).map { values =>
          target match {
            case P.CallTarget.Turtle(command) =>
              val selector = command match {
                case R.TurtleCommand.Forward => "forward"
                case R.TurtleCommand.TurnRight => "turn"
              }
              s"""<block s="$selector">${values.mkString}</block>"""
            case P.CallTarget.Helper(binding) =>
              s"""<custom-block s="${escape(callSpec(binding))}">${values.mkString}</custom-block>"""
          }
        }
      case _ => reject(Problem.UnsupportedStatement,
        s"${method.binding.originalName}: Snap export currently supports only turtle and helper calls.")
    } }.map(_.mkString)

  private def renderValue(expression: X.Expression, method: P.Method): Result[String] = expression.node match {
    case X.Node.IntLiteral(value) => Right(s"<l>$value</l>")
    case X.Node.Group(inner) => renderValue(inner, method)
    case X.Node.Read(variable, _) if variable.valueType == R.ValueType.IntValue &&
        method.binding.parameters.exists(_.variable == variable) =>
      Right(s"""<block var="${escape(variable.name)}"/>""")
    case _ => reject(Problem.UnsupportedExpression,
      s"${method.binding.originalName}: Snap export currently supports only integer literals and parameter values.")
  }

  private def checkExecution(program: P.Program): Result[Unit] =
    if JavaTurtleRuntime.runVm(program).status == JavaTurtleRuntime.Status.Completed then Right(())
    else reject(Problem.ExecutionLimit, "This program exceeds the Java execution limits and cannot be exported to Snap.")

  private def replaceInner(xml: String, path: List[String], value: String): Result[String] = path match {
    case Nil => Right(value)
    case tag :: rest =>
      val candidates = SnapXmlParser.children(xml).filter(_.tag == tag)
      if candidates.size != 1 then reject(Problem.InvalidTemplate, s"The empty Snap project needs exactly one $tag element.")
      else {
        val element = candidates.head
        replaceInner(element.inner, rest, value).map { inner =>
          val headerEnd = element.outer.indexOf('>') + 1
          val replaced = element.outer.take(headerEnd) + inner + element.outer.drop(headerEnd + element.inner.length)
          xml.take(element.start) + replaced + xml.drop(element.end)
        }
      }
  }
}
