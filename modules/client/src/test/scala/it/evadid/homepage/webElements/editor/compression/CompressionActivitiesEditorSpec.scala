package it.evadid.homepage.webElements.editor.compression

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.UiDomSuite
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.workbook.interactions.compression.CompressionExperimentInteraction
import it.evadid.workbook.interactions.compression.*
import org.scalajs.dom
import scala.concurrent.ExecutionContext
import scala.scalajs.js

/** The inline compression activities: written answers, reflections, bit flips, file inspection, transfers and the file simulator. */
class CompressionActivitiesEditorSpec extends UiDomSuite {
  private given ExecutionContext = ExecutionContext.global
  private def src(key: String) = loadingText("CompressionWorkbook/src_" + key.replace('.', '_'))
  private def text(key: String) = loadingText(s"CompressionWorkbook/$key")
  private def activity[T <: CompressionExperiment](initial: T, locked: Signal[Boolean] = Val(false)): (Var[CompressionExperiment], Element) = {
    val answer = Var[CompressionExperiment](initial)
    answer -> CompressionActivitiesEditor(pageInfo, answer, initial, locked).getDomElement()
  }

  test("a written answer is saved as typed; in code answers Tab indents instead of leaving the field") {
    val (answer, element) = activity(WrittenAnswer(code = true))
    mounted(element) { root =>
      val area = one(root, "textarea").asInstanceOf[dom.html.TextArea]
      assert(area.classList.contains("compression-code"))
      typeInto(area, "ifx")
      assertEquals(answer.now(), WrittenAnswer("ifx", code = true))
      area.setSelectionRange(2, 2)
      val tab = js.Dynamic.newInstance(EditorDomSuite.window.KeyboardEvent)("keydown", js.Dynamic.literal(key = "Tab", bubbles = true, cancelable = true))
      area.dispatchEvent(tab.asInstanceOf[dom.Event])
      assert(tab.defaultPrevented.asInstanceOf[Boolean])
      assertEquals(answer.now(), WrittenAnswer("if  x", code = true))
      assertEquals(area.selectionStart, 4)
    }
    val (prose, proseElement) = activity(WrittenAnswer())
    mounted(proseElement) { root =>
      val tab = js.Dynamic.newInstance(EditorDomSuite.window.KeyboardEvent)("keydown", js.Dynamic.literal(key = "Tab", bubbles = true, cancelable = true))
      one(root, "textarea").dispatchEvent(tab.asInstanceOf[dom.Event])
      assert(!tab.defaultPrevented.asInstanceOf[Boolean], "prose answers keep normal keyboard navigation")
      assertEquals(prose.now(), WrittenAnswer())
    }
  }

  test("an ethics choice reveals the matching follow-up question whose reason is saved") {
    val (answer, element) = activity(EthicalReflection())
    mounted(element) { root =>
      assertEquals(all(root, "textarea").size, 0)
      val radios = all(root, "input[type=radio]")
      click(radios(1))
      assertEquals(answer.now(), EthicalReflection(Some(1)))
      assert(root.textContent.contains(src("intro.ethicsFollowupNo")))
      typeInto(one(root, "textarea"), "privacy matters")
      assertEquals(answer.now(), EthicalReflection(Some(1), "privacy matters"))
      click(radios(2))
      assert(root.textContent.contains(src("intro.ethicsFollowupMaybe")))
      assertEquals(answer.now().asInstanceOf[EthicalReflection].reason, "privacy matters", "the reason is kept")
    }
  }

  test("flipping bits in the password and in the image counts each flip and redraws the image") {
    val canvas = FakeCanvas(128, 128, Seq.fill(128 * 128)((0, 0, 0)))
    canvas.install()
    try {
      val (answer, element) = activity(BitComparison(TextBits("Ab")))
      mounted(element) { root =>
        def state = answer.now().asInstanceOf[BitComparison]
        assertEquals(one(root, "pre").textContent, "01000001 01100010")
        val List(flipPassword, flipImage) = withText(root, "button", src("widget.bitflip.flipButton")): @unchecked
        click(flipPassword)
        assertEquals(state.password.flippedBit, Some(47 % 16))
        assertEquals(state.passwordFlips, 1)
        assertEquals(one(root, "pre").textContent, "01000001 01100011")
        assert(root.textContent.contains(text("bitsUnchanged")))
        val before = canvas.lastRaster
        assertEquals(before.size, 128 * 128)
        assertEquals(before(8192), (255, 255, 255), "outside the ring the image is white")
        click(flipImage)
        assertEquals(state.imageBit, Some(8192))
        assert(root.textContent.contains(text("bitChangedAt")))
        val after = canvas.lastRaster
        assertEquals(after.zip(before).count(_ != _), 1, "exactly one pixel changes")
        assertEquals(after(8192), (0, 0, 0), "the flipped pixel turns dark")
      }
    } finally canvas.uninstall()
  }

  test("inspecting a document opens and closes the parts of the docx archive") {
    val (answer, element) = activity(FileInspection("Bericht", formattedContent = Some(LanguageMapContentId("CompressionWorkbook/docx"))))
    mounted(element) { root =>
      assertEquals(one(root, "pre").textContent, "Bericht")
      val details = all(root, "details")
      assertEquals(details.size, 6)
      details(4).asInstanceOf[js.Dynamic].open = true
      dispatch(details(4), "toggle")
      assertEquals(answer.now().asInstanceOf[FileInspection].expanded, Seq("word/document.xml"))
      details(4).asInstanceOf[js.Dynamic].open = false
      dispatch(details(4), "toggle")
      assertEquals(answer.now().asInstanceOf[FileInspection].expanded, Nil)
      answer.set(FileInspection("Bericht", expanded = Seq("_rels/.rels")))
      assert(details(1).asInstanceOf[js.Dynamic].open.asInstanceOf[Boolean], "restored answers reopen their parts")
    }
  }

  test("a transfer simulation steps through every file and the archive, by hand and automatically") {
    val files = Seq("a.txt" -> "aaaa", "b.txt" -> "bb")
    val (answer, element) = activity(TransferSimulation(files))
    def step = answer.now().asInstanceOf[TransferSimulation].step
    mountedAsync(element) { root =>
      def phases = all(root, "p").filter(p => p.textContent.startsWith("a.txt") || p.textContent.startsWith("b.txt") || p.textContent.startsWith("beweise.zip"))
        .map(_.textContent)
      assert(phases.head.contains(text("transferAllocate")), phases.toString)
      assert(phases.tail.forall(_.contains(text("transferReady"))), phases.toString)
      click(buttonNamed(root, text("nextStep")))
      assertEquals(step, 1)
      assert(phases.head.contains(text("transferOpen")))
      assert(phases(2).contains(text("transferAllocate")))
      click(buttonNamed(root, src("widget.zipArchive.simulateButton")))
      eventually(step == 8, attempts = 1000, clue = "automatic transfer").map { _ =>
        assert(phases.take(2).forall(_.contains(text("transferDone"))), phases.toString)
        click(buttonNamed(root, text("nextStep")))
        assertEquals(step, 8, "the last step is final")
      }
    }
  }

  private val photos = FileSimulation("photos", Seq(
    SimulationFile("report.docx", 1000, "document", Seq("Autor: Unbekannt")),
    SimulationFile("holiday.raw", 4000, "image", Seq("GPS: 47.1, 8.3")),
    SimulationFile("log.txt", 500, "text", count = 3)), tutorial = true)

  test("the file simulator applies tools to clicked files, reports problematic metadata and archives a selection") {
    val (answer, element) = activity(photos)
    def state = answer.now().asInstanceOf[FileSimulation]
    mounted(element) { root =>
      def fileButtons = all(root, "tbody button")
      def tutorial = all(root, "ul li").map(_.textContent.take(2))
      assertEquals(tutorial, List("○ ", "○ ", "○ ", "○ "))
      assert(root.textContent.contains(src("widget.filesystemSimulator.noToolHint")))
      dispatch(fileButtons.head, "mouseover")
      assert(state.hovered)
      click(fileButtons.head)
      assertEquals(state.steps, 0, "without a tool a click does nothing")
      click(buttonNamed(root, src("widget.filesystemSimulator.tools.convert.label")))
      assertEquals(state.tool, "convert")
      assert(root.textContent.contains(src("widget.filesystemSimulator.tools.convert.hint")))
      click(fileButtons.head)
      assertEquals(state.files.head.name, "report.txt")
      assertEquals(state.steps, 1)
      assert(root.textContent.contains(src("widget.filesystemSimulator.metadataYes")), "the GPS position is still there")
      assertEquals(all(root, "td.compression-error").size, 1)
      assert(root.textContent.contains(text("groupFileCount")))

      click(buttonNamed(root, src("widget.filesystemSimulator.tools.archive.label")))
      val archive = buttonNamed(root, src("widget.filesystemSimulator.archiveCreate"))
      assert(isDisabled(archive))
      click(fileButtons.head)
      click(fileButtons(1))
      assertEquals(state.selected, Seq(0, 1))
      assertEquals(fileButtons(1).getAttribute("aria-pressed"), "true")
      click(archive)
      assert(state.error, "the photo's GPS data blocks archiving")
      assert(root.textContent.contains(src("widget.filesystemSimulator.archiveMetadataError")))
      click(fileButtons(1))
      click(fileButtons(2))
      click(archive)
      assertEquals(state.files.map(_.name), List("holiday.raw", "logs_4_dateien.rar"))
      click(all(root, "rect").last)
      assertEquals(state.selected, Seq(1), "the storage map also selects files")
      assertEquals(tutorial, List("✓ ", "✓ ", "✓ ", "○ "))
    }
  }

  test("a completed simulation reports success; the overview switches scenarios without editing them") {
    val clean = FileSimulation("clean", Seq(SimulationFile("movie.mp4", 1000, "video")))
    val (answer, element) = activity(clean)
    mounted(element) { root =>
      click(buttonNamed(root, src("widget.filesystemSimulator.tools.lossy.label")))
      click(all(root, "tbody button").head)
      assertEquals(answer.now().asInstanceOf[FileSimulation].files.head.bytes, 110L)
      assert(root.textContent.contains(src("widget.filesystemSimulator.success")))
      assert(root.textContent.contains(src("widget.filesystemSimulator.metadataNo")))
    }
    val (overview, overviewElement) = activity(FileOverview(Seq(photos, clean)))
    mounted(overviewElement) { root =>
      assertEquals(all(root, "tbody button").size, 0, "overview files are not editable")
      assertEquals(all(root, "tbody tr").size, 3)
      choose(one(root, "select"), "1")
      assertEquals(overview.now().asInstanceOf[FileOverview].selected, 1)
      assertEquals(all(root, "tbody tr").map(_.firstChild.textContent), List("movie.mp4"))
      assertEquals(all(root, "details").size, 8)
    }
  }

  test("a previous-answer comparison quotes the referenced written answer from the open workbook") {
    val earlier = CompressionExperimentInteraction("earlier", LanguageMapContentId("CompressionWorkbook/earlier"), WrittenAnswer())
    inOpenWorkbook(earlier) {
      val (_, element) = activity(PreviousAnswer("earlier"))
      mounted(element) { root =>
        assert(one(root, "blockquote").textContent.contains(text("comparisonEmpty")))
        pageInfo.interactions.setStateFromUserInteraction(earlier, WrittenAnswer("Smaller files load faster"),
          it.evadid.workbook.sync.UpdateImportance.MAJOR)
        assertEquals(one(root, "blockquote").textContent, "Smaller files load faster")
      }
    }
    val (_, unknown) = activity(PreviousAnswer("not-in-workbook"))
    mounted(unknown)(root => assert(one(root, "p").textContent.contains(text("comparisonEmpty"))))
  }

  test("a locked activity disables its controls and ignores storage-map clicks") {
    val (answer, element) = activity(photos.choose("lossless"), Val(true))
    mounted(element) { root =>
      assert(all(root, "button").forall(isDisabled))
      dispatch(all(root, "rect").head, "click")
      assertEquals(answer.now(), photos.choose("lossless"))
    }
  }
}
