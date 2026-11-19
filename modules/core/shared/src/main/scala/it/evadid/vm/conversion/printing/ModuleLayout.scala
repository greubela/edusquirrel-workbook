package it.evadid.vm.conversion.printing

import it.evadid.core.datastructures.language.AppLanguage.English
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.errors.BeSingleLineComment
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.tree.BeExpressionReference
import it.evadid.vm.code.usage.{BeAssignVariable, BeUseValue}
import it.evadid.vm.naming.NamingStyle
import it.evadid.vm.types.{BeDataValueLiteral, BeScope}

/** Where printers place the parts of a program around its entry point.
  *
  * Python prints a called `def main()` and Java a `Main` class with a static `main`; both use this
  * split so they agree. Classes, functions and module variables whose first assignment is a literal
  * stay at module level (Python) or become static members (Java). All other statements run, in
  * order, in `main`. Each module variable they assign is declared with its type at module level
  * (`x: float`, `static double x;`) unless the module already declares it, so both languages keep
  * their type hints; Python's `main` then declares those names `global`. Comments stay with the item
  * that follows them.
  */
object ModuleLayout {
  final case class Layout(module: Seq[BeExpression], main: Seq[BeExpression], mainVariables: Seq[BeDefineVariable])

  def of(program: BeStartProgram): Layout = {
    val body = program.startSequence.map(_.body).getOrElse(Nil)
    val (module, main, pending, declared) = body.foldLeft((Vector.empty[BeExpression], Vector.empty[BeExpression], Vector.empty[BeExpression], Set.empty[String])) {
      case ((module, main, comments, names), statement) => statement match {
        case comment: BeSingleLineComment => (module, main, comments :+ comment, names)
        case _: BeDefineFunction | _: BeDefineClass => (module ++ comments :+ statement, main, Vector.empty, names)
        case declaration: BeDefineVariable => (module ++ comments :+ declaration, main, Vector.empty, names + name(declaration))
        case assignment @ BeAssignVariable(target, BeUseValue(_: BeDataValueLiteral, _)) if !names(name(target)) =>
          (module ++ comments :+ assignment, main, Vector.empty, names + name(target))
        case other => (module, main ++ comments :+ other, Vector.empty, names ++ assigned(other).map(name))
      }
    }
    val mainStatements = main ++ pending
    val mainVariables = mainStatements.flatMap(assigned).distinctBy(name)
    val declarations = mainVariables.filterNot(variable => declared(name(variable))).map(_.copy(initValue = None))
    Layout(declarations ++ module, mainStatements, mainVariables)
  }

  def name(variable: BeDefineVariable): String = variable.name.getNameIn(English, NamingStyle.SnakeCase)

  /** Variables assigned by a statement, in source order. */
  def assigned(statement: BeExpression): Seq[BeDefineVariable] = nodes(statement).collect {
    case BeAssignVariable(target, _) => target
  }

  /** The statement and its descendants, depth first in source order. */
  def nodes(expression: BeExpression): Vector[BeExpression] =
    expression +: expression.structureInfo.getChildren(false, BeScope.GlobalScope()).toVector.flatMap {
      case BeExpressionReference(_, child) => nodes(child)
      case _ => Vector.empty
    }
}
