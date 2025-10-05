package interactionPlugins.blockEnvironment.secondInteration

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import interactionPlugins.blockEnvironment.programming.rendering.{BeProgramRenderer, BeRendererConfig}
import org.scalajs.dom.DataTransferEffectAllowedKind
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

class HtmlBeBlockPalette(
  paletteModel: BeBlockPaletteModel,
  rendererConfig: BeRendererConfig,
  dragContext: BeDragContext,
  onEntryRequested: BePaletteEntry => Unit
) extends HtmlWorkbookElement {

  private val categories = paletteModel.categories
  private val activeCategory: Var[Option[BePaletteCategory]] = Var(categories.headOption)

  private def tabButton(category: BePaletteCategory): HtmlElement =
    button(
      cls := "be-palette-tab-button",
      typ := "button",
      onClick.mapTo(Some(category)) --> activeCategory.writer,
      cls.toggle("active") <-- activeCategory.signal.map(_.contains(category)),
      child.text := category.label
    )

  private def paletteEntryElement(entry: BePaletteEntry): HtmlElement = {
    val previewProgram = paletteModel.previewProgramFor(entry)
    val previewCanvas = BeProgramRenderer(previewProgram, rendererConfig).render()

    div(
      cls := "be-palette-entry",
      draggable := true,
      onDragStart --> { event =>
        dragContext.startPaletteDrag(entry)
        Option(event.dataTransfer).foreach { dataTransfer =>
          dataTransfer.effectAllowed = DataTransferEffectAllowedKind.copy
          dataTransfer.setData("text/be-block", entry.id)
        }
      },
      onDragEnd --> (_ => dragContext.cancelDrag()),
      onDblClick --> (_ => onEntryRequested(entry)),
      div(
        cls := "be-palette-entry-preview",
        previewCanvas.getDomElement()
      ),
      div(
        cls := "be-palette-entry-label",
        entry.label
      )
    )
  }

  private val domElement =
    div(
      cls := "be-block-palette",
      div(
        cls := "be-palette-tabs",
        categories.map(tabButton)
      ),
      div(
        cls := "be-palette-content",
        children <-- activeCategory.signal.map {
          case Some(category) => paletteModel.entriesFor(category).map(paletteEntryElement)
          case None => Nil
        }
      )
    )

  override def getDomElement(): L.Element = domElement
}
