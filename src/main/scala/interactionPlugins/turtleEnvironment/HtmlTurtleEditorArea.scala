package interactionPlugins.turtleEnvironment

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import org.scalajs.dom.{DataTransferDropEffectKind, DataTransferEffectAllowedKind, DragEvent, html}
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

class HtmlTurtleEditorArea(
  program: TurtleBlockProgram,
  dragContext: TurtleBlockDragContext
) extends HtmlWorkbookElement {

  import TurtlePathSegment.*

  private enum TurtleDropZoneKind(val cssClass: String) {
    case Below extends TurtleDropZoneKind("below")
    case Inside extends TurtleDropZoneKind("inside")
    case Parameter extends TurtleDropZoneKind("parameter")
  }

  private def isCommandBlock(node: TurtleStructuredBlock): Boolean =
    node.block.definition.behaviour.isInstanceOf[TurtleBlockBehaviour.Command]

  private def isReporterBlock(node: TurtleStructuredBlock, valueType: TurtleValueType): Boolean =
    node.block.definition.behaviour match {
      case TurtleBlockBehaviour.Reporter(vt, _) => vt == valueType
      case _                                    => false
    }

  private def canAcceptStackDrop(payload: TurtleDragPayload): Boolean = payload match {
    case TurtleDragPayload.PaletteBlock(definition) =>
      definition.behaviour.isInstanceOf[TurtleBlockBehaviour.Command] && definition.key != TurtleBlockLibrary.whenProgramStarted.key
    case TurtleDragPayload.EditorBlockGroup(blocks, _, _) => blocks.forall(isCommandBlock)
  }

  private def canAcceptSocketDrop(socket: TurtleBlockSocketDefinition, payload: TurtleDragPayload): Boolean = payload match {
    case TurtleDragPayload.PaletteBlock(definition) =>
      definition.behaviour match {
        case TurtleBlockBehaviour.Reporter(valueType, _) => valueType == socket.valueType
        case _                                           => false
      }
    case TurtleDragPayload.EditorBlockGroup(blocks, _, _) =>
      blocks.nonEmpty && blocks.forall(isReporterBlock(_, socket.valueType))
  }

  private def stackDropZone(path: List[TurtlePathSegment], insertIndex: Int, kind: TurtleDropZoneKind): HtmlElement = {
    val isActive = Var(false)
    div(
      className := s"turtle-drop-zone turtle-drop-zone--${kind.cssClass}",
      cls.toggle("active") <-- isActive.signal,
      onDragEnter --> ((event: DragEvent) =>
        dragContext.peek match {
          case Some(payload) if canAcceptStackDrop(payload) =>
            event.preventDefault()
            isActive.set(true)
          case _ => ()
        }
      ),
      onDragOver --> ((event: DragEvent) =>
        dragContext.peek match {
          case Some(payload) if canAcceptStackDrop(payload) =>
            event.preventDefault()
            Option(event.dataTransfer).foreach { dataTransfer =>
              dataTransfer.dropEffect = payload match {
                case TurtleDragPayload.EditorBlockGroup(_, _, _) => DataTransferDropEffectKind.move
                case _                                           => DataTransferDropEffectKind.copy
              }
            }
            isActive.set(true)
          case _ => ()
        }
      ),
      onDragLeave --> (_ => isActive.set(false)),
      onDrop --> ((event: DragEvent) => {
        event.preventDefault()
        dragContext.consumePayload() match {
          case Some(TurtleDragPayload.PaletteBlock(definition)) =>
            val blocks = TurtleBlockLibrary.instantiateWithCompanion(definition)
            program.insertBlocks(path, insertIndex, blocks)
          case Some(TurtleDragPayload.EditorBlockGroup(blocks, _, _)) =>
            program.insertBlocks(path, insertIndex, blocks)
          case None => ()
        }
        isActive.set(false)
      })
    )
  }

  private def socketTarget(
    path: List[TurtlePathSegment],
    socket: TurtleBlockSocketDefinition,
    childrenNodes: List[TurtleStructuredBlock]
  ): HtmlElement = {
    val isActive = Var(false)
    val targetClasses = s"turtle-parameter-target turtle-parameter-target--${socket.valueType.toString.toLowerCase()}"
    val renderedChildren =
      if (childrenNodes.nonEmpty)
        childrenNodes.zipWithIndex.map { case (child, idx) => renderReporter(path, child, idx) }
      else
        List(div(cls := "turtle-parameter-placeholder", s"drop ${socket.label}"))

    div(
      cls := "turtle-parameter-slot",
      span(cls := "turtle-parameter-label", socket.label),
      div(
        className := targetClasses,
        cls.toggle("active") <-- isActive.signal,
        styleAttr := s"--socket-color: ${socket.color}",
        onDragEnter --> ((event: DragEvent) =>
          dragContext.peek match {
            case Some(payload) if canAcceptSocketDrop(socket, payload) =>
              event.preventDefault()
              isActive.set(true)
            case _ => ()
          }
        ),
        onDragOver --> ((event: DragEvent) =>
          dragContext.peek match {
            case Some(payload) if canAcceptSocketDrop(socket, payload) =>
              event.preventDefault()
              Option(event.dataTransfer).foreach { dataTransfer =>
                dataTransfer.dropEffect = payload match {
                  case TurtleDragPayload.EditorBlockGroup(_, _, _) => DataTransferDropEffectKind.move
                  case _                                           => DataTransferDropEffectKind.copy
                }
              }
              isActive.set(true)
            case _ => ()
          }
        ),
        onDragLeave --> (_ => isActive.set(false)),
        onDrop --> ((event: DragEvent) => {
          event.preventDefault()
          dragContext.consumePayload() match {
            case Some(TurtleDragPayload.PaletteBlock(definition)) =>
              val blocks = TurtleBlockLibrary.instantiateWithCompanion(definition)
              program.insertBlocks(path, 0, blocks)
            case Some(TurtleDragPayload.EditorBlockGroup(blocks, _, _)) =>
              program.insertBlocks(path, 0, blocks)
            case None => ()
          }
          isActive.set(false)
        }),
        renderedChildren
      )
    )
  }

  private def literalEditor(block: TurtleBlock): Option[HtmlElement] = block.definition.key match {
    case "numericLiteral" =>
      val currentValue = block.value.getOrElse(0.0)
      Some(
        input(
          cls := "turtle-reporter-input",
          typ := "number",
          value := currentValue.toString,
          onInput.mapToValue --> (value => value.toDoubleOption.foreach(num => program.updateBlockValue(block.id, num)))
        )
      )
    case "booleanLiteral" =>
      val checkedValue = block.booleanValue(default = true)
      Some(
        label(
          cls := "turtle-reporter-toggle",
          input(
            cls := "turtle-reporter-checkbox",
            typ := "checkbox",
            checked := checkedValue,
            onInput.map { event =>
              event.target match {
                case inputElem: html.Input => if (inputElem.checked) 1.0 else 0.0
                case _                     => if (checkedValue) 1.0 else 0.0
              }
            } --> (value => program.updateBlockValue(block.id, value))
          ),
          span(cls := "turtle-reporter-toggle-label", if (checkedValue) "true" else "false")
        )
      )
    case _ => None
  }

  private def renderReporter(
    path: List[TurtlePathSegment],
    node: TurtleStructuredBlock,
    index: Int
  ): HtmlElement = {
    val block = node.block
    val sockets = block.definition.sockets
    val socketElements = sockets.map { socket =>
      val childPath = path :+ TurtlePathSegment.IntoSocket(block.id, socket.id)
      socketTarget(childPath, socket, node.socketContent(socket.id))
    }
    val valueEditor = literalEditor(block).toList
    div(
      cls := "turtle-reporter-wrapper",
      div(
        cls := "turtle-reporter-block",
        draggable := true,
        onDragStart --> ((event: DragEvent) => {
          Option(event.dataTransfer).foreach { dataTransfer =>
            dataTransfer.effectAllowed = DataTransferEffectAllowedKind.move
            dataTransfer.setData("text/turtle-block", block.definition.key)
          }
          val detached = program.detachFrom(path, index)
          dragContext.startEditorDrag(detached, path, () => program.insertBlocks(path, index, detached))
        }),
        onDragEnd --> (_ => dragContext.cancelDragIfNecessary()),
        onContextMenu.preventDefault --> (_ => program.detachFrom(path, index)),
        div(cls := "turtle-block-shape", block.definition.shape.render(block.label)),
        valueEditor,
        socketElements
      )
    )
  }

  private def renderBlock(path: List[TurtlePathSegment], node: TurtleStructuredBlock, index: Int): HtmlElement = {
    val block = node.block
    val isRoot = block.definition.key == TurtleBlockLibrary.whenProgramStarted.key
    val insidePath = path :+ TurtlePathSegment.IntoBlock(block.id)
    val insideArea =
      if (block.definition.supportsArea(TurtleBlockArea.Inside)) {
        val childNodes = renderStack(insidePath, node.inside, skipFirstDrop = false, TurtleDropZoneKind.Inside)
        Some(div(cls := "turtle-block-children", childNodes))
      } else None
    val socketElements = block.definition.sockets.map { socket =>
      val socketPath = path :+ TurtlePathSegment.IntoSocket(block.id, socket.id)
      socketTarget(socketPath, socket, node.socketContent(socket.id))
    }
    div(
      cls := "turtle-editor-branch",
      div(
        cls := "turtle-editor-block",
        draggable := (!isRoot),
        onDragStart --> ((event: DragEvent) => {
          if (!isRoot) {
            Option(event.dataTransfer).foreach { dataTransfer =>
              dataTransfer.effectAllowed = DataTransferEffectAllowedKind.move
              dataTransfer.setData("text/turtle-block", block.definition.key)
            }
            val detached = program.detachFrom(path, index)
            dragContext.startEditorDrag(detached, path, () => program.insertBlocks(path, index, detached))
          } else {
            event.preventDefault()
          }
        }),
        onDragEnd --> (_ => dragContext.cancelDragIfNecessary()),
        onContextMenu.preventDefault --> (_ => if (!isRoot) program.removeBlock(block.id)),
        div(cls := "turtle-block-shape", block.definition.shape.render(block.label)),
        socketElements
      ),
      insideArea.toList
    )
  }

  private def renderStack(
    path: List[TurtlePathSegment],
    blocks: List[TurtleStructuredBlock],
    skipFirstDrop: Boolean,
    kind: TurtleDropZoneKind
  ): Seq[HtmlElement] = {
    val elements = scala.collection.mutable.ListBuffer.empty[HtmlElement]
    for (idx <- 0 to blocks.length) {
      val allowDrop =
        if (skipFirstDrop && idx == 0) false
        else if (idx == 0) true
        else blocks.lift(idx - 1).forall(_.block.definition.supportsArea(TurtleBlockArea.Below))
      if (allowDrop) {
        elements += stackDropZone(path, idx, kind)
      }
      if (idx < blocks.length) {
        elements += renderBlock(path, blocks(idx), idx)
      }
    }
    elements.toList
  }

  private val domElement =
    div(
      cls := "turtle-editor-area",
      children <-- program.blocksSignal.map { blocks =>
        renderStack(program.rootPath, blocks, skipFirstDrop = true, TurtleDropZoneKind.Below)
      }
    )

  override def getDomElement(): L.Element = domElement
}
