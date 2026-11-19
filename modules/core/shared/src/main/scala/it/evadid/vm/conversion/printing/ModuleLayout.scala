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
  * that follows them, and classes come first because structured sources hoist class definitions.
  */
object ModuleLayout {
  final case class Layout(module: Seq[BeExpression], main: Seq[BeExpression], mainVariables: Seq[BeDefineVariable])

  def of(program: BeStartProgram): Layout = {
    val body = program.startSequence.map(_.body).getOrElse(Nil)
    // Module items keep their preceding comments; `seen` holds names assigned so far, which stops
    // later literal assignments from being hoisted.
    final case class State(module: Vector[Vector[BeExpression]] = Vector.empty, main: Vector[BeExpression] = Vector.empty,
        comments: Vector[BeExpression] = Vector.empty, seen: Set[String] = Set.empty, declared: Set[String] = Set.empty) {
      def toModule(item: BeExpression, names: Set[String] = Set.empty): State =
        copy(module = module :+ (comments :+ item), comments = Vector.empty, seen = seen ++ names, declared = declared ++ names)
    }
    val state = body.foldLeft(State()) { (state, statement) => statement match {
      case comment: BeSingleLineComment => state.copy(comments = state.comments :+ comment)
      case _: BeDefineFunction | _: BeDefineClass => state.toModule(statement)
      case declaration: BeDefineVariable => state.toModule(declaration, Set(name(declaration)))
      case assignment @ BeAssignVariable(target, BeUseValue(_: BeDataValueLiteral, _)) if !state.seen(name(target)) =>
        state.toModule(assignment, Set(name(target)))
      case other => state.copy(main = state.main ++ state.comments :+ other, comments = Vector.empty,
        seen = state.seen ++ assigned(other).map(name))
    } }
    val mainStatements = state.main ++ state.comments
    val mainVariables = mainStatements.flatMap(assigned).distinctBy(name)
    val declarations = mainVariables.filterNot(variable => state.declared(name(variable))).map(_.copy(initValue = None))
    // Classes come first, as structured sources hoist class definitions.
    val (classes, others) = state.module.partition(_.lastOption.exists(_.isInstanceOf[BeDefineClass]))
    Layout(classes.flatten ++ declarations ++ others.flatten, mainStatements, mainVariables)
  }

  def name(variable: BeDefineVariable): String = variable.name.getNameIn(English, NamingStyle.SnakeCase)

  /** Variables assigned by a statement, in source order. */
  def assigned(statement: BeExpression): Seq[BeDefineVariable] = statement.subtree.collect {
    case BeAssignVariable(target, _) => target
  }
}
