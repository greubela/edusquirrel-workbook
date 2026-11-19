package it.evadid.vm.conversion.javaexpression

import it.evadid.vm.compilation.java.turtle.JavaTurtleCompiler

import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateBeExpression


import it.evadid.core.datastructures.language.AppLanguage.Java
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, ProgrammingConversionException, ProgrammingConversionLocation}
import it.evadid.vm.code.tree.BeExpressionReference
import it.evadid.vm.parsing.generic.abstractions.GenericAST
import it.evadid.vm.parsing.java.clean.JavaParser
import it.evadid.vm.parsing.java.clean.model.JavaAST.{JavaAttributeAccess, JavaCallExpression, JavaClassDef, JavaFunctionCall, JavaMethodDef, JavaTarget, JavaUnparsableStatement}
import it.evadid.vm.compilation.java.turtle.{JavaTurtleInputLimits, JavaTurtleSourceParser, JavaTurtleProgram}
import it.evadid.vm.types.{BeChildInfo, BeChildRole, BeScope}
import it.evadid.workbook.elements.interactionElements.programming.*
import upickle.default.*
import java.util.regex.Pattern

import it.evadid.vm.conversion.ConversionResult.*

final class JavaToExpression(code: String) {
  private lazy val isClass: Boolean = {
    JavaTurtleInputLimits.checkParserInput(code).fold(
      diagnostic => rejectAt(diagnostic.message, code, diagnostic.range.map(_.start)), identity)
    val sourceForAst = JavaToBeExpressionParser.withoutEntityHints(code)
    JavaTurtleInputLimits.checkParserInput(sourceForAst).fold(
      diagnostic => rejectAt(diagnostic.message, sourceForAst, diagnostic.range.map(_.start)), identity)
    val parsed = JavaParser.parse(sourceForAst).fold(
      {
        case conversion: ProgrammingConversionException =>
          throw ProgrammingConversionException.unparsable(
            "This Java source cannot be read as a complete program or fragment.",
            conversion.error.line, conversion.error.column)
        case error =>
          throw IllegalArgumentException("This Java source cannot be read as a complete program or fragment.", error)
      },
      identity)
    var pending: List[GenericAST] = List(parsed)
    var hasClass = false
    while pending.nonEmpty do {
      val node = pending.head
      pending = pending.tail
      node match {
        case statement: JavaUnparsableStatement =>
          val line = {
            val index = code.indexOf(statement.source)
            if index >= 0 then Some(ProgrammingConversionLocation.lineColumn(code, index)._1) else None
          }
          throw ProgrammingConversionException.unparsable(
            "This Java source contains unsupported or unfinished syntax.", line)
        case _: JavaClassDef => hasClass = true
        case _ => ()
      }
      pending = node.getChildren().toList ::: pending
    }
    hasClass
  }

  def isClassProgram: Boolean = isClass

  private def parsedExpression(source: String): ProgrammingStateBeExpression = {
    val expression = JavaToBeExpressionParser.parse(source)
    val nodes = expression.recToTree(false, BeChildInfo(BeChildRole.NoRole, BeScope.GlobalScope())).values
    val unsupported = nodes.exists {
      case BeExpressionReference(_, _: BeExpressionUnsupported) => true
      case BeExpressionReference(_, _: BeExpressionUnparsable) => true
      case _ => false
    }
    if unsupported then {
      val reason = nodes.collectFirst {
        case BeExpressionReference(_, BeExpressionUnsupported(source, _)) => source
        case BeExpressionReference(_, BeExpressionUnparsable(source, _, _)) => source
      }.getOrElse(source).take(160)
      throw ProgrammingConversionException.unsupported(s"This Java construct is not supported by the shared expression tree: $reason")
    }
    ProgrammingStateBeExpression(expression)
  }

  def toLegacyTurtleCommands: List[TurtleCommand[Double]] = {
    if isClassProgram then throw IllegalArgumentException("Full Java classes require the checked Java runner.")
    convert.turtleCommandExecutionInfo.completedCommands
  }

  def compileForExecution: Either[JavaTurtleSourceParser.Diagnostic, JavaTurtleProgram] =
    JavaTurtleCompiler.compile(code)

  def convert: ProgrammingStateBeExpression = {
    val _ = isClassProgram // Validate limits, syntax and incomplete input before lowering.
    parsedExpression(conversionSource)
  }

  lazy val preparedSource = it.evadid.vm.conversion.structured.PreparedProgramSource(code, Java)

  /** A static entry-point class is a module plus startup, not a language-specific VM node. */
  private def conversionSource: String = {
    import it.evadid.vm.conversion.structured.SourceStructure.*
    val source = JavaToBeExpressionParser.withoutEntityHints(preparedSource.code)
    val classes = javaClasses(source)
    val main = "\\bstatic\\s+void\\s+main\\s*\\(\\s*(?:String\\s*\\[\\]\\s+([A-Za-z_$][\\w$]*)\\s*)?\\)".r
    val entry = classes.find(block => javaMembers(block.source).exists(main.findFirstIn(_).nonEmpty))
    def declarationNames(declarations: Seq[String]): Set[String] = declarations.flatMap { declaration =>
      "^(?:(?:public|private|protected|static|final)\\s+)*[\\w$<>\\[\\]]+\\s+([\\w$]+)".r.findFirstMatchIn(declaration).map(_.group(1))
    }.toSet
    def unqualify(source: String, owner: String, names: Set[String]): String = {
      val qualified = ("\\b" + Pattern.quote(owner) + "\\s*\\.\\s*([A-Za-z_$][\\w$]*)").r
        .findAllMatchIn(mask(source, false)).filter(found => names(found.group(1))).toVector
      remove(source, qualified.map(found => found.start -> found.start(1)))
    }
    entry match {
      case None if !classes.exists(_.name == "Main") =>
        val loose = remove(source, classes.map(block => block.start -> block.end))
        unqualify(source, "Main", declarationNames(javaMembers(s"class Main { $loose }")))
      case None => source
      case Some(block) =>
        val members = javaMembers(block.source)
        if members.exists(scope(_) == MemberScope.Static) && members.exists(scope(_) == MemberScope.Instance) then
          throw ProgrammingConversionException.unsupported("Mixed static and instance members are not supported by the shared expression tree.")
        val candidates = members.filter(main.findFirstIn(_).nonEmpty)
        val entryMember = candidates.find(member => main.findFirstMatchIn(member).exists(_.group(1) != null)).getOrElse(candidates.head)
        main.findFirstMatchIn(entryMember).foreach { entry =>
          Option(entry.group(1)).foreach { name =>
            val body = mask(entryMember, false).drop(entry.end)
            if ("\\b" + Pattern.quote(name) + "\\b").r.findFirstIn(body).nonEmpty then
              throw ProgrammingConversionException.unsupported("Program arguments cannot be represented by the shared startup expression.")
          }
        }
        val cleanEntry = mask(entryMember, false)
        val startup = entryMember.substring(cleanEntry.indexOf('{') + 1, cleanEntry.lastIndexOf('}'))
        val declarations = members.filterNot(_ == entryMember)
        // Calls to module members use Main's namespace in Java, and the module namespace in the tree.
        val combined = remove(source, Seq(block.start -> block.end)) + "\n" + declarations.mkString("\n") + "\n" + startup
        unqualify(combined, block.name, declarationNames(declarations))
    }
  }

}
