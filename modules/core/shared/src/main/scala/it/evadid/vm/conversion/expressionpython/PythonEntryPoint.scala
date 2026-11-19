package it.evadid.vm.conversion.expressionpython

import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.BeDefineFunction
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.usage.BeFunctionCall
import it.evadid.vm.conversion.printing.ModuleLayout
import it.evadid.vm.naming.BeEntityName

/** Python's entry point: the program's main sequence is printed as `def main():` followed by `main()`.
  *
  * [[ModuleLayout]] decides what stays at module level; variables assigned in `main` are declared
  * `global`, so the code behaves as module-level statements. Reading reverses this: a parameterless
  * `main` called once, by the last top-level statement, is the main sequence rather than a helper.
  */
object PythonEntryPoint {
  private val mainName = BeEntityName.fromCodeString("main")
  private def isMain(definition: BeDefineFunction): Boolean =
    definition.inputs.isEmpty &&
      definition.functionTypeInfo.displayName.universalInterpretation() == mainName.universalInterpretation()

  /** Inline a `def main(): …` + final `main()` entry point into the main sequence. */
  def inline(program: BeStartProgram): BeStartProgram = program.startSequence match {
    case Some(sequence) =>
      val body = sequence.body
      val entry = body.lastOption.collect { case call: BeFunctionCall if isMain(call.funcDef) => call.funcDef }
      val definitionIndex = entry.map(main => body.indexWhere {
        case definition: BeDefineFunction => definition == main
        case _ => false
      }).filter(_ >= 0)
      val calls = entry.map(main => sequence.subtree.count {
        case call: BeFunctionCall => call.funcDef == main
        case _ => false
      })
      (entry, definitionIndex, calls) match {
        case (Some(main), Some(index), Some(1)) =>
          val statements = main.body.body.filterNot {
            case pass: BeSequence => pass.body.isEmpty
            case _ => false
          }
          val rest = body.patch(index, Nil, 1).dropRight(1)
          BeStartProgram(Some(sequence.copy(body = rest ++ statements)))
        case _ => program
      }
    case None => program
  }

  /** Move the main sequence into `def main():`, called at the end of the module; see [[ModuleLayout]]. */
  def wrap(program: BeStartProgram): BeStartProgram = {
    val layout = ModuleLayout.of(program)
    // The Python printer declares the module variables that `main` assigns `global`.
    val entry = BeDefineFunction(Nil, None, BeSequence.optionalBody(layout.main.toList),
      BeDefineFunction.functionInfo(mainName))
    BeStartProgram(Some(BeSequence.optionalBody((layout.module :+ entry :+ BeFunctionCall.fromDefinition(entry, Map.empty)).toList)))
  }
}
