package interactionPlugins.blockEnvironment.config

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.eventPropToProcessor
import datastructures.core.vm.code.tree.{BeExpressionNode, BeExtensionPoint}
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.editor.*
import interactionPlugins.blockEnvironment.programming.editor.elements.*
import it.evadid.core.datastructures.geometry.Point
import org.scalajs.dom.{DragEvent, MouseEvent}

trait BeTreeControllerConfig {

  def isEditable: Boolean = false

  def onDivMouseEnter: Option[(MouseEvent, BeProgram, L.HtmlElement) => Any] = None

  def onDivMouseLeave: Option[(MouseEvent, BeProgram, L.HtmlElement) => Any] = None

  def onDivMouseMoved: Option[(MouseEvent, BeProgram, L.HtmlElement) => Any] = None

  def onDivMouseClicked: Option[(MouseEvent, BeProgram, L.HtmlElement) => Any] = None

  def onShapeClicked: Option[(MouseEvent, BeProgram, BeExpressionNode) => Any] = None

  def onShapeMouseEnter: Option[(MouseEvent, BeProgram, BeExpressionNode) => Any] = None

  def onShapeMouseLeave: Option[(MouseEvent, BeProgram, BeExpressionNode) => Any] = None

  def onDivDragEnd: Option[(DragEvent, BeProgram, L.HtmlElement) => Any] = None
  def onDivDragStart: Option[(DragEvent, BeProgram, L.HtmlElement) => Any] = None
  def onDivDraggedOver: Option[(DragEvent, BeProgram, L.HtmlElement) => Any] = None
  def onDivDragLeave: Option[(DragEvent, BeProgram, L.HtmlElement) => Any] = None
  def onDivDropped: Option[(DragEvent, BeProgram, L.HtmlElement) => Any] = None

  def getMouseAmendsForShape(beProgram: BeProgram, exprNode: BeExpressionNode): Seq[L.Modifier[L.SvgElement]] = {
    List(
      onShapeMouseEnter.map(f => L.onMouseEnter --> { e => f(e, beProgram, exprNode) }),
      onShapeMouseLeave.map(f => L.onMouseLeave --> { e => f(e, beProgram, exprNode) }),
      onShapeClicked.map(f => L.onClick.stopPropagation --> { e => f(e, beProgram, exprNode) })
    ).flatten
  }

  def getMouseAmendsForDiv(beProgram: BeProgram, element: L.HtmlElement): Seq[L.Modifier[L.HtmlElement]] = {
    List(
      onDivMouseEnter.map(f => L.onMouseEnter --> { e => f(e, beProgram, element) }),
      onDivMouseLeave.map(f => L.onMouseLeave --> { e => f(e, beProgram, element) }),
      onDivMouseClicked.map(f => L.onClick --> { e => f(e, beProgram, element) }),
      onDivMouseMoved.map(f => L.onMouseMove --> { e => f(e, beProgram, element) })
    ).flatten
  }

  def getHtmlDragDropAmends(beProgram: BeProgram, element: L.HtmlElement): Seq[L.Modifier[L.HtmlElement]] = {
    val dragStartMod: Option[Seq[L.Modifier[L.HtmlElement]]] = onDivDragStart.map(f => Seq(
      L.draggable := true, // allow dragging from this element
      L.onDragStart --> { e => f(e, beProgram, element) }
    ))
    val dragLeaveMods: Option[Seq[L.Modifier[L.HtmlElement]]] = onDivDragLeave.map(f => Seq(
      L.onDragLeave --> { e => f(e, beProgram, element) }
    ))
    val dragEndMods: Option[Seq[L.Modifier[L.HtmlElement]]] = onDivDragEnd.map(f => Seq(
      L.onDragEnd --> { e => f(e, beProgram, element) }
    ))
    val onDraggedOverMods: Option[Seq[L.Modifier[L.HtmlElement]]] = onDivDraggedOver.map(f => Seq(
      L.onDragOver.preventDefault --> (e => f(e, beProgram, element)),
    ))
    val onDroppedMods: Option[Seq[L.Modifier[L.HtmlElement]]] = onDivDropped.map(f => Seq(
      L.onDrop.stopPropagation.preventDefault --> { e => f(e, beProgram, element) }
    ))

    val list: Seq[L.Modifier[L.HtmlElement]] = (dragStartMod ++ dragEndMods ++ onDraggedOverMods ++ onDroppedMods).flatten.toList
    list
  }

}


object BeTreeControllerConfig {

  def noOpConfig(): BeTreeControllerConfig = new BeTreeControllerConfig {
    override def isEditable: Boolean = false
  }
  
  def libraryTreeConfig(editorState: EditorState): BeTreeControllerConfig = new BeTreeControllerConfig {

    override def onDivDragStart: Option[(DragEvent, BeProgram, L.HtmlElement) => Any] =
      Some((dragEvent, program, htmlElement) => editorState.controllerStateVar.update(oldState => oldState.copy(draggingEvent = Some(BeDraggingEvent(program)))))
    override def onDivDragEnd: Option[(DragEvent, BeProgram, L.HtmlElement) => Any] =
      Some((dragEvent, program, htmlElement) => editorState.controllerStateVar.update(oldState => oldState.copy(draggingEvent = None)))
    override def onShapeClicked: Option[(MouseEvent, BeProgram, BeExpressionNode) => Any] = Some((mouseEvent, program, childPos) => println("clicking: " + childPos))

    override def onDivDraggedOver: Option[(MouseEvent, BeProgram, L.HtmlElement) => Any] = defaultOnDragEnteredOrMovedInDiv(editorState)
    override def onDivDragLeave: Option[(MouseEvent, BeProgram, L.HtmlElement) => Any] = defaultOnDragLeaveDiv(editorState)
  }

  def editTreeConfig(editorState: EditorState): BeTreeControllerConfig = new BeTreeControllerConfig {
    override val isEditable: Boolean = true

    override def onShapeClicked: Option[(MouseEvent, BeProgram, BeExpressionNode) => Any] = Some((mouseEvent, program, childPos) => println("clicking: " + childPos))
    override def onShapeMouseEnter: Option[(MouseEvent, BeProgram, BeExpressionNode) => Any] = defaultOnMouseEnterShape(editorState)
    override def onShapeMouseLeave: Option[(MouseEvent, BeProgram, BeExpressionNode) => Any] = defaultOnMouseLeaveShape(editorState)

    override def onDivDraggedOver: Option[(MouseEvent, BeProgram,L.HtmlElement) => Any] = defaultOnDragEnteredOrMovedInDiv(editorState)
    override def onDivDragLeave: Option[(MouseEvent, BeProgram,L.HtmlElement) => Any] = defaultOnDragLeaveDiv(editorState)

    override def onDivDropped: Option[(DragEvent, BeProgram, L.HtmlElement) => Any] = Some((dragEvent, program, htmlElement) => {
   
      val rect = htmlElement.ref.getBoundingClientRect()
      val divPos = Point[Double](dragEvent.clientX - rect.left, dragEvent.clientY - rect.top)
      editorState.controllerStateVar.update(oldState => oldState.copy(unhandledDropEvents = Some(TreeDroppedEvent(program, divPos))))
    })

  }

  def defaultOnDragEnteredOrMovedInDiv(editorState: EditorState): Option[(MouseEvent, BeProgram, L.HtmlElement) => Any] = {
    Some(
      (mouseEvent: MouseEvent, beProgram: BeProgram, div: L.HtmlElement) => {
        val rect = div.ref.getBoundingClientRect()
        val divPos = Point[Double](mouseEvent.clientX - rect.left, mouseEvent.clientY - rect.top)
        val newState = Some(MouseOverProgram(beProgram, divPos))
        editorState.controllerStateVar.update(_.copy(mouseDragOverProgram = newState))
      }
    )
  }

  def defaultOnDragLeaveDiv(editorState: EditorState): Option[(MouseEvent, BeProgram, L.HtmlElement) => Any] = {
    Some(
      (mouseEvent: MouseEvent, beProgram: BeProgram, div: L.HtmlElement) => {
        editorState.controllerStateVar.update(_.copy(mouseDragOverProgram = None))
      }
    )
  }


  def defaultOnMouseEnterShape(editorState: EditorState): Option[(MouseEvent, BeProgram, BeExpressionNode) => Any] =
    Some(
      (mouseEvent: MouseEvent, program: BeProgram, exprNode: BeExpressionNode) =>
        editorState.controllerStateVar.update(oldVal => oldVal.copy(mouseOverExpression = Some(MouseOverExpression(program, exprNode))))
    )

  def defaultOnMouseLeaveShape(editorState: EditorState): Option[(MouseEvent, BeProgram, BeExpressionNode) => Any] =
    Some(
      (mouseEvent: MouseEvent, program: BeProgram, exprNode: BeExpressionNode) => exprNode match {
        case BeExtensionPoint(isRequired, childPosition, willBeUsedAsType) => {
          editorState.controllerStateVar.update(oldVal => oldVal.copy(mouseOverExpression = None))
        }
        case _ => {
        }
      }
    )


}
