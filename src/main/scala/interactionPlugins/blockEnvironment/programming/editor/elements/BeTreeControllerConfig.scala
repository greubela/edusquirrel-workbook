package interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.eventPropToProcessor
import contentmanagement.model.vm.code.tree.{BeExpressionNode, BeExtensionPoint}
import contentmanagement.model.vm.types.BeChildPosition
import interactionPlugins.blockEnvironment.config.*
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.editor.*
import org.scalajs.dom.{DragEvent, MouseEvent}


trait BeTreeControllerConfig {

  def isEditable: Boolean = false

  def onClicked: Option[(MouseEvent, BeProgram, BeExpressionNode) => Any] = None

  def onMouseEnter: Option[(MouseEvent, BeProgram, BeExpressionNode) => Any] = None

  def onMouseLeave: Option[(MouseEvent, BeProgram, BeExpressionNode) => Any] = None

  def onDragStart: Option[(DragEvent, BeProgram) => Any] = None

  def onDragEnd: Option[(DragEvent, BeProgram) => Any] = None

  def onDraggedOver: Option[(MouseEvent, BeProgram, BeExpressionNode) => Any] = None

  def onDropped: Option[(MouseEvent, BeProgram) => Any] = None

  def getMouseAmendsForShape(beProgram: BeProgram, exprNode: BeExpressionNode): Seq[L.Modifier[L.SvgElement]] = {
    List(
      onMouseEnter.map(f => L.onMouseEnter --> { e => f(e, beProgram, exprNode) }),
      onMouseLeave.map(f => L.onMouseLeave --> { e => f(e, beProgram, exprNode) }),
      onClicked.map(f => L.onClick.stopPropagation --> { e => f(e, beProgram, exprNode) })
    ).flatten
  }

  def getHtmlDragAmends(beProgram: BeProgram): Seq[L.Modifier[L.HtmlElement]] = {

    val dragStartMod = onDragStart.map(f => Seq(
      L.draggable := true, // allow dragging from this element
      L.onDragStart --> { e => f(e, beProgram) }
    ))
    val dragEndMods = onDragEnd.map(f => Seq(
      L.onDragEnd --> { e => f(e, beProgram) }
    ))
    val onDroppedMods = onDropped.map(f => Seq(
      L.onDragOver.preventDefault --> (_ => ()),
      L.onDrop.stopPropagation.preventDefault --> { e => f(e, beProgram) }
    )).toList.flatten
    
    val list: Seq[L.Modifier[L.HtmlElement]] = (dragStartMod ++ dragEndMods).flatten.toList
    list
  }


}


object BeTreeControllerConfig {

  def libraryTreeConfig(editorState: EditorState): BeTreeControllerConfig = new BeTreeControllerConfig {

    override def onDragStart: Option[(DragEvent, BeProgram) => Any] = Some((dragEvent, program) => editorState.controllerStateVar.update(oldState => oldState.copy(draggingEvent = Some(BeDraggingEvent(program)))))

    override def onDragEnd: Option[(DragEvent, BeProgram) => Any] = Some((dragEvent, program) => editorState.controllerStateVar.update(oldState => oldState.copy(draggingEvent = None)))

    override def onClicked: Option[(MouseEvent, BeProgram, BeExpressionNode) => Any] = Some((mouseEvent, program, childPos) => println("clicking: " + childPos))

  }

  def editTreeConfig(editorState: EditorState): BeTreeControllerConfig = new BeTreeControllerConfig {
    override val isEditable: Boolean = true

    override def onClicked: Option[(MouseEvent, BeProgram, BeExpressionNode) => Any] = Some((mouseEvent, program, childPos) => println("clicking: " + childPos))

    override def onMouseEnter: Option[(MouseEvent, BeProgram, BeExpressionNode) => Any] = defaultOnMouseEnter(editorState)

    override def onMouseLeave: Option[(MouseEvent, BeProgram, BeExpressionNode) => Any] = defaultOnMouseLeave(editorState)
  }

  def defaultOnMouseEnter(editorState: EditorState): Option[(MouseEvent, BeProgram, BeExpressionNode) => Any] =
    Some(
      (mouseEvent: MouseEvent, program: BeProgram, exprNode: BeExpressionNode) => exprNode match {
        case BeExtensionPoint(isRequired, childPosition, extensionType) => {
          editorState.controllerStateVar.update(oldVal => oldVal.copy(mouseOverNode = Some(BeMouseOverExtensionPoint(program, exprNode.asInstanceOf[BeExtensionPoint]))))
        }
        case _ => {

        }
      }
    )

  def defaultOnMouseLeave(editorState: EditorState): Option[(MouseEvent, BeProgram, BeExpressionNode) => Any] =
    Some(
      (mouseEvent: MouseEvent, program: BeProgram, exprNode: BeExpressionNode) => exprNode match {
        case BeExtensionPoint(isRequired, childPosition, extensionType) => {
          editorState.controllerStateVar.update(oldVal => oldVal.copy(mouseOverNode = None))
        }
        case _ => {

        }
      }
    )


}
