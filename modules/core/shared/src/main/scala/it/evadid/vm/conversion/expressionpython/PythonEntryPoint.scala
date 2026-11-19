package it.evadid.vm.conversion.expressionpython

import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.core.datastructures.language.AppLanguage.English
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction}
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.tree.BeExpressionReference
import it.evadid.vm.code.usage.{BeAssignVariable, BeFunctionCall, BeUseValue}
import it.evadid.vm.naming.{BeEntityName, NamingStyle}
import it.evadid.vm.types.{BeChildInfo, BeChildRole, BeDataValueLiteral, BeScope}

/** Python's entry point: the program's main sequence is printed as `def main():` followed by `main()`.
  *
  * Printing keeps classes, functions and literal-initialised module variables at module level and
  * moves the remaining statements, in order, into `main`; variables assigned there are declared
  * `global`, so the code behaves as module-level statements. Reading reverses this: a parameterless
  * `main` called once, by the last top-level statement, is the main sequence rather than a helper.
  */
object PythonEntryPoint {
  private val mainName = BeEntityName.fromCodeString("main")
  private def isMain(definition: BeDefineFunction): Boolean =
    definition.inputs.isEmpty &&
      definition.functionTypeInfo.displayName.universalInterpretation() == mainName.universalInterpretation()
  private def isGlobalDeclaration(statement: BeExpression): Boolean = statement match {
    case BeExpressionUnsupported(source, _) => source.trim.startsWith("global ")
    case BeExpressionUnparsable(source, _, _) => source.trim.startsWith("global ")
    case _ => false
  }
  private def nodes(expression: BeExpression): Iterable[BeExpression] =
    expression.recToTree(false, BeChildInfo(BeChildRole.NoRole, BeScope.GlobalScope())).values.collect {
      case BeExpressionReference(_, node) => node
    }
  private def pythonName(name: BeEntityName): String = name.getNameIn(English, NamingStyle.SnakeCase)

  /** Annotated assignments cannot target names declared `global`; inside `main` they are printed plainly. */
  def withoutGlobalAnnotations(source: String): String = {
    val lines = source.linesIterator.toVector
    val start = lines.lastIndexWhere(_.startsWith("def main("))
    val globals = lines.lift(start + 1).map(_.trim).filter(_.startsWith("global ")).toSeq
      .flatMap(_.stripPrefix("global ").split(",").map(_.trim)).toSet
    if start < 0 || globals.isEmpty then source
    else {
      val annotated = ("^(\\s+)(" + globals.map(java.util.regex.Pattern.quote).mkString("|") + ")\\s*:[^=]*=").r
      val end = lines.indexWhere(line => line.trim.nonEmpty && !line.startsWith(" ") && !line.startsWith("\t"), start + 1) match {
        case -1 => lines.length
        case index => index
      }
      lines.zipWithIndex.map { (line, index) =>
        if index > start && index < end then annotated.replaceFirstIn(line, "$1$2 =") else line
      }.mkString("\n") + (if source.endsWith("\n") then "\n" else "")
    }
  }

  /** Inline a `def main(): …` + final `main()` entry point into the main sequence. */
  def inline(program: BeStartProgram): BeStartProgram = program.startSequence match {
    case Some(sequence) =>
      val body = sequence.body
      val entry = body.lastOption.collect { case call: BeFunctionCall if isMain(call.funcDef) => call.funcDef }
      val definitionIndex = entry.map(main => body.indexWhere {
        case definition: BeDefineFunction => definition == main
        case _ => false
      }).filter(_ >= 0)
      val calls = entry.map(main => nodes(sequence).count {
        case call: BeFunctionCall => call.funcDef == main
        case _ => false
      })
      (entry, definitionIndex, calls) match {
        case (Some(main), Some(index), Some(1)) =>
          val statements = main.body.body.filterNot(statement => isGlobalDeclaration(statement) || (statement match {
            case pass: BeSequence => pass.body.isEmpty
            case _ => false
          }))
          val rest = body.patch(index, Nil, 1).dropRight(1)
          BeStartProgram(Some(sequence.copy(body = rest ++ statements)))
        case _ => program
      }
    case None => program
  }

  /** Move the main sequence into `def main():`, called at the end of the module. */
  def wrap(program: BeStartProgram): BeStartProgram = {
    val body = program.startSequence.map(_.body).getOrElse(Nil)
    val hoisted = body.foldLeft((Vector.empty[BeExpression], Vector.empty[BeExpression], Set.empty[String])) {
      case ((module, main, names), statement) => statement match {
        case _: BeDefineFunction | _: BeDefineClass => (module :+ statement, main, names)
        case assignment @ BeAssignVariable(target, BeUseValue(_: BeDataValueLiteral, _))
            if !names.contains(pythonName(target.name)) =>
          (module :+ assignment, main, names + pythonName(target.name))
        case other =>
          val assigned = nodes(other).collect { case BeAssignVariable(target, _) => pythonName(target.name) }
          (module, main :+ other, names ++ assigned)
      }
    }
    val (module, main, _) = hoisted
    val globals = main.flatMap(statement => nodes(statement).collect { case BeAssignVariable(target, _) => pythonName(target.name) }).distinct
    val declaration = Option.when(globals.nonEmpty)(BeExpressionUnsupported(s"global ${globals.mkString(", ")}", Some(Python)))
    val entry = BeDefineFunction(Nil, None, BeSequence.optionalBody((declaration.toList ++ main).toList),
      BeDefineFunction.functionInfo(mainName))
    BeStartProgram(Some(BeSequence.optionalBody((module :+ entry :+ BeFunctionCall.fromDefinition(entry, Map.empty)).toList)))
  }
}
