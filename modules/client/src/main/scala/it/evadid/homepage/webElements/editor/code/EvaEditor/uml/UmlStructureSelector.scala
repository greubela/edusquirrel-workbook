package it.evadid.homepage.webElements.editor.code.EvaEditor.uml

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.core.datastructures.uml.*

/** A language-independent UML model rendered as clickable class boxes: name, attributes and functions.
  * It shows the `selection` it is given and reports clicks to `choose`; the owner decides what is selected.
  */
case class UmlStructureSelector(diagram: Signal[UmlClassDiagram],
    selection: Signal[Option[UmlEntityKey]],
    choose: Observer[UmlEntityKey],
    classFooter: UmlClass => Modifier[HtmlElement] = _ => emptyMod,
    trailing: Modifier[HtmlElement] = emptyMod) extends HtmlAppElement {
  import it.evadid.core.datastructures.uml.UmlSelectedEntity

  /** The selected entry with its current source, or `None` when nothing or a removed entry is selected. */
  val selectedEntitySignal: Signal[Option[UmlSelectedEntity]] =
    diagram.combineWith(selection).map((classes, key) => key.flatMap(classes.entity)).distinct

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
    val selected = key.combineWith(selection).map((entryKey, chosen) => chosen.contains(entryKey))
    button(tpe := "button", cls := "uml-entry",
      cls("uml-entry--selected") <-- selected,
      aria.pressed <-- selected.map(_.toString),
      child.text <-- label,
      modifiers,
      onClick.compose(_.sample(key)) --> choose)
  }
}
