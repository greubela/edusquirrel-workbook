package interactionPlugins.turtleEnvironment

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import org.scalajs.dom.DataTransferEffectAllowedKind
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

class HtmlBlockDragFromArea(
  dragContext: TurtleBlockDragContext,
  availableBlocks: Map[TurtleBlockCategory, List[TurtleBlockDefinition]],
  onBlockRequested: TurtleBlockDefinition => Unit
) extends HtmlWorkbookElement {

  private val categories = TurtleBlockCategory.values.toList
  private val activeCategoryVar: Var[TurtleBlockCategory] = Var(categories.headOption.getOrElse(TurtleBlockCategory.Control))

  private def tabButton(category: TurtleBlockCategory) =
    button(
      cls := "turtle-tab-button",
      typ := "button",
      onClick.mapTo(category) --> activeCategoryVar.writer,
      cls.toggle("active") <-- activeCategoryVar.signal.map(_ == category),
      child.text <-- activeCategoryVar.signal.map(current =>
        if (current == category) s"★ ${category.toString}" else category.toString
      )
    )

  private def paletteBlock(definition: TurtleBlockDefinition): HtmlElement = {
    val preview = definition.createInstance()
    val previewHeight = preview.definition.shape.computeHeight(0.0)
    div(
      cls := "turtle-palette-block",
      draggable := true,
      onDragStart --> { event =>
        dragContext.startPaletteDrag(definition) 
        Option(event.dataTransfer).foreach { dataTransfer =>
          dataTransfer.effectAllowed = DataTransferEffectAllowedKind.copy //"copy"
          dataTransfer.setData("text/turtle-block", definition.key)
        }
      },
      onDragEnd --> (_ => dragContext.consumePayload()),
      onDblClick --> (_ => onBlockRequested(definition)),
      preview.definition.shape.render(preview.label, previewHeight)
    )
  }

  private val domElement =
    div(
      cls := "turtle-block-drag-area",
      div(
        cls := "turtle-tab-pane",
        categories.map(tabButton)
      ),
      div(
        cls := "turtle-block-list",
        children <-- activeCategoryVar.signal.map { category =>
          availableBlocks.getOrElse(category, Nil).map(paletteBlock)
        }
      )
    )

  override def getDomElement(): L.Element = domElement
}
