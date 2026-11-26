package it.evadid.vm.parsing.source

import it.evadid.vm.code.errors.{ProgrammingConversionException, ProgrammingConversionKind}
import it.evadid.vm.parsing.java.clean.JavaSourceMap
import it.evadid.vm.parsing.java.clean.model.JavaAST
import it.evadid.vm.parsing.java.clean.model.JavaAST.{JavaMethodDef, JavaProgram}

/** Step 2 for Java ([[SourceParsing]]): the located syntax tree of the step-1 `masked` source. Every node carries its
  * range in the original text; parentheses and `//` comments are kept as nodes.
  */
final case class JavaSyntax(structured: JavaSourceStructured, sourceMap: JavaSourceMap) {
  def program: JavaProgram = sourceMap.program

  /** Statements the grammar kept as unreadable text, with their ranges. */
  lazy val unreadable: Vector[JavaSourceMap.Node[JavaAST]] = {
    def visit(node: JavaSourceMap.Node[JavaAST]): Vector[JavaSourceMap.Node[JavaAST]] = node.value match {
      case _: JavaAST.JavaUnparsableStatement => Vector(node)
      case _ => node.children.flatMap(visit)
    }
    visit(sourceMap.root)
  }

  /** The program as a conversion reads it: refused at its first unreadable statement. */
  def readable: Either[ProgrammingConversionException, JavaSyntax] = unreadable.headOption.fold(Right(this))(node =>
    Left(ProgrammingConversionException.at(ProgrammingConversionKind.Unparsable, "This Java source contains unsupported or unfinished syntax.",
      structured.source, Some(node.range.start))))

  /** Each structured function with its method node: the node starting where the function's span starts. */
  lazy val functions: Vector[JavaFunctionSyntax] = {
    def methods(node: JavaSourceMap.Node[JavaAST]): Vector[JavaSourceMap.Node[JavaMethodDef]] = node.value match {
      case _: JavaMethodDef => Vector(node.asInstanceOf[JavaSourceMap.Node[JavaMethodDef]]) ++ node.children.flatMap(methods)
      case _ => node.children.flatMap(methods)
    }
    val nodes = methods(sourceMap.root)
    structured.programClasses.flatMap(_.functions).flatMap(function =>
      nodes.find(node => function.span.contains(node.range.start) && node.value.name == function.name)
        .map(JavaFunctionSyntax(function, _)))
  }
}

/** A structured function with its located method node. */
final case class JavaFunctionSyntax(structured: JavaFunctionStructured, node: JavaSourceMap.Node[JavaMethodDef])
