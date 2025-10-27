package interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.eventPropToProcessor
import com.raquo.laminar.nodes.ReactiveHtmlElement
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole}
import interactionPlugins.blockEnvironment.config.{BeDraggingEvent, BeMouseOverNode}
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.editor.*
import org.scalajs.dom.{DragEvent, MouseEvent}


trait BeTreeControllerConfig {

  def isEditable: Boolean = false

  def onClicked: Option[(MouseEvent, BeProgram, BeChildPosition) => Any] = None

  def onMouseEnter: Option[(MouseEvent, BeProgram, BeChildPosition) => Any] = None

  def onMouseLeave: Option[(MouseEvent, BeProgram, BeChildPosition) => Any] = None

  def onDragStart: Option[(DragEvent, BeProgram) => Any] = None

  def onDragEnd: Option[(DragEvent, BeProgram) => Any] = None

  def onDraggedOver: Option[(MouseEvent, BeProgram, BeChildPosition) => Any] = None

  def onDropped: Option[(MouseEvent, BeProgram, BeChildPosition) => Any] = None

  def getMouseAmendsForPosition(beProgram: BeProgram, positionAsChild: BeChildPosition): Seq[L.Modifier[L.SvgElement]] = {
    List(
      onMouseEnter.map(f => L.onMouseEnter --> { e => f(e, beProgram, positionAsChild) }),
      onMouseLeave.map(f => L.onMouseLeave --> { e => f(e, beProgram, positionAsChild) }),
      onClicked.map(f => L.onClick.stopPropagation --> { e => f(e, beProgram, positionAsChild) })
    ).flatten
  }

  def getSvgDropAmends(beProgram: BeProgram, positionAsChild: BeChildPosition): Seq[L.Modifier[L.SvgElement]] = {
    onDropped.map(f => Seq(
      L.onDragOver.preventDefault --> (_ => ()),
      L.onDrop.stopPropagation.preventDefault --> { e => f(e, beProgram, positionAsChild) }
    )).toList.flatten
  }

  def getHtmlDragAmends(beProgram: BeProgram): Seq[L.Modifier[L.HtmlElement]] = {

    val dragStartMod = onDragStart.map(f => Seq(
      L.draggable := true, // allow dragging from this element
      L.onDragStart --> { e => f(e, beProgram) }
    ))
    val dragEndMods = onDragEnd.map(f => Seq(
      L.onDragEnd --> { e => f(e, beProgram) }
    ))
    
    val list: Seq[L.Modifier[L.HtmlElement]] = (dragStartMod ++ dragEndMods).flatten.toList
    list
  }


}


object BeTreeControllerConfig {

  def libraryTreeConfig(editorState: TreeEditorState): BeTreeControllerConfig = new BeTreeControllerConfig {

    override def onDragStart: Option[(DragEvent, BeProgram) => Any] = Some((dragEvent, program) => editorState.controllerStateVar.update(oldState => oldState.copy(draggingEvent = Some(BeDraggingEvent(program)))))

    override def onDragEnd: Option[(DragEvent, BeProgram) => Any] = Some((dragEvent, program) => editorState.controllerStateVar.update(oldState => oldState.copy(draggingEvent = None)))

    override def onClicked: Option[(MouseEvent, BeProgram, BeChildPosition) => Any] = Some((mouseEvent, program, childPos) => println("clicking: " + childPos))

    override def onMouseEnter: Option[(MouseEvent, BeProgram, BeChildPosition) => Any] = Some((mouseEvent, program, childPos) => defaultOnMouseEnter(editorState))

    override def onMouseLeave: Option[(MouseEvent, BeProgram, BeChildPosition) => Any] = Some((mouseEvent, program, childPos) => defaultOnMouseLeave(editorState))
  }

  def editTreeConfig(editorState: TreeEditorState): BeTreeControllerConfig = new BeTreeControllerConfig {

    override val isEditable: Boolean = true

    override def onClicked: Option[(MouseEvent, BeProgram, BeChildPosition) => Any] = Some((mouseEvent, program, childPos) => println("clicking: " + childPos))

    override def onMouseEnter: Option[(MouseEvent, BeProgram, BeChildPosition) => Any] = defaultOnMouseEnter(editorState)

    override def onMouseLeave: Option[(MouseEvent, BeProgram, BeChildPosition) => Any] = defaultOnMouseLeave(editorState)

  }

  def defaultOnMouseEnter(editorState: TreeEditorState): Option[(MouseEvent, BeProgram, BeChildPosition) => Any] =
    Some(
      (mouseEvent: MouseEvent, program: BeProgram, childPos: BeChildPosition)
      => editorState.controllerStateVar.update(oldVal => oldVal.copy(mouseOverNode = Some(BeMouseOverNode(program, childPos))))
    )

  def defaultOnMouseLeave(editorState: TreeEditorState): Option[(MouseEvent, BeProgram, BeChildPosition) => Any] =
    Some(
      (mouseEvent: MouseEvent, program: BeProgram, childPos: BeChildPosition)
      => editorState.controllerStateVar.update(oldVal => oldVal.copy(mouseOverNode = None))
    )


}
