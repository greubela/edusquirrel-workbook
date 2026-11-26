package it.evadid.homepage.webElements.editor.code.EvaEditor.uml

import com.raquo.laminar.api.L.*
import com.raquo.airstream.ownership.{Owner, Subscription}
import org.scalajs.dom
import scala.scalajs.js
import java.util.UUID
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.core.datastructures.uml.*

/** A language-independent UML model rendered as class boxes: name, attributes and functions.
  * It shows the `selection` it is given and reports clicks to `choose`; the owner decides what is selected.
  * Only the program's own classes are clickable and get the `classFooter`. Library and unknown classes are
  * read-only boxes under a stereotype; implied members (used, but defined nowhere) are marked in any box.
  */
case class UmlStructureSelector(diagram: Signal[UmlClassDiagram],
    selection: Signal[Option[UmlEntityKey]],
    choose: Observer[UmlEntityKey],
    classFooter: UmlClass => Modifier[HtmlElement] = _ => emptyMod,
    trailing: Modifier[HtmlElement] = emptyMod,
    labels: UmlStructureSelector.OriginLabels) extends HtmlAppElement {
  import it.evadid.core.datastructures.uml.UmlSelectedEntity
  import UmlRelationLayout.*
  private val root = Var(Option.empty[dom.Element])
  private val boxes = Var(Map.empty[String, Box])
  private val markerId = s"uml-${UUID.randomUUID()}"

  private def measure(): Unit = root.now().foreach { parent =>
    val area = parent.getBoundingClientRect()
    val nodes = parent.querySelectorAll(".uml-class")
    val next = (0 until nodes.length).map { index =>
      val node = nodes(index).asInstanceOf[dom.Element]
      val rect = node.getBoundingClientRect()
      node.getAttribute("data-uml-class") -> Box(rect.left - area.left, rect.top - area.top, rect.width, rect.height)
    }.toMap
    if next != boxes.now() then boxes.set(next)
  }

  private def watch(node: dom.Element, owner: Owner): Unit = {
    if js.typeOf(js.Dynamic.global.ResizeObserver) == "function" then {
      val observer = new dom.ResizeObserver((_, _) => measure())
      observer.observe(node)
      new Subscription(owner, () => observer.disconnect())
    }
    measure()
  }

  private def relationships: SvgElement = svg.svg(svg.cls := "uml-relationships",
    svg.defs(
      svg.marker(svg.idAttr := s"$markerId-triangle", svg.viewBox := "0 0 12 12", svg.refX := "12", svg.refY := "6",
        svg.markerWidth := "12", svg.markerHeight := "12", svg.orient := "auto",
        svg.path(svg.d := "M 0 0 L 12 6 L 0 12 Z", svg.cls := "uml-relation-head uml-relation-head--hollow")),
      svg.marker(svg.idAttr := s"$markerId-open", svg.viewBox := "0 0 12 12", svg.refX := "12", svg.refY := "6",
        svg.markerWidth := "12", svg.markerHeight := "12", svg.orient := "auto",
        svg.path(svg.d := "M 0 1 L 12 6 L 0 11", svg.cls := "uml-relation-head"))),
    svg.g(children <-- diagram.combineWith(boxes.signal).map { (model, measured) =>
      model.relations.flatMap { relation => for {
        from <- measured.get(relation.from)
        to <- measured.get(relation.to)
        arrow <- route(relation, from, to)
      } yield {
        val head = relation.kind match {
          case UmlRelation.Kind.Inheritance | UmlRelation.Kind.Realization => "triangle"
          case _ => "open"
        }
        svg.g(svg.cls := s"uml-relation uml-relation--${relation.kind.toString.toLowerCase}",
          svg.path(svg.d := arrow.path, svg.markerEnd := s"url(#$markerId-$head)"),
          Option.when(relation.kind == UmlRelation.Kind.Association)(svg.text(
            svg.x := arrow.labelX.toString, svg.y := arrow.labelY.toString,
            relation.role.getOrElse("") + (if relation.many then " *" else ""))))
      }}
    }))

  /** The selected entry with its current source, or `None` when nothing or a removed entry is selected. */
  val selectedEntitySignal: Signal[Option[UmlSelectedEntity]] =
    diagram.combineWith(selection).map((classes, key) => key.flatMap(classes.entity)).distinct

  override def getDomElement(): Element = div(
    cls := "uml-structure-selector",
    onMountCallback { context => root.set(Some(context.thisNode.ref)); watch(context.thisNode.ref, context.owner) },
    onUnmountCallback(_ => { root.set(None); boxes.set(Map.empty) }),
    relationships,
    // A class whose origin changes (an edited library copy becomes the program's own) gets a new box.
    children <-- diagram.map(_.classes).split(umlClass => (umlClass.name, umlClass.origin))((key, _, umlClass) =>
      renderClass(umlClass, key._2)), trailing)

  private def renderClass(umlClass: Signal[UmlClass], origin: UmlClassOrigin): Element = {
    val texts = labels.of(origin)
    sectionTag(
      cls := "uml-class",
      dataAttr("uml-class") <-- umlClass.map(_.name),
      onMountCallback(context => watch(context.thisNode.ref, context.owner)),
      cls(s"uml-class--${origin.toString.toLowerCase}") := !origin.editable,
      texts.fold(emptyMod)((_, hint) => title <-- hint),
      div(cls := "uml-class__header",
        child.maybe <-- umlClass.map(value => Option.when(value.isInterface)(span(cls := "uml-class__stereotype", "«interface»"))),
        texts.fold(emptyMod)((name, _) => span(cls := "uml-class__stereotype", child.text <-- name.map(value => s"«$value»"))),
        entry(origin, umlClass.map(_.key), umlClass.map(_.name), cls("uml-entry--abstract") <-- umlClass.map(_.isAbstract))),
      compartment(origin, "uml-class__attributes", umlClass.map(_.attributes)),
      compartment(origin, "uml-class__functions", umlClass.map(_.functions)),
      Option.when(origin.editable)(child <-- umlClass.map(value => div(cls := "uml-class__actions", classFooter(value)))))
  }

  private def compartment(origin: UmlClassOrigin, className: String, members: Signal[Seq[UmlMember]]): Element = ul(
    cls := s"uml-class__compartment $className",
    children <-- members.split(member => (member.key, member.implied))((key, _, member) => li(
      if key._2 then span(cls := "uml-entry uml-entry--readonly uml-entry--implied", title <-- labels.impliedHint, child.text <-- member.map(_.label))
      else entry(origin, member.map(_.key), member.map(_.label),
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
  /** Stereotypes and hints of read-only boxes, and the hint of implied members, by `blockeditor` map key. */
  final class OriginLabels(text: String => Signal[String]) {
    /** The stereotype shown above a read-only class's name, and the hint explaining it. */
    def of(origin: UmlClassOrigin): Option[(Signal[String], Signal[String])] = origin match {
      case UmlClassOrigin.Program => None
      case UmlClassOrigin.Library => Some(text("umlLibraryClass") -> text("umlLibraryClassHint"))
      case UmlClassOrigin.Unknown => Some(text("umlUnknownClass") -> text("umlUnknownClassHint"))
    }
    def impliedHint: Signal[String] = text("umlImpliedMemberHint")
  }
}
