package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.codeTaskToggle

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.InteractionVariableOnJS
import it.evadid.homepage.control.singletons.FileStore
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.codeTaskToggle.SketchDownloadInteraction
import it.evadid.workbook.elements.interactionElements.reorderExercise.{ReorderCorrectness, ReorderInteraction}

object HtmlSketchDownloadRenderer extends LineBasedRenderingFactory[SketchDownloadInteraction] {

  override protected def createRendering(download: SketchDownloadInteraction): AtomarLineRendering = {
    val enabledSignal = fullInfo.signals.workbook.flatMapSwitch {
      case Some(workbookInfo) =>
        workbookInfo.loadedWorkbook.allContainedInteractionsById.get(download.unlockWhenReorderCorrect) match {
          case Some(reorder: ReorderInteraction[?]) =>
            reorder.interactionVariable
              .createInteractionSignal(fullInfo.syncControl)
              .map(state => ReorderCorrectness.isOrderCorrect(reorder, state))
          case _ =>
            Val(false)
        }
      case None =>
        Val(false)
    }

    val dom = div(
      cls := "download-btn-wrapper",
      button(
        cls := "btn-primary",
        disabled <-- enabledSignal.map(enabled => !enabled),
        text <-- laminarHelper.plaintextStringSignal(download.buttonLabel),
        onClick --> { _ => fullInfo.fileStore.downloadFile(download.filename, download.sketchContent) }
      )
    )

    AtomarLineRendering.basicLine(download, dom, "sketch-download")
  }

}
