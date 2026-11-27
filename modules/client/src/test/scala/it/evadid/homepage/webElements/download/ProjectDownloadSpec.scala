package it.evadid.homepage.webElements.download

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, Rust, SnapLanguage}
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.vm.simulation.data.DataWorld
import com.raquo.laminar.api.L.{Val, Var}
import it.evadid.homepage.webElements.editor.code.program.ProgrammingExecutionLabels
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateRaw, ProgrammingStateSnapScalaModel}
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

  test("a Snap project is saved as its XML file") {
    val recorder = Recorder()
    assert(ProjectDownload.download(ProgrammingStateSnapScalaModel.mini, SnapLanguage, "Hallo Welt", saver = recorder).isRight)
    assertEquals(recorder.saved.map(entry => (entry._1, entry._2)), Vector(("Hallo_Welt.xml", "application/xml")))
  }

  test("the download button saves the program in its current language and is disabled where none exists") {
    val recorder = Recorder()
    val state = Var[ProgrammingState](ProgrammingStateRaw(Python, "print(1)\n"))
    val labels = ProgrammingExecutionLabels(Val("console"), Val("run"), Val("stop"), Val("running"), Val("stopped"), Val("unavailable"), Val("Download"))
    val close = mount(ProjectDownloadButton(state.signal, labels, "brick-task", recorder, () => LocalDateTime.of(2026, 11, 27, 13, 5, 9)))
    val button = dom.document.querySelector(".programming-execution__download").asInstanceOf[dom.html.Button]
    try {
      button.click()
      assertEquals(recorder.saved.map(_._1), Vector("2026-11-27_13-05-09_brick-task.py"), "named after the time and the exercise id")
      state.set(ProgrammingStateRaw(Python, "forward(1)").toBeExpression)
      assert(button.disabled, "block display has no project format")
    } finally close()
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
      assertEquals(clicked.toSeq, Seq("Hallo_Welt.py|blob:"), "a single-file project is saved as that file")
      assertEquals(dom.document.querySelectorAll("a[download]").length, 0, "the link is removed again")
    } finally dom.document.removeEventListener("click", listener, useCapture = true)
  }
}
