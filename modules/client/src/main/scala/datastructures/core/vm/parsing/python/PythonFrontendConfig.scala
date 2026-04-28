package datastructures.core.vm.parsing.python

import PythonSymbolTable.KnownStructure

final case class PythonFrontendConfig(
                                       defaultIndent: Int,
                                       defaultKnownStructures: Seq[KnownStructure]
                                     )

object PythonFrontendConfig {
  val default: PythonFrontendConfig =
    PythonFrontendConfig(
      defaultIndent = 4,
      defaultKnownStructures = PythonSymbolTable.defaultKnownStructures
    )
}
