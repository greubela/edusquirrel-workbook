package it.evadid.homepage.webElements.code

import com.raquo.airstream.ownership.Subscription
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.code.JavaMethodSource.*
import org.scalajs.dom

import java.util.UUID

/** A UML class diagram of the current Java source: class boxes with name, attribute and operation
  * compartments, and relationship arrows. It is derived from the source analysis alone; selecting an
  * operation opens its code, the add buttons open the editor's structure dialogs.
  */
private[code] object JavaClassDiagram {

  final case class Actions(
      select: JavaMethod => Unit,
      editField: JavaField => Unit,
      addClass: () => Unit,
      addField: String => Unit,
      addConstructor: String => Unit,
      addMethod: String => Unit
  )

  private final case class Box(left: Double, top: Double, width: Double, height: Double) {
    def right: Double = left + width
    def bottom: Double = top + height
    def centerX: Double = left + width / 2
    def centerY: Double = top + height / 2
  }

  private final case class Arrow(relation: Relation, path: String, labelX: Double, labelY: Double, anchor: String)

  /** Rows of the diagram: supertypes one row above their subtypes, library classes last. */
  private def rows(analysis: JavaMethodSource): List[List[JavaClass]] = {
    val byName = analysis.classes.map(clazz => clazz.name -> clazz).toMap
    def level(clazz: JavaClass, visiting: Set[String]): Int =
      (clazz.superclasses ++ clazz.interfaces).flatMap(byName.get).filterNot(parent => visiting(parent.name))
        .map(parent => level(parent, visiting + clazz.name) + 1).maxOption.getOrElse(0)
    val levels = analysis.classes.groupBy(clazz => level(clazz, Set.empty)).toList.sortBy(_._1).map(_._2)
    (levels :+ analysis.libraries).filter(_.nonEmpty)
  }

  private def route(from: Box, to: Box, relation: Relation): Option[Arrow] = {
    def vertical(startY: Double, endY: Double, labelOffset: Double) = {
      val middle = (startY + endY) / 2
      Arrow(relation, s"M ${from.centerX} $startY V $middle H ${to.centerX} V $endY", to.centerX + 6, endY + labelOffset, "start")
    }
    def horizontal(startX: Double, endX: Double, anchor: String) = {
      val middle = (startX + endX) / 2
      Arrow(relation, s"M $startX ${from.centerY} H $middle V ${to.centerY} H $endX",
        if anchor == "end" then endX - 8 else endX + 8, to.centerY - 6, anchor)
    }
    if to.bottom <= from.top then Some(vertical(from.top, to.bottom, 14))
    else if from.bottom <= to.top then Some(vertical(from.bottom, to.top, -6))
    else if from.right <= to.left then Some(horizontal(from.right, to.left, "end"))
    else if to.right <= from.left then Some(horizontal(from.left, to.right, "start"))
    else None
  }

  private def measure(canvas: dom.html.Element, relations: List[Relation]): List[Arrow] = {
    val origin = canvas.getBoundingClientRect()
    def box(name: String): Option[Box] =
      Option(canvas.querySelector(s"[data-class-name='$name']")).map { element =>
        val rect = element.getBoundingClientRect()
        Box(rect.left - origin.left + canvas.scrollLeft, rect.top - origin.top + canvas.scrollTop, rect.width, rect.height)
      }
    relations.flatMap(relation => for {
      from <- box(relation.from)
      to <- box(relation.to)
      arrow <- route(from, to, relation)
    } yield arrow)
  }

  private def kindLabel(kind: RelationKind, labels: JavaEditorLabels): Signal[String] = kind match {
    case RelationKind.Inheritance => labels.inherits
    case RelationKind.Realization => labels.realizes
    case RelationKind.Association => labels.knows
    case RelationKind.Dependency => labels.uses
  }

  private def renderArrow(arrow: Arrow, markers: String, labels: JavaEditorLabels): SvgElement = {
    val relation = arrow.relation
    val dashed = relation.kind == RelationKind.Realization || relation.kind == RelationKind.Dependency
    val head = relation.kind match {
      case RelationKind.Inheritance | RelationKind.Realization => "triangle"
      case _ => "open"
    }
    svg.g(
      svg.cls := s"java-uml__arrow java-uml__arrow--${relation.kind.toString.toLowerCase}",
      svg.path(
        svg.d := arrow.path, svg.fill := "none", svg.stroke := "currentColor", svg.strokeWidth := "1.5",
        if dashed then svg.strokeDashArray := "6 4" else emptyMod,
        svg.markerEnd := s"url(#$markers-$head)"
      ),
      relation.kind match {
        case RelationKind.Association =>
          svg.text(svg.cls := "java-uml__arrow-label", svg.x := arrow.labelX.toString, svg.y := arrow.labelY.toString,
            svg.textAnchor := arrow.anchor, relation.role.getOrElse("") + (if relation.many then " *" else ""))
        case RelationKind.Dependency =>
          svg.text(svg.cls := "java-uml__arrow-label", svg.x := arrow.labelX.toString, svg.y := arrow.labelY.toString,
            svg.textAnchor := arrow.anchor, child.text <-- labels.uses.map(text => s"«$text»"))
        case _ => emptyNode
      }
    )
  }

  private def markerDefinitions(markers: String): SvgElement =
    svg.defs(
      svg.marker(
        svg.idAttr := s"$markers-triangle", svg.viewBox := "0 0 12 12", svg.refX := "12", svg.refY := "6",
        svg.markerWidth := "12", svg.markerHeight := "12", svg.orient := "auto",
        svg.path(svg.d := "M 0 0 L 12 6 L 0 12 Z", svg.cls := "java-uml__marker-hollow", svg.stroke := "currentColor")
      ),
      svg.marker(
        svg.idAttr := s"$markers-open", svg.viewBox := "0 0 12 12", svg.refX := "12", svg.refY := "6",
        svg.markerWidth := "10", svg.markerHeight := "10", svg.orient := "auto",
        svg.path(svg.d := "M 0 0 L 12 6 L 0 12", svg.fill := "none", svg.stroke := "currentColor", svg.strokeWidth := "1.5")
      )
    )

  private def addButton(text: Signal[String], disabledSignal: Signal[Boolean], action: () => Unit): Element =
    button(
      typ := "button",
      cls := "java-uml__add",
      disabled <-- disabledSignal,
      span(aria.hidden := true, "+ "),
      child.text <-- text,
      onClick --> (_ => action())
    )

  private def operation(method: JavaMethod, selected: Signal[Option[JavaMethod]], locked: Signal[Boolean],
      actions: Actions, readOnly: Boolean): Element = {
    val classes = "java-uml__member java-uml__operation" +
      (if method.isStatic then " java-uml__member--static" else "") +
      (if method.isAbstract then " java-uml__member--abstract" else "") +
      (if method.isConstructor then " java-uml__member--constructor" else "")
    val signature = s"${if method.returnType.isEmpty then "" else method.returnType + " "}${method.name}(${method.parameters})"
    if readOnly then span(cls := classes, title := signature, umlOperation(method))
    else {
      val isSelected = selected.map(_.exists(current => sameMethod(current, method) && current.start == method.start))
      button(
        typ := "button",
        cls <-- isSelected.map(if _ then s"$classes java-uml__member--selected" else classes),
        dataAttr("member") := method.name,
        aria.pressed <-- isSelected.map(_.toString),
        disabled <-- locked,
        title := signature,
        umlOperation(method),
        onClick --> (_ => actions.select(method))
      )
    }
  }

  private def attribute(field: JavaField, locked: Signal[Boolean], actions: Actions, readOnly: Boolean): Element = {
    val classes = "java-uml__member java-uml__attribute" + (if field.isStatic then " java-uml__member--static" else "")
    val declaration = s"${field.javaType} ${field.name}${field.initializer.fold("")(value => s" = $value")}"
    if readOnly || field.shared then span(cls := classes, title := declaration, umlAttribute(field))
    else button(
      typ := "button",
      cls := classes,
      dataAttr("member") := field.name,
      disabled <-- locked,
      title := declaration,
      umlAttribute(field),
      onClick --> (_ => actions.editField(field))
    )
  }

  private def classBox(clazz: JavaClass, selected: Signal[Option[JavaMethod]], locked: Signal[Boolean],
      features: JavaStructureFeatures, labels: JavaEditorLabels, actions: Actions): Element = {
    val readOnly = clazz.library
    // Python's module functions: no attributes or constructors, and no class name of its own.
    val module = clazz.isModule
    val stereotype: Modifier[HtmlElement] =
      if clazz.library then span(cls := "java-uml__stereotype", child.text <-- labels.library.map(text => s"«$text»"))
      else if clazz.isInterface then span(cls := "java-uml__stereotype", "«interface»")
      else if module then span(cls := "java-uml__stereotype", "«module»")
      else emptyMod
    val name = div(
      cls := "java-uml__name-compartment",
      stereotype,
      strong(cls := "java-uml__class-name" + (if clazz.isAbstract && !clazz.isInterface then " java-uml__class-name--abstract" else ""),
        if module then child.text <-- labels.module else clazz.name)
    )
    val attributes = div(
      cls := "java-uml__compartment java-uml__attributes",
      clazz.fields.map(attribute(_, locked, actions, readOnly)),
      if !readOnly && !module && features.addField && !clazz.isInterface then addButton(labels.addField, locked, () => actions.addField(clazz.name)) else emptyMod
    )
    val operations = div(
      cls := "java-uml__compartment java-uml__operations",
      clazz.methods.map(operation(_, selected, locked, actions, readOnly)),
      if !readOnly && !module && features.addConstructor && !clazz.isInterface then
        addButton(labels.addConstructor, locked, () => actions.addConstructor(clazz.name)) else emptyMod,
      if !readOnly && features.addMethod then
        addButton(if module then labels.addFunction else labels.addMethod, locked, () => actions.addMethod(clazz.name)) else emptyMod
    )
    val boxClasses = "java-uml__class" + (if clazz.library then " java-uml__class--library" else "") +
      (if clazz.isInterface then " java-uml__class--interface" else "")
    if readOnly then detailsTag(
      cls := boxClasses,
      dataAttr("class-name") := clazz.name,
      summaryTag(name),
      operations
    )
    else div(
      cls := boxClasses + (if module then " java-uml__class--module" else ""),
      dataAttr("class-name") := clazz.name,
      role := "group",
      if module then aria.label <-- labels.module else aria.label := clazz.name,
      name,
      if module then emptyMod else attributes,
      operations
    )
  }

  private def relationText(relation: Relation, labels: JavaEditorLabels): Signal[String] =
    kindLabel(relation.kind, labels).combineWith(labels.module).map { (kind, module) =>
      def named(name: String) = if name.isEmpty then module else name
      val role = relation.role.fold("")(name => s" ($name${if relation.many then " *" else ""})")
      s"${named(relation.from)} $kind ${named(relation.to)}$role"
    }

  def render(
      analysis: Signal[JavaMethodSource],
      draft: Signal[Boolean],
      locked: Signal[Boolean],
      selected: Signal[Option[JavaMethod]],
      features: JavaStructureFeatures,
      labels: JavaEditorLabels,
      actions: Actions,
      navigationId: String
  ): Element = {
    val markers = s"java-uml-${UUID.randomUUID()}"
    val arrows = Var(List.empty[Arrow])
    val canvasSize = Var((0.0, 0.0))
    navTag(
      idAttr := navigationId,
      aria.label <-- labels.diagramDescription,
      cls <-- draft.map(if _ then "java-uml java-uml--draft" else "java-uml"),
      div(
        cls := "java-uml__heading",
        div(
          h3(cls := "java-uml__title", child.text <-- labels.diagramView),
          p(cls := "java-uml__hint", child.text <-- labels.diagramHint)
        ),
        if features.addClass then addButton(labels.addClass, locked, actions.addClass) else emptyMod
      ),
      p(
        cls <-- draft.map(if _ then "java-uml__draft-notice" else "java-uml__draft-notice java-uml__draft-notice--hidden"),
        role := "status",
        child.text <-- labels.draftNotice
      ),
      div(
        cls := "java-uml__canvas",
        svg.svg(
          svg.cls := "java-uml__relations-layer",
          aria.hidden := true,
          svg.width <-- canvasSize.signal.map(_._1.toString),
          svg.height <-- canvasSize.signal.map(_._2.toString),
          markerDefinitions(markers),
          children <-- arrows.signal.map(_.map(renderArrow(_, markers, labels)))
        ),
        children <-- analysis.map { current =>
          if current.classes.isEmpty && current.libraries.isEmpty then List(
            div(
              cls := "java-uml__empty",
              strong(child.text <-- labels.noClasses),
              span(child.text <-- labels.noClassesHint)
            )
          )
          else rows(current).map(row => div(cls := "java-uml__row", row.map(classBox(_, selected, locked, features, labels, actions))))
        },
        onMountCallback { ctx =>
          val canvas = ctx.thisNode.ref
          // Measured in the next frame: the class boxes of a new analysis are rendered after this observer runs.
          def layout(relations: List[Relation]): Unit = dom.window.requestAnimationFrame { _ =>
            if canvas.isConnected then {
              canvasSize.set((canvas.scrollWidth.toDouble, canvas.scrollHeight.toDouble))
              arrows.set(measure(canvas, relations))
            }
          }
          val latest = Var(List.empty[Relation])
          analysis.foreach { current =>
            latest.set(current.relations)
            layout(current.relations)
          }(using ctx.owner)
          val observer = new dom.ResizeObserver((_, _) => layout(latest.now()))
          observer.observe(canvas)
          new Subscription(ctx.owner, () => observer.disconnect())
        }
      ),
      ul(
        cls := "java-uml__relation-list",
        aria.label <-- labels.relations,
        children <-- analysis.map(_.relations.map(relation => li(child.text <-- relationText(relation, labels))))
      )
    )
  }
}
