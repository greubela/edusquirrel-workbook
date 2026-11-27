package it.evadid.homepage.webElements.editor.code.JavaEditor

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.uml.{UmlClassDiagram, UmlEntityKey, UmlEntityKind}
import it.evadid.homepage.webElements.editor.code.JavaEditor.JavaEditorLabels.Key
import it.evadid.homepage.webElements.editor.code.fragment.FragmentCodeEditor.Scope
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.structure.JavaUmlDiagram
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateJavaStructured

/** What [[JavaComponentEditor]] shows: its editing mode and the entry selected in the class diagram.
  *
  * Both change together, only through the transitions below, as one immutable [[JavaEditorNavigation.Position]].
  * Choosing a class or member picks the matching mode; switching to a mode picks a matching entry; a function
  * renamed or rewritten in Function Mode stays selected; a draft that grew beyond its entry widens the mode.
  */
final class JavaEditorNavigation(state: Var[ProgrammingStateJavaStructured]) {
  import JavaEditorNavigation.*

  private val position = Var(Position.initial(state.now()))

  val mode: Signal[Mode] = position.signal.map(_.mode).distinct
  val selection: Signal[Option[UmlEntityKey]] = position.signal.map(_.selection).distinct

  def currentMode: Mode = position.now().mode

  /** A click in the diagram. */
  val choose: Observer[UmlEntityKey] = Observer(key => position.update(_.choose(key)))

  def switchTo(next: Mode): Unit = position.update(_.switchTo(next, JavaUmlDiagram(state.now()).editable))

  /** Shows an entry that was just added: its class, or its function when `functionCode` names one. */
  def reveal(className: BeEntityName, functionCode: Option[String]): Unit =
    position.update(_.reveal(JavaUmlDiagram(state.now()).editable, className, functionCode))

  /** Shows the whole draft of the editor of mode `from` once it holds several declarations. */
  def widen(from: Mode, scope: Scope): Unit = position.update(_.widen(from, scope, JavaUmlDiagram(state.now()).editable))

  /** Keeps the edited function selected while its source changes in Function Mode. */
  def follow(function: JavaFunctionEditor.Function, program: ProgrammingStateJavaStructured): Unit =
    position.update(_.follow(function, program))
}

object JavaEditorNavigation {
  enum Mode(val label: Key) {
    case Class extends Mode(Key.javaModeClass)
    case Function extends Mode(Key.javaModeFunction)
    case Full extends Mode(Key.javaModeFull)
  }

  def isFunction(key: UmlEntityKey): Boolean = key.member.exists(_.kind == UmlEntityKind.Function)

  final case class Position(mode: Mode, selection: Option[UmlEntityKey]) {
    def choose(key: UmlEntityKey): Position = Position(if isFunction(key) then Mode.Function else Mode.Class, Some(key))

    def switchTo(next: Mode, diagram: UmlClassDiagram): Position = next match {
      case Mode.Class if selection.isEmpty => Position(next, diagram.classes.headOption.map(_.key))
      case Mode.Function if !selection.exists(isFunction) =>
        val owner = selection.flatMap(key => diagram.classes.find(_.name == key.className))
        Position(next, owner.orElse(diagram.classes.headOption).flatMap(_.functions.headOption).map(_.key).orElse(selection))
      case _ => copy(mode = next)
    }

    def reveal(diagram: UmlClassDiagram, className: BeEntityName, functionCode: Option[String]): Position =
      Position(if functionCode.isDefined then Mode.Function else Mode.Class, diagram.key(className, functionCode))

    /** Class Mode shows the methods of one class, Full Mode any other combination; only the open editor widens. */
    def widen(from: Mode, scope: Scope, diagram: UmlClassDiagram): Position =
      if mode != from then this
      else scope match {
        case Scope.Owner if mode == Mode.Function =>
          Position(Mode.Class, selection.flatMap(key => diagram.classes.find(_.name == key.className)).map(_.key).orElse(selection))
        case _ => copy(mode = Mode.Full)
      }

    def follow(function: JavaFunctionEditor.Function, program: ProgrammingStateJavaStructured): Position =
      if mode != Mode.Function then this
      else {
        val code = program.definedClasses.get(function.className).filter(_.length >= function.method.span.end)
          .map(function.method.span.text)
        JavaUmlDiagram(program).key(function.className, code).fold(this)(key => copy(selection = Some(key)))
      }
  }

  object Position {
    /** The single entry point `main`, if there is one, else the full source. */
    def initial(state: ProgrammingStateJavaStructured): Position =
      JavaComponentEditor.entryFunction(state).flatMap { function =>
        JavaUmlDiagram(state).key(function.className,
          Some(state.definedClasses(function.className).substring(function.method.span.start, function.method.span.end)))
      }.fold(Position(Mode.Full, None))(key => Position(Mode.Function, Some(key)))
  }
}
