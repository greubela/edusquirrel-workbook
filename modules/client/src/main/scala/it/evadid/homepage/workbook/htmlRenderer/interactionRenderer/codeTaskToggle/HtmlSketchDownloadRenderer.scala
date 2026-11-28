package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.codeTaskToggle

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.*

import it.evadid.core.util.InfoUtil
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.interactions.codeTaskToggle.SketchDownloadInteraction
import it.evadid.workbook.interactions.reorderExercise.ReorderInteraction

import java.time.LocalDateTime

object HtmlSketchDownloadRenderer extends LineBasedRenderingFactory[SketchDownloadInteraction] {

  override protected def createRendering(fullInfo: FullInfo, download: SketchDownloadInteraction): AtomarLineRendering = {
    val enabledSignal = fullInfo.signals.workbook.flatMapSwitch {
      case Some(workbookInfo) =>
        workbookInfo.loadedWorkbook.allContainedInteractionsById.get(download.unlockWhenReorderCorrect) match {
          case Some(reorder: ReorderInteraction.ReorderCodeInteraction) =>
            fullInfo.reporters.reportersFor(reorder).grading.summary.map(_.status.isCorrect)
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
        text <-- laminarHelper(fullInfo).plaintextStringSignal(download.buttonLabel),
        onClick --> { _ => fullInfo.contentControl.downloadToDisc.downloadFile(HtmlSketchDownloadRenderer.fileName(download), download.sketchContent) }
      )
    )

    AtomarLineRendering.basicLine(fullInfo, download, dom, "sketch-download")
  }

  /** The sketch's own name, dated like every download (`2026-11-27_13-05-09_sensor-auslesen.ino`). */
  def fileName(download: SketchDownloadInteraction, now: LocalDateTime = LocalDateTime.now()): String = {
    val name = download.filenameRelativeToWorkbookResources.split('/').last
    val dot = name.lastIndexOf('.')
    if dot > 0 then InfoUtil.downloadName(name.take(dot), now) + name.drop(dot) else InfoUtil.downloadName(name, now)
  }
}
