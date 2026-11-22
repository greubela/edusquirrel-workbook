package it.evadid.homepage.webElements.download

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, Rust}
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.vm.simulation.data.DataWorld
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw
import org.scalajs.dom

import java.time.LocalDateTime
import scala.scalajs.js

class ProjectDownloadSpec extends EditorDomSuite {
  private final class Recorder extends ProjectDownload.Saver {
    val calls = com.raquo.airstream.state.Var(Vector.empty[(String, String, Int)])
    def saved: Vector[(String, String, Int)] = calls.now()
    override def save(fileName: String, mimeType: String, bytes: Array[Byte]): Unit = calls.update(_ :+ ((fileName, mimeType, bytes.length)))
  }
  private val program = ProgrammingStateRaw(Java, "public class Main {\n    public static void main(String[] args) {\n        NumberList.swap(0, 1);\n    }\n}")

  test("downloads the exported project as a ZIP named after the project") {
    val recorder = Recorder()
    val result = ProjectDownload.download(program, Java, "Tauschen üben", DataWorld(list = Vector(2, 1)), recorder,
      () => LocalDateTime.of(2026, 11, 20, 8, 0))
    assertEquals(result.map(_.folder), Right("Tauschen_ueben"))
    assertEquals(recorder.saved.map(entry => (entry._1, entry._2)), Vector(("Tauschen_ueben.zip", "application/zip")))
    assert(recorder.saved.head._3 > 100)
    assertEquals(result.toOption.get.files.map(_.path).toSet, Set("Main.java", "NumberList.java", "package.bluej", "README.TXT"))
  }

  test("nothing is saved when the project cannot be exported") {
    val recorder = Recorder()
    assert(ProjectDownload.download(program, Rust, "x", saver = recorder).isLeft)
    assertEquals(recorder.saved, Vector.empty)
  }

  test("the browser saver clicks a temporary download link for a blob and removes it") {
    val clicked = js.Array[String]()
    val listener: js.Function1[dom.Event, Unit] = event => {
      val link = event.target.asInstanceOf[dom.HTMLAnchorElement]
      clicked.push(s"${link.getAttribute("download")}|${link.href.take(5)}")
      event.preventDefault()
    }
    dom.document.addEventListener("click", listener, useCapture = true)
    try {
      ProjectDownload.download(ProgrammingStateRaw(Python, "print(1)\n"), Python, "Hallo Welt")
      assertEquals(clicked.toSeq, Seq("Hallo_Welt.zip|blob:"))
      assertEquals(dom.document.querySelectorAll("a[download]").length, 0, "the link is removed again")
    } finally dom.document.removeEventListener("click", listener, useCapture = true)
  }
}
