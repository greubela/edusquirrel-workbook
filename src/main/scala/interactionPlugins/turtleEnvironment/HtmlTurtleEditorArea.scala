package interactionPlugins.turtleEnvironment

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import org.scalajs.dom.{DragEvent, html}
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

class HtmlTurtleEditorArea(
  program: TurtleBlockProgram,
  dragContext: TurtleBlockDragContext
) extends HtmlWorkbookElement {

  private def dropZone(insertIndex: Int): HtmlElement =
    div(
      cls := "turtle-drop-zone",
      onDragOver --> ((event: DragEvent) => event.preventDefault()),
      onDrop --> ((event: DragEvent) => {
        event.preventDefault()
        dragContext.consumePayload().foreach {
          case TurtleDragPayload.PaletteBlock(definition) =>
            val blocks = TurtleBlockLibrary.instantiateWithCompanion(definition)
            program.insertBlocks(insertIndex, blocks)
          case TurtleDragPayload.EditorBlockGroup(blocks, _, _) =>
            program.insertBlocks(insertIndex, blocks)
        }
      })
    )

  private def editorBlock(block: TurtleBlock, index: Int): HtmlElement = {
    val isRoot = block.command == TurtleCommand.WhenProgramStarted
    val parameterInput = block.definition.parameter.flatMap {
      case TurtleBlockParameter.Numeric(param) =>
        Some(
          input(
            cls := "turtle-block-parameter",
            typ := "number",
            minAttr := param.min.toString,
            maxAttr := param.max.toString,
            value := block.value.getOrElse(param.defaultValue).toInt.toString,
            onInput.mapToValue --> (value =>
              value.toDoubleOption.foreach(num => program.updateBlockValue(block.id, num))
            )
          )
        )
      case TurtleBlockParameter.Boolean(param) =>
        Some(
          input(
            cls := "turtle-block-parameter", 
            typ := "checkbox",
            checked := param.asBoolean(block.value),
            onInput.map { event =>
              val checkedValue = event.target match {
                case inputElem: html.Input => if (inputElem.checked) param.sanitize(1.0) else param.sanitize(0.0)
                case _                     => param.defaultNumeric
              }
              checkedValue
            } --> (value => program.updateBlockValue(block.id, value))
          )
        )
    }
    div(
      cls := "turtle-editor-block",
      draggable := (!isRoot),
      onDragStart --> ((event: DragEvent) => {
        if (!isRoot) {
          event.dataTransfer.setData("text/turtle-block", block.definition.key)
          val detached = program.detachFrom(index)
          dragContext.startEditorDrag(detached, index, () => program.insertBlocks(index, detached))
        } else {
          event.preventDefault()
        }
      }),
      onDragEnd --> (_ => dragContext.cancelDragIfNecessary()),
      onContextMenu.preventDefault --> (_ => if (!isRoot) program.removeBlock(block.id)),
      block.shape.render(block.label),
      parameterInput
    )
  }

  private val domElement =
    div(
      cls := "turtle-editor-area",
      children <-- program.blocksSignal.map { blocks =>
        val blockNodes = blocks.zipWithIndex.flatMap { case (block, idx) =>
          val zoneIndex = if (idx == 0) 1 else idx
          List(dropZone(zoneIndex), editorBlock(block, idx))
        }
        blockNodes :+ dropZone(blocks.length)
      }
    )

  override def getDomElement(): L.Element = domElement
}
