package it.evadid.workbook.elements.interactionElements.programming.state

import it.evadid.vm.parsing.generic.abstractions.GenericAST
import it.evadid.vm.parsing.java.clean.JavaParser
import it.evadid.vm.parsing.java.clean.model.JavaAST.{JavaAttributeAccess, JavaCallExpression, JavaClassDef, JavaFunctionCall, JavaMethodDef, JavaTarget, JavaUnparsableStatement}
import it.evadid.vm.parsing.java.turtle.{JavaTurtleInputLimits, JavaTurtleSource, JavaTurtleVmPrograms}
import it.evadid.workbook.elements.interactionElements.programming.state.snap.JavaTurtleEditingBridge

import scala.util.Try

private[programming] object JavaAnalysis {
  private var latest = Option.empty[Entry]

  def forSource(source: String): Entry =
    if source == null || source.length > JavaTurtleInputLimits.MaxSourceCharacters then new Entry(source)
    else synchronized {
      latest.filter(_.source == source).getOrElse {
        val entry = new Entry(source)
        latest = Some(entry)
        entry
      }
    }

  final class Entry(val source: String) {
    lazy val classification: Try[(Boolean, Boolean, Boolean)] = Try {
      JavaTurtleInputLimits.checkParserInput(source).fold(
        diagnostic => throw IllegalArgumentException(diagnostic.message), identity)
      val sourceForAst = JavaToBeExpressionParser.withoutEntityHints(source)
      JavaTurtleInputLimits.checkParserInput(sourceForAst).fold(
        diagnostic => throw IllegalArgumentException(diagnostic.message), identity)
      val parsed = JavaParser.parse(sourceForAst).fold(
        error => throw IllegalArgumentException("This Java source cannot be read as a complete program or fragment.", error),
        identity)
      var pending: List[GenericAST] = List(parsed)
      var hasClass = false
      var hasStaticMain = false
      var hasTurtleCalls = false
      while pending.nonEmpty do {
        val node = pending.head
        pending = pending.tail
        node match {
          case _: JavaUnparsableStatement =>
            throw IllegalArgumentException("This Java source contains unsupported or unfinished syntax.")
          case _: JavaClassDef => hasClass = true
          case method: JavaMethodDef if method.name == "main" && method.modifiers.contains("static") => hasStaticMain = true
          case JavaFunctionCall(JavaTarget(_, Seq("Turtle"), _), _) => hasTurtleCalls = true
          case JavaCallExpression(JavaAttributeAccess(JavaTarget("Turtle", locations, _), _), _) if locations.isEmpty => hasTurtleCalls = true
          case _ => ()
        }
        pending = node.getChildren().toList ::: pending
      }
      (hasClass, hasStaticMain, hasTurtleCalls)
    }

    lazy val program: Try[Either[JavaTurtleSource.Diagnostic, JavaTurtleVmPrograms.Program]] =
      Try(JavaTurtleVmPrograms.compile(source))
    private var pythonResult = Option.empty[Try[ProgrammingStatePythonString]]
    private var snapResult = Option.empty[Try[ProgrammingStateSnapXml]]

    def python(checked: => Either[JavaTurtleSource.Diagnostic, JavaTurtleVmPrograms.Program]): ProgrammingStatePythonString = synchronized {
      if pythonResult.isEmpty then
        pythonResult = Some(Try(checked.left.map(_.message).flatMap(JavaTurtleEditingBridge.toPython)
          .fold(message => throw IllegalArgumentException(message), identity)))
      pythonResult.get.get
    }

    def snap(checked: => Either[JavaTurtleSource.Diagnostic, JavaTurtleVmPrograms.Program]): ProgrammingStateSnapXml = synchronized {
      if snapResult.isEmpty then
        snapResult = Some(Try(checked.left.map(_.message).flatMap(JavaTurtleEditingBridge.toSnap)
          .fold(message => throw IllegalArgumentException(message), identity)))
      snapResult.get.get
    }
  }
}
