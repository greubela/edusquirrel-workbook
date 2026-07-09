package it.evadid.homepage.workbook.htmlRenderer.interactionEditors

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.*
import it.evadid.workbook.elements.interactionElements.reorderExercise.{ReorderInteraction, ReorderInteractionState, ReorderType}
import it.evadid.workbook.model.interaction.sync.UpdateImportance

/**
 * Renders core reorder interactions as drag-and-drop HTML controls.
 * The renderer maps the concrete interaction variant to the appropriate element view, while all order validation and state mutation rules remain in the core reorder model.
 */
object HtmlReorderInteractionRenderer extends LineBasedRenderingFactory[ReorderInteraction[?]] {

  /**
   * Creates the outer DOM element for a supported reorder interaction variant.
   * This method selects the item renderer for code lines or language-map content and delegates shared drag-and-drop wiring to the typed helper.
   */
  override protected def createRendering(workbookElement: ReorderInteraction[_]): AtomarLineRendering = {

    val cssStr: String = "reorder-interaction ${reorderTypeCssClass(interaction.defaultValue.elementType)}\",\n    "

    val dom: Element = workbookElement match {
      case interaction: ReorderInteraction.ReorderCodeInteraction =>
        createDomElementForTypedInteraction[String](
          interaction,
          line => pre(code(line)),
          itemCssClass = "reorder-item--code"
        )
      case interaction: ReorderInteraction.ReorderMapIdInteraction =>
        createDomElementForTypedInteraction[LanguageMapContentId](
          interaction,
          contentId => div(child.text <-- contentIdStringSignal(contentId)),
          itemCssClass = ""
        )
    }

    RenderingLine(true, dom, cssStr)
  }

  /**
   * Builds the shared drag-and-drop list for a typed reorder interaction.
   * UI-only details such as hover placeholders and mouse drop-index detection stay here, while the model state performs order sanitation and move application.
   */
  private def createDomElementForTypedInteraction[T](
                                                      interaction: ReorderInteraction[T],
                                                      elementRenderer: T => Element,
                                                      itemCssClass: String
                                                    ): Element = {
    val stateVar = interaction.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MINOR).toAirstreamVar
    val draggingId: Var[Option[Int]] = Var(None)
    val hoverIndex: Var[Option[Int]] = Var(None)

    def dropIndex(container: org.scalajs.dom.html.Div, mouseY: Double): Int = {
      val items = container.querySelectorAll(".reorder-item")
      var newIndex = items.length
      var found = false
      var i = 0
      while (i < items.length && !found) {
        val rect = items.item(i).asInstanceOf[org.scalajs.dom.html.Div].getBoundingClientRect()
        val middleY = rect.top + (rect.height / 2)
        if (mouseY < middleY) {
          newIndex = i
          found = true
        }
        i += 1
      }
      newIndex
    }

    def renderItem(state: ReorderInteractionState[T], itemId: Int): Element = {
      val content = elementRenderer(state.elements(itemId))
      val baseCls = if (itemCssClass.nonEmpty) s"reorder-item $itemCssClass" else "reorder-item"

      div(
        cls := baseCls,
        cls("reorder-item--dragging") <-- draggingId.signal.map(_.contains(itemId)),
        draggable := true,
        onDragStart --> (_ => draggingId.set(Some(itemId))),
        onDragEnd --> (_ => {
          draggingId.set(None)
          hoverIndex.set(None)
        }),
        content
      )
    }

    div(
      cls := "reorder-list",
      onDragOver.preventDefault --> { e =>
        val container = e.currentTarget.asInstanceOf[org.scalajs.dom.html.Div]
        hoverIndex.set(Some(dropIndex(container, e.clientY)))
      },
      onDrop.preventDefault --> { e =>
        val container = e.currentTarget.asInstanceOf[org.scalajs.dom.html.Div]
        val targetIdx = dropIndex(container, e.clientY)
        draggingId.now() match {
          case Some(idToMove) =>
            stateVar.update(_.moveElementToIndex(idToMove, targetIdx))
          case _ =>
        }
        draggingId.set(None)
        hoverIndex.set(None)
      },
      children <-- stateVar.signal.combineWith(hoverIndex.signal).map {
        case (state, hover) =>
          val visible = state.sanitizedCurrentOrder.map(renderItem(state, _))

          hover match {
            case Some(index) =>
              val safe = index.max(0).min(visible.length)
              val (front, back) = visible.splitAt(safe)
              front ++ List(div(cls := "drop-placeholder")) ++ back
            case None =>
              visible
          }
      }
    )
  }

  /**
   * Converts the core reorder type into a stable CSS hook for styling.
   * This keeps CSS naming decisions at the renderer boundary while avoiding duplicated type checks in the DOM construction code.
   */
  private def reorderTypeCssClass(reorderType: ReorderType): String = reorderType match {
    case ReorderType.CODELINES(_) => "reorder-interaction--code"
    case ReorderType.LANGUAGE_MAP_IDS => "reorder-interaction--language-map"
    case ReorderType.BASIC_STRINGS => "reorder-interaction--basic"
  }

}
