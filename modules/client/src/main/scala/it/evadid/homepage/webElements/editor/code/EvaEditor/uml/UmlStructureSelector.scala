package it.evadid.homepage.webElements.editor.code.EvaEditor.uml

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.core.datastructures.uml.*

/** A language-independent UML model rendered as class boxes: name, attributes and functions.
  * It shows the `selection` it is given and reports clicks to `choose`; the owner decides what is selected.
  * Only the program's own classes are clickable and get the `classFooter`. Library and unknown classes are
  * read-only boxes under the stereotype and hint `originLabels` gives them.
  */
case class UmlStructureSelector(diagram: Signal[UmlClassDiagram],
    selection: Signal[Option[UmlEntityKey]],
    choose: Observer[UmlEntityKey],
    classFooter: UmlClass => Modifier[HtmlElement] = _ => emptyMod,
    trailing: Modifier[HtmlElement] = emptyMod,
    originLabels: UmlClassOrigin => Option[UmlStructureSelector.OriginLabels] = _ => None) extends HtmlAppElement {
  import it.evadid.core.datastructures.uml.UmlSelectedEntity

  /** The selected entry with its current source, or `None` when nothing or a removed entry is selected. */
  val selectedEntitySignal: Signal[Option[UmlSelectedEntity]] =
    diagram.combineWith(selection).map((classes, key) => key.flatMap(classes.entity)).distinct

  override def getDomElement(): Element = div(
    cls := "uml-structure-selector",
    // A class whose origin changes (an edited library copy becomes the program's own) gets a new box.
    children <-- diagram.map(_.classes).split(umlClass => (umlClass.name, umlClass.origin))((key, _, umlClass) =>
      renderClass(umlClass, key._2)), trailing)

  private def renderClass(umlClass: Signal[UmlClass], origin: UmlClassOrigin): Element = {
    val labels = originLabels(origin).filterNot(_ => origin.editable)
    sectionTag(
      cls := "uml-class",
      cls(s"uml-class--${origin.toString.toLowerCase}") := !origin.editable,
      labels.fold(emptyMod)(texts => title <-- texts.hint),
      div(cls := "uml-class__header",
        labels.fold(emptyMod)(texts => span(cls := "uml-class__stereotype", child.text <-- texts.name.map(name => s"«$name»"))),
        entry(origin, umlClass.map(_.key), umlClass.map(_.name), cls("uml-entry--abstract") <-- umlClass.map(_.isAbstract))),
      compartment(origin, "uml-class__attributes", umlClass.map(_.attributes)),
      compartment(origin, "uml-class__functions", umlClass.map(_.functions)),
      Option.when(origin.editable)(child <-- umlClass.map(value => div(cls := "uml-class__actions", classFooter(value)))))
  }

  private def compartment(origin: UmlClassOrigin, className: String, members: Signal[Seq[UmlMember]]): Element = ul(
    cls := s"uml-class__compartment $className",
    children <-- members.split(_.key)((_, _, member) => li(entry(origin, member.map(_.key), member.map(_.label),
      cls("uml-entry--static") <-- member.map(_.isStatic),
      cls("uml-entry--abstract") <-- member.map(_.isAbstract)))))

  private def entry(origin: UmlClassOrigin, key: Signal[UmlEntityKey], label: Signal[String], modifiers: Modifier[HtmlElement]*): HtmlElement =
    if !origin.editable then span(cls := "uml-entry uml-entry--readonly", child.text <-- label, modifiers)
    else {
      val selected = key.combineWith(selection).map((entryKey, chosen) => chosen.contains(entryKey))
      button(tpe := "button", cls := "uml-entry",
        cls("uml-entry--selected") <-- selected,
        aria.pressed <-- selected.map(_.toString),
        child.text <-- label,
        modifiers,
        onClick.compose(_.sample(key)) --> choose)
    }
}

object UmlStructureSelector {
  /** The stereotype shown above a read-only class's name, and the hint explaining it. */
  final case class OriginLabels(name: Signal[String], hint: Signal[String])
}
