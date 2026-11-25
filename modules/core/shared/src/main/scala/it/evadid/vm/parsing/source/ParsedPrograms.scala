package it.evadid.vm.parsing.source

import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.defining.BeDefineFunction
import it.evadid.vm.conversion.parsed.ParsedProgram
import it.evadid.vm.conversion.pythonexpression.PythonToExpression
import it.evadid.vm.parsing.java.clean.JavaSourceMap
import it.evadid.vm.parsing.java.clean.model.JavaAST.JavaMethodDef
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateBeExpression

/** Step 3 for Java ([[SourceParsing]]): the language-neutral program ([[ParsedProgram]]), the shared expression tree
  * built from it, and each structured function with its syntax node and the definition the expression tree holds for it.
  */
final case class JavaProgramParsed(syntax: JavaSyntax, neutral: ParsedProgram, expression: ProgrammingStateBeExpression,
    functions: Vector[JavaFunctionParsed]) {
  def structured: JavaSourceStructured = syntax.structured
}

final case class JavaFunctionParsed(structured: JavaFunctionStructured, syntax: JavaSourceMap.Node[JavaMethodDef],
    definition: Option[BeDefineFunction])

/** Step 3 for Python ([[SourceParsing]]): the program, the bound definitions and each structured function with its
  * definition. An unreadable source (`syntax` failed) is a program of one unparsable statement.
  */
final case class PythonProgramParsed(structured: PythonSourceStructured, syntax: Either[SyntaxFailure, PythonSyntax], neutral: ParsedProgram,
    program: BeProgram, details: PythonToExpression.Details, functions: Vector[PythonFunctionParsed])

final case class PythonFunctionParsed(structured: PythonFunctionStructured, definition: Option[BeDefineFunction])
