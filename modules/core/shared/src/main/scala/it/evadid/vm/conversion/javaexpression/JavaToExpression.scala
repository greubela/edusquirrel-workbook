package it.evadid.vm.conversion.javaexpression

import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateBeExpression

import it.evadid.vm.conversion.ProgrammingStateConversions.*

import it.evadid.core.datastructures.language.AppLanguage.Java
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, ProgrammingConversionException, ProgrammingConversionLocation}
import it.evadid.vm.code.tree.BeExpressionReference
import it.evadid.vm.parsing.generic.abstractions.GenericAST
import it.evadid.vm.parsing.java.clean.JavaParser
import it.evadid.vm.parsing.java.clean.model.JavaAST.{JavaAttributeAccess, JavaCallExpression, JavaClassDef, JavaFunctionCall, JavaMethodDef, JavaTarget, JavaUnparsableStatement}
import it.evadid.vm.conversion.javaexpression.checked.{JavaTurtleInputLimits, JavaTurtleSource, JavaTurtleVmPrograms}
import it.evadid.vm.types.{BeChildInfo, BeChildRole, BeScope}
import it.evadid.workbook.elements.interactionElements.programming.*
import upickle.default.*

import it.evadid.vm.conversion.ConversionResult.*

final class JavaToExpression(code: String) {
  private lazy val classDetails: (Boolean, Boolean, Boolean) = {
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
    var hasStaticMain = false
    var hasTurtleCalls = false
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
        case method: JavaMethodDef if method.name == "main" && method.modifiers.contains("static") => hasStaticMain = true
        case JavaFunctionCall(JavaTarget(_, Seq("Turtle"), _), _) => hasTurtleCalls = true
        case JavaCallExpression(JavaAttributeAccess(JavaTarget("Turtle", locations, _), _), _) if locations.isEmpty => hasTurtleCalls = true
        case _ => ()
      }
      pending = node.getChildren().toList ::: pending
    }
    (hasClass, hasStaticMain, hasTurtleCalls)
  }

  def isClassProgram: Boolean = classDetails._1

  private def legacyPrinterExpression(): ProgrammingStateBeExpression = {
    val expression = JavaToBeExpressionParser.parse(code)
    val nodes = expression.recToTree(false, BeChildInfo(BeChildRole.NoRole, BeScope.GlobalScope())).values
    val unsupported = nodes.exists {
      case BeExpressionReference(_, _: BeExpressionUnsupported) => true
      case BeExpressionReference(_, _: BeExpressionUnparsable) => true
      case _ => false
    }
    if unsupported then throw IllegalArgumentException("This legacy Java class cannot be converted completely.")
    ProgrammingStateBeExpression(expression)
  }

  def toLegacyTurtleCommands: List[TurtleCommand[Double]] = {
    if isClassProgram then throw IllegalArgumentException("Full Java classes require the checked Java runner.")
    convert.deriveTurtleCommands
  }

  def toJavaVmProgram: Either[JavaTurtleSource.Diagnostic, JavaTurtleVmPrograms.Program] =
    JavaTurtleVmPrograms.compile(code)

  def convert: ProgrammingStateBeExpression =
    if !isClassProgram then ProgrammingStateBeExpression(JavaToBeExpressionParser.parse(code))
    else toJavaVmProgram match {
      case Right(program) => ProgrammingStateBeExpression(program.root)
      case Left(_) if JavaToBeExpressionParser.withoutEntityHints(code) != code && !classDetails._2 && !classDetails._3 =>
        legacyPrinterExpression()
      case Left(diagnostic) => rejectAt(diagnostic.message, code, diagnostic.range.map(_.start))
    }

}
