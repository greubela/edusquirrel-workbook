package it.evadid.vm.parsing.source

import it.evadid.vm.parsing.python.clean.model.PyAST.PyProgram

/** Step 2 for Python ([[SourceParsing]]): the syntax tree of the step-1 `masked` source. Top-level statements carry
  * their line numbers; nested ones are not located yet.
  */
final case class PythonSyntax(structured: PythonSourceStructured, program: PyProgram)
