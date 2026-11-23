package it.evadid.homepage.webElements.editor.code.EvaEditor.uml

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.EvaEditor.uml.UmlClassDiagram.{EntityKey, UmlClass, UmlMember}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateJavaStructured
import upickle.default.ReadWriter

object UmlStructureSelector {
  sealed trait SelectedUmlEntity derives ReadWriter {
    def name: String
    def fullCode: String
  }
  /** `code` is the class declaration without its preamble of imports. */
  case class SelectedClass(name: String, code: String) extends SelectedUmlEntity derives ReadWriter {
    override def fullCode: String = code
  }
  /** `code` is the full method or constructor declaration, including its body. */
  case class SelectedFunction(name: String, code: String) extends SelectedUmlEntity derives ReadWriter {
    override def fullCode: String = code
  }
  /** `code` is the field declaration, including its initializer. */
  case class SelectedAttribute(name: String, code: String) extends SelectedUmlEntity derives ReadWriter {
    override def fullCode: String = code
  }
}

/** The classes of a Java state as clickable UML class boxes: name, attributes and functions.
  * A click selects an entry; the selection keeps following that entry's current source while the state changes.
  */
case class UmlStructureSelector(underlyingState: Signal[ProgrammingStateJavaStructured]) extends HtmlAppElement {
  import UmlStructureSelector.SelectedUmlEntity

  private val diagram: Signal[UmlClassDiagram] = underlyingState.map(UmlClassDiagram(_))
  private val selectedKey: Var[Option[EntityKey]] = Var(None)

  /** The entry last clicked, or `None` before the first click and after the entry is removed from the source. */
  val selectedEntitySignal: Signal[Option[SelectedUmlEntity]] =
    diagram.combineWith(selectedKey.signal).map((classes, key) => key.flatMap(classes.entity)).distinct

  /** Which entry is selected; unlike [[selectedEntitySignal]], it does not change while the source is edited. */
  val selectedKeySignal: Signal[Option[EntityKey]] = selectedKey.signal

  /** Selects an entry as a click on it would, e.g. a function that was just added. */
  def select(key: Option[EntityKey]): Unit = selectedKey.set(key)

  override def getDomElement(): Element = div(
    cls := "uml-structure-selector",
    children <-- diagram.map(_.classes).split(_.name)((_, _, umlClass) => renderClass(umlClass)))

  private def renderClass(umlClass: Signal[UmlClass]): Element = sectionTag(
    cls := "uml-class",
    div(cls := "uml-class__header",
      entry(umlClass.map(_.key), umlClass.map(_.name), cls("uml-entry--abstract") <-- umlClass.map(_.isAbstract))),
    compartment("uml-class__attributes", umlClass.map(_.attributes)),
    compartment("uml-class__functions", umlClass.map(_.functions)))

  private def compartment(className: String, members: Signal[Seq[UmlMember]]): Element = ul(
    cls := s"uml-class__compartment $className",
    children <-- members.split(_.key)((_, _, member) => li(entry(member.map(_.key), member.map(_.label),
      cls("uml-entry--static") <-- member.map(_.isStatic),
      cls("uml-entry--abstract") <-- member.map(_.isAbstract)))))

  private def entry(key: Signal[EntityKey], label: Signal[String], modifiers: Modifier[Button]*): Button = {
    val selected = key.combineWith(selectedKey.signal).map((entryKey, chosen) => chosen.contains(entryKey))
    button(tpe := "button", cls := "uml-entry",
      cls("uml-entry--selected") <-- selected,
      aria.pressed <-- selected.map(_.toString),
      child.text <-- label,
      modifiers,
      onClick.compose(_.sample(key)) --> (clicked => selectedKey.set(Some(clicked))))
  }
}
