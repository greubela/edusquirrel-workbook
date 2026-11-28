package it.evadid.homepage.webElements.download


import it.evadid.core.datastructures.language.AppLanguage.{Java, ProgrammingLanguage, Python, SnapLanguage}
import it.evadid.vm.projectexport.{ExportedProject, ProjectExport}
import it.evadid.vm.simulation.data.DataWorld
import it.evadid.workbook.interactions.programming.state.ProgrammingState
import org.scalajs.dom

import java.time.Instant
import scala.scalajs.js
import scala.scalajs.js.typedarray.*

/**
 * Saves programs from the editors in a form the student can open elsewhere: a single-file project as that file
 * (Snap XML, a Python script), any other as a ZIP (a BlueJ project for Java).
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

  def supports(language: ProgrammingLanguage): Boolean = Seq(Java, Python, SnapLanguage).contains(language)

  private val mimeTypes = Map("py" -> "text/x-python", "xml" -> "application/xml")

  /** Exports `state` in the format of `language` and saves it; Left explains why nothing was saved. */
  def download(state: ProgrammingState, language: ProgrammingLanguage, title: String, world: DataWorld = DataWorld(),
      saver: Saver = BrowserSaver, now: () => Instant = () => Instant.now()): Either[String, ExportedProject] =
    ProjectExport.forLanguage(state, language, title, world).map { project =>
      project.files match {
        case Seq(file) =>
          val extension = file.path.substring(file.path.lastIndexOf('.') + 1)
          saver.save(s"${project.folder}.$extension", mimeTypes.getOrElse(extension, "text/plain"),
            file.content.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        case _ => saver.save(project.fileName, "application/zip", project.zip(now()))
      }
      project
    }
}
