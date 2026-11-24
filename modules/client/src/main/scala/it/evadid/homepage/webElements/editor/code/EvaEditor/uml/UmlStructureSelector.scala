package it.evadid.homepage.webElements.editor.code.EvaEditor.uml

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.core.datastructures.uml.*

/** A language-independent UML model rendered as clickable class boxes: name, attributes and functions.
  * A click selects an entry; the selection keeps following that entry's current source while the state changes.
  */
case class UmlStructureSelector(diagram: Signal[UmlClassDiagram],
    classFooter: UmlClass => Modifier[HtmlElement] = _ => emptyMod,
    trailing: Modifier[HtmlElement] = emptyMod) extends HtmlAppElement {
  import it.evadid.core.datastructures.uml.UmlSelectedEntity

  private val selection: Var[Option[UmlEntityKey]] = Var(None)

  /** The entry last clicked, or `None` before the first click and after the entry is removed from the source. */
  val selectedEntitySignal: Signal[Option[UmlSelectedEntity]] =
    diagram.combineWith(selection.signal).map((classes, key) => key.flatMap(classes.entity)).distinct

  /** Which entry is selected; unlike [[selectedEntitySignal]], it does not change while the source is edited. */
  val selectedKeySignal: Signal[Option[UmlEntityKey]] = selection.signal

  /** Selects an entry as a click on it would, e.g. a function that was just added. */
  def selectedKey: Option[UmlEntityKey] = selection.now()

  def select(key: Option[UmlEntityKey]): Unit = selection.set(key)

  override def getDomElement(): Element = div(
    cls := "uml-structure-selector",
    children <-- diagram.map(_.classes).split(_.name)((_, _, umlClass) => renderClass(umlClass)), trailing)

  private def renderClass(umlClass: Signal[UmlClass]): Element = sectionTag(
    cls := "uml-class",
    div(cls := "uml-class__header",
      entry(umlClass.map(_.key), umlClass.map(_.name), cls("uml-entry--abstract") <-- umlClass.map(_.isAbstract))),
    compartment("uml-class__attributes", umlClass.map(_.attributes)),
    compartment("uml-class__functions", umlClass.map(_.functions)),
    child <-- umlClass.map(value => div(cls := "uml-class__actions", classFooter(value))))

  private def compartment(className: String, members: Signal[Seq[UmlMember]]): Element = ul(
    cls := s"uml-class__compartment $className",
    children <-- members.split(_.key)((_, _, member) => li(entry(member.map(_.key), member.map(_.label),
      cls("uml-entry--static") <-- member.map(_.isStatic),
      cls("uml-entry--abstract") <-- member.map(_.isAbstract)))))

  private def entry(key: Signal[UmlEntityKey], label: Signal[String], modifiers: Modifier[Button]*): Button = {
    val selected = key.combineWith(selection.signal).map((entryKey, chosen) => chosen.contains(entryKey))
    button(tpe := "button", cls := "uml-entry",
      cls("uml-entry--selected") <-- selected,
      aria.pressed <-- selected.map(_.toString),
      child.text <-- label,
      modifiers,
      onClick.compose(_.sample(key)) --> (clicked => selection.set(Some(clicked))))
  }
}
