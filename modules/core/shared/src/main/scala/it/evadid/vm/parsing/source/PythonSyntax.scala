package it.evadid.vm.parsing.source

import it.evadid.vm.parsing.python.clean.model.PyAST.PyProgram

/** Step 2 for Python ([[SourceParsing]]): the syntax tree of the step-1 `masked` source. Node ranges refer to the original
  * source, including before indentation and newline normalization.
  */
final case class PythonSyntax(structured: PythonSourceStructured, sourceMap: it.evadid.vm.parsing.python.clean.PythonSourceMap) {
  def program: PyProgram = sourceMap.program

  lazy val functionOrigins: Map[SourceSpan, SourceSpan] = {
    import it.evadid.vm.parsing.python.clean.model.PyAST
    def functions(node: PyAST): Seq[PyAST.PyFunctionDef] = {
      val current = node match { case function: PyAST.PyFunctionDef => Seq(function); case _ => Nil }
      current ++ node.getChildren().collect { case child: PyAST => child }.flatMap(functions)
    }
    val nodes = functions(program)
    structured.functions.flatMap(function => nodes.find(node => node.name == function.name &&
      sourceMap.rangeOf(node).exists(span => function.span.contains(span.start)))
        .flatMap(sourceMap.rangeOf).map(function.span -> _)).toMap
  }
}
