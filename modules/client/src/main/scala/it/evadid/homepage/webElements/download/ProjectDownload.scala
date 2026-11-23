package it.evadid.homepage.webElements.download

import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.vm.projectexport.{ExportedProject, ProjectExport}
import it.evadid.vm.simulation.data.DataWorld
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import org.scalajs.dom

import java.time.LocalDateTime
import scala.scalajs.js
import scala.scalajs.js.typedarray.*

/**
 * Saves programs from the editors as a ZIP the student can open in another IDE (BlueJ for Java).
 * The browser steps are injectable so the flow can be tested without a real download.
 */
object ProjectDownload {
  /** Starts a browser download of `bytes` named `fileName`. */
  trait Saver {
    def save(fileName: String, mimeType: String, bytes: Array[Byte]): Unit
  }

  /** Blob URL plus a temporary `<a download>` link, the standard browser download path. */
  object BrowserSaver extends Saver {
    override def save(fileName: String, mimeType: String, bytes: Array[Byte]): Unit = {
      val blob = new dom.Blob(js.Array(bytes.toTypedArray.buffer), new dom.BlobPropertyBag { `type` = mimeType })
      val url = dom.URL.createObjectURL(blob)
      val link = dom.document.createElement("a").asInstanceOf[dom.HTMLAnchorElement]
      link.href = url
      link.setAttribute("download", fileName)
      link.style.display = "none"
      dom.document.body.appendChild(link)
      try link.click()
      finally {
        link.remove()
        // Revoke after the click has been handled, so the download can still read the blob.
        js.timers.setTimeout(0)(dom.URL.revokeObjectURL(url))
      }
    }
  }

  /** Exports `state` in the format of `language` and saves it; Left explains why nothing was saved. */
  def download(state: ProgrammingState, language: ProgrammingLanguage, title: String, world: DataWorld = DataWorld(),
      saver: Saver = BrowserSaver, now: () => LocalDateTime = () => LocalDateTime.now()): Either[String, ExportedProject] =
    ProjectExport.forLanguage(state, language, title, world).map { project =>
      saver.save(project.fileName, "application/zip", project.zip(now()))
      project
    }
}
