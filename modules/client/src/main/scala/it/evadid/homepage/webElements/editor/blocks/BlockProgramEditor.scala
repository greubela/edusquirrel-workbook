package it.evadid.homepage.webElements.editor.blocks

import com.raquo.laminar.api.L.*
import com.raquo.laminar.codecs.StringAsIsCodec
import com.raquo.laminar.keys.SvgAttr
import it.evadid.core.datastructures.geometry.{Bounds, Point}
import it.evadid.core.datastructures.language.AppLanguage.{BlockDisplay, English, HumanLanguage, ProgrammingLanguage, Python}
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.AppElementRendered
import it.evadid.core.datastructures.vectorShapes.blocks.*
import it.evadid.core.datastructures.vectorShapes.renderer.SvgLaminarRenderer
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.EditorAppearance
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditorContext, EvaEditorLabels, EvaEditorTab}
import it.evadid.vm.code.errors.ProgrammingConversionError
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateBeExpression}
import it.evadid.util.logging.Logger
import it.evadid.vm.BeProgram
import it.evadid.vm.editing.{BeBlockLibrary, BeValueInput, BeNodePath, BeProgramEditing, BeSequenceSlot}
import it.evadid.vm.types.BeChildRole.BodySequence
import org.scalajs.dom

/** Drag-and-drop block editor bound directly to a program.
  *
  * Templates are dragged from the library into a slot; program statements are dragged between slots or
  * back onto the library to remove them. Clicking a value (parameter, repeat count or assigned value) opens
  * an editor that accepts an expression of the value's type; operators on literals are computed, so `3+5`
  * becomes `8`. Every edit replaces the program in `program`; drag, view and value drafts are the editor's
  * transient state.
  */
final case class BlockProgramEditor(program: Var[BeProgram], library: BeBlockLibrary, labels: BlockEditorLabels,
                               language: Signal[HumanLanguage] = Val(English), initialView: BlockView = BlockView())
  extends HtmlAppElement {

  private final case class ValueEdit(path: BeNodePath, draft: String, anchor: Point[Double])

  private val logger = Logger.withNameAndPrefixes(Some("BlockProgramEditor"))
  private val view = Var(initialView)
  private val press = Var(Option.empty[Press])
  private val editing = Var(Option.empty[ValueEdit])
  /** The element holding the program drawing, for converting pointer positions. */
  private val workspace = Var(Option.empty[dom.html.Element])

  private val rendering = language.distinct.map(human => BlockRenderingConfig(language = human))
  private val layout: Signal[AppElementRendered[Double]] = program.signal.combineWith(view.signal, rendering)
    .map((current, display, config) => BlockElements.layout(BeExpressionBlocks.program(current, display, config)))
  private val roles = layout.map(BlockElements.roles)
  private val dragged: Signal[Option[DragSource]] = press.signal.map(_.filter(_.active).flatMap(_.source)).distinct
  private val accepted = program.signal.combineWith(dragged).map((current, source) => source.fold(Set.empty)(BlockDragging.accepted(current, _)))
  private val target: Signal[Option[BeSequenceSlot]] = roles.combineWith(accepted, press.signal).map { (placed, slots, current) =>
    current.filter(_.active).flatMap(_.overProgram).flatMap(BlockDragging.nearest(placed, slots, _))
  }.distinct
  private val highlight = dragged.combineWith(accepted, target).map((source, slots, destination) =>
    BlockHighlight(source.nonEmpty, slots, destination)).distinct
  private val drawing = layout.combineWith(program.signal, view.signal, rendering, highlight).map { (idle, current, display, config, colours) =>
    if !colours.dragging then idle else BlockElements.layout(BeExpressionBlocks.program(current, display, config, colours))
  }

  private val roleAttr = SvgAttr("data-block-role", StringAsIsCodec, None)
  private val decorate: SvgLaminarRenderer.Decoration = rendered => rendered.elementConfig match {
    case BlockElementConfig(_, _, _, Some(role)) => role match {
      case BlockRole.Statement(_) => Seq(svg.cls := "block-editor__statement", roleAttr := "statement")
      case BlockRole.Value(_) => Seq(svg.cls := "block-editor__value", roleAttr := "value")
      case BlockRole.Slot(_) => Seq(svg.cls := "block-editor__slot", roleAttr := "slot")
    }
    case _ => Nil
  }

  private def client(event: dom.PointerEvent) = Point(event.clientX, event.clientY)

  private def drawingElement: Option[dom.Element] = workspace.now().flatMap(host => Option(host.querySelector("svg.block-editor__drawing")))

  /** The drawing's coordinate system: its view box, scaled to its rendered size. */
  private def viewBox(svgElement: dom.Element): (Bounds[Double], dom.DOMRect) = {
    val values = Option(svgElement.getAttribute("viewBox")).getOrElse("0 0 0 0").trim.split("\\s+").map(_.toDouble)
    (Bounds(Point(values(0), values(1)), it.evadid.core.datastructures.geometry.Dimension(values(2), values(3))), svgElement.getBoundingClientRect())
  }

  private def scale(box: Bounds[Double], rect: dom.DOMRect) =
    (if rect.width > 0 && box.width > 0 then box.width / rect.width else 1.0, if rect.height > 0 && box.height > 0 then box.height / rect.height else 1.0)

  /** The pointer in drawing coordinates while it is above the drawing. */
  private def programPoint(event: dom.PointerEvent): Option[Point[Double]] = drawingElement.flatMap { element =>
    val (box, rect) = viewBox(element)
    val (sx, sy) = scale(box, rect)
    Option.when(event.clientX >= rect.left && event.clientX <= rect.right && event.clientY >= rect.top && event.clientY <= rect.bottom)(
      Point(box.startX + (event.clientX - rect.left) * sx, box.startY + (event.clientY - rect.top) * sy))
  }

  /** Where a drawing position appears inside the workspace, for placing the value editor. */
  private def workspacePosition(point: Point[Double]): Point[Double] = (for {
    host <- workspace.now()
    element <- drawingElement
  } yield {
    val (box, rect) = viewBox(element)
    val (sx, sy) = scale(box, rect)
    val origin = host.getBoundingClientRect()
    Point(rect.left - origin.left + host.scrollLeft + (point.x - box.startX) / sx, rect.top - origin.top + host.scrollTop + (point.y - box.startY) / sy)
  }).getOrElse(point)

  private def startPress(event: dom.PointerEvent, source: Option[DragSource], value: Option[BeNodePath], inProgram: Option[Point[Double]]): Unit = {
    event.preventDefault()
    press.set(Some(Press(source, value, client(event), client(event), false, inProgram)))
  }

  private def finishPress(event: dom.PointerEvent, destination: Option[BeSequenceSlot], current: BeProgram,
                          placed: Seq[(BlockRole, Bounds[Double])]): Unit = {
    press.now().foreach { finished =>
      if finished.active then finished.source.foreach { source =>
        val overLibrary = event.target match {
          case element: dom.Element => Option(element.closest(".block-editor__library")).nonEmpty
          case _ => false
        }
        val result = if overLibrary then BlockDragging.discard(current, source) else destination.map(BlockDragging.drop(current, source, _)).getOrElse(Right(current))
        result.toOption.filter(_ != current).foreach(program.set)
      } else finished.value.foreach(path => openValueEditor(current, path, placed))
    }
    press.set(None)
  }

  private def openValueEditor(current: BeProgram, path: BeNodePath, placed: Seq[(BlockRole, Bounds[Double])]): Unit =
    for {
      text <- BeValueInput.textAt(current, path)
      bounds <- placed.collectFirst { case (BlockRole.Value(`path`), bounds) => bounds }
    } editing.set(Some(ValueEdit(path, text, workspacePosition(Point(bounds.startX, bounds.endY)))))

  private def commitValue(): Unit = editing.now().foreach { edit =>
    BeValueInput.update(program.now(), edit.path, edit.draft).foreach { edited =>
      editing.set(None)
      program.set(edited)
    }
  }

  /** Keyboard alternative to dragging: append the template to the end of the main program. */
  private def append(template: it.evadid.vm.code.abstractions.BeExpression): Unit = {
    val current = program.now()
    val main = BeNodePath.root / BodySequence(0)
    BeProgramEditing.slots(current).filter(_.sequence == main).lastOption
      .flatMap(slot => BeProgramEditing.insert(current, slot, template).toOption).foreach(program.set)
  }

  private def templateLabel(source: DragSource) = source match {
    case DragSource.Template(statement) => library.templates.find(_.statement == statement).flatMap(_.label)
    case DragSource.Moving(_, _) => None
  }

  private def image(rendered: AppElementRendered[Double]) = SvgLaminarRenderer.renderSvgImage(logger, rendered)

  private def libraryPanel: Element = div(
    cls := "block-editor__library",
    h3(cls := "block-editor__heading", child.text <-- labels.library),
    p(cls := "block-editor__hint", child.text <-- labels.libraryHint),
    div(cls := "block-editor__templates", role := "list",
      children <-- rendering.map(config => library.templates.map { template =>
        div(cls := "block-editor__template", role := "listitem", tabIndex := 0,
          aria.label := template.statement.structureInfo.toStringInLanguage(Python, config.language),
          onPointerDown.filter(_.button == 0) --> (event => startPress(event, Some(DragSource.Template(template.statement)), None, None)),
          onKeyDown.filter(event => event.key == "Enter" || event.key == " ") --> { event =>
            event.preventDefault()
            append(template.statement)
          },
          image(BlockElements.layout(BeExpressionBlocks.template(template.statement, template.label, config))))
      }))
  )

  private def toolbar: Element = div(
    cls := "block-editor__toolbar",
    label(cls := "block-editor__option",
      input(typ := "checkbox", checked <-- view.signal.map(_.placeholders),
        onInput.mapToChecked --> (enabled => view.update(_.copy(placeholders = enabled)))),
      span(child.text <-- labels.placeholders)),
    label(cls := "block-editor__option",
      span(child.text <-- labels.controlFlow),
      select(
        ControlFlowDisplay.values.toSeq.map(display => option(value := display.toString, child.text <-- labels.controlFlowOption(display))),
        value <-- view.signal.map(_.controlFlow.toString),
        onChange.mapToValue --> (selected => view.update(_.copy(controlFlow = ControlFlowDisplay.valueOf(selected))))))
  )

  private def valueEditor(path: BeNodePath, anchor: Point[Double]): Element = {
    val outcome = program.signal.combineWith(editing.signal).map((current, edit) =>
      edit.map(value => BeValueInput.update(current, value.path, value.draft)))
    val rejected = outcome.map(_.flatMap(_.left.toOption).map(_.expected))
    val errorId = s"block-editor-value-error-${path.roles.mkString("-").hashCode.abs}"
    div(
      cls := "block-editor__popup", role := "dialog", aria.label <-- labels.editValue,
      styleAttr := s"left: ${anchor.x}px; top: ${anchor.y + 4}px",
      onPointerDown --> (_.stopPropagation()),
      label(cls := "block-editor__popup-label",
        span(child.text <-- labels.editValue),
        input(typ := "text", cls := "block-editor__popup-input",
          value <-- editing.signal.map(_.map(_.draft).getOrElse("")),
          aria.invalid <-- rejected.map(_.nonEmpty.toString),
          aria.describedBy := errorId,
          onMountCallback(context => context.thisNode.ref.select()),
          onInput.mapToValue --> (draft => editing.update(_.map(_.copy(draft = draft)))),
          onKeyDown --> { event =>
            event.key match {
              case "Enter" => event.preventDefault(); commitValue()
              case "Escape" => event.preventDefault(); editing.set(None)
              case _ => ()
            }
          })),
      p(idAttr := errorId, cls := "block-editor__popup-error", role := "alert",
        child.text <-- rejected.flatMapSwitch(_.fold(Val(""))(labels.invalid))),
      div(cls := "block-editor__popup-actions",
        button(typ := "button", cls := "block-editor__popup-apply", disabled <-- rejected.map(_.nonEmpty),
          child.text <-- labels.apply, onClick --> (_ => commitValue())),
        button(typ := "button", cls := "block-editor__popup-cancel", child.text <-- labels.cancel,
          onClick --> (_ => editing.set(None))))
    )
  }

  private def ghost: Modifier[HtmlElement] = child.maybe <-- dragged.combineWith(rendering).map { (source, config) =>
    source.map(dragging => div(
      cls := "block-editor__ghost", aria.hidden := true,
      styleAttr <-- press.signal.map(_.fold("display: none")(current => s"left: ${current.current.x + 8}px; top: ${current.current.y + 8}px")),
      image(BlockElements.layout(BeExpressionBlocks.template(dragging.statement, templateLabel(dragging), config)))))
  }

  private def programPanel: Element = div(
    cls := "block-editor__program",
    h3(cls := "block-editor__heading", child.text <-- labels.program),
    div(
      cls := "block-editor__workspace",
      cls("is-dragging") <-- dragged.map(_.nonEmpty),
      onMountUnmountCallback(context => workspace.set(Some(context.thisNode.ref)), _ => workspace.set(None)),
      onPointerDown.compose(_.filter(_.button == 0).map(event => event -> programPoint(event)).withCurrentValueOf(roles, program.signal)) --> {
        (event, point, placed, current) =>
          point.foreach { position =>
            val (source, value) = BlockDragging.pressAt(current, placed, position)
            startPress(event, source, value, Some(position))
          }
      },
      child <-- drawing.map(rendered => SvgLaminarRenderer.renderSvgImage(logger, rendered, decorate).amend(svg.cls := "block-editor__drawing")),
      child.maybe <-- editing.signal.map(_.map(edit => edit.path -> edit.anchor)).distinct.map(_.map(valueEditor))
    )
  )

  override def getDomElement(): Element = div(
    cls := "block-editor",
    documentEvents(_.onPointerMove) --> { event =>
      if press.now().nonEmpty then press.update(_.map(_.movedTo(client(event), programPoint(event))))
    },
    documentEvents(_.onPointerUp).withCurrentValueOf(target, program.signal, roles) --> { (event, destination, current, placed) =>
      finishPress(event, destination, current, placed)
    },
    documentEvents(_.onPointerCancel) --> (_ => press.set(None)),
    documentEvents(_.onKeyDown).filter(_.key == "Escape") --> (_ => press.set(None)),
    program.signal.changes --> (_ => editing.set(None)),
    toolbar,
    div(cls := "block-editor__panels", libraryPanel, programPanel),
    ghost
  )
}

object BlockProgramEditor {
  /** The Eva tab editing the expression representation; other representations are converted by Eva first. */
  def forEvaTab(library: BeBlockLibrary = BeBlockLibrary.turtle): EvaEditorTab = new EvaEditorTab {
    override val language: ProgrammingLanguage = BlockDisplay
    override def label(labels: EvaEditorLabels): Signal[String] = labels.blocks.tab
    override def create(state: Var[ProgrammingState], context: EvaEditorContext): HtmlAppElement = {
      val program = state.bimap {
        case ProgrammingStateBeExpression(expression) => BeProgram(expression)
        case _ => BeProgram.empty // inactive while Eva switches to another language
      }(edited => ProgrammingStateBeExpression(edited.fullProgram))
      BlockProgramEditor(program, library, context.labels.blocks, context.environment.humanLanguage)
    }
  }
}
