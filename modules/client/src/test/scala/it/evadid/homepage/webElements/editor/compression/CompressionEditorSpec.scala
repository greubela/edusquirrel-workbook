package it.evadid.homepage.webElements.editor.compression

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.UiDomSuite
import it.evadid.workbook.model.compression.*
import org.scalajs.dom
import scala.concurrent.ExecutionContext

/** The fullscreen compression models: numeric drafts, text encodings, bit flips, image blocks and storage maps. */
class CompressionEditorSpec extends UiDomSuite {
  private given ExecutionContext = ExecutionContext.global
  private def text(key: String) = loadingText(s"CompressionWorkbook/$key")
  private def editor(initial: CompressionExperiment, locked: Signal[Boolean] = Val(false)): (Var[CompressionExperiment], Element) = {
    val answer = Var(initial)
    val created = CompressionEditor(pageInfo, answer, initial, locked)
    assert(!created.dismissOnOutsideClick)
    answer -> created.getDomElement()
  }
  private def numbers(root: dom.Element) = all(root, "input[type=number]")
  private def reset(root: dom.Element) = click(buttonNamed(root, text("resetExperiment")))

  test("numeric settings accept values in range, flag invalid drafts without saving them and reset to the start") {
    val (answer, element) = editor(VideoBudget())
    mounted(element) { root =>
      val List(bitrate, duration, copies, capacity) = numbers(root): @unchecked
      assertEquals(numbers(root).map(valueOf), List("5", "300", "1", "2000"))
      typeInto(bitrate, "2.5")
      typeInto(copies, "3")
      assertEquals(answer.now(), VideoBudget(2.5, 300, 3, 2000))
      assert(one(root, ".compression-summary").textContent.contains(text("videoSummary")))
      typeInto(duration, "1.5")
      assertEquals(duration.getAttribute("aria-invalid"), "true", "durations are whole seconds")
      assert(root.textContent.contains(text("invalidNumber")))
      typeInto(capacity, "0")
      assertEquals(capacity.getAttribute("aria-invalid"), "true", "below the minimum")
      assertEquals(answer.now(), VideoBudget(2.5, 300, 3, 2000), "invalid drafts are not saved")
      reset(root)
      assertEquals(answer.now(), VideoBudget())
      assertEquals(numbers(root).map(valueOf), List("5", "300", "1", "2000"))
      assert(numbers(root).forall(_.getAttribute("aria-invalid") == "false"))
      answer.set(VideoBudget(copies = 9))
      assertEquals(valueOf(copies), "9", "a restored answer replaces the draft")
    }
  }

  test("photo and archive budgets edit their fields; a locked editor disables them") {
    val (photo, photoElement) = editor(PhotoBudget())
    mounted(photoElement) { root =>
      typeInto(numbers(root).head, "0,5")
      assertEquals(numbers(root).head.getAttribute("aria-invalid"), "true", "the browser number field uses a dot")
      typeInto(numbers(root).head, "0.5")
      assertEquals(photo.now(), PhotoBudget(0.5))
    }
    val (archive, archiveElement) = editor(ArchiveBudget())
    mounted(archiveElement) { root =>
      List("20", "2048", "0", "5", "4096").zip(numbers(root)).foreach((value, field) => typeInto(field, value))
      assertEquals(archive.now(), ArchiveBudget(20, 2048, 0, 5, 4096))
      assert(root.textContent.contains(text("archiveCostNote")))
    }
    val (_, lockedElement) = editor(ArchiveBudget(), Val(true))
    mounted(lockedElement)(root => assert(numbers(root).forall(isDisabled) && isDisabled(buttonNamed(root, text("resetExperiment")))))
  }

  test("run-length text shows its runs, decoding and the challenges it achieved") {
    val (answer, element) = editor(RunLengthText("AAB"))
    mounted(element) { root =>
      def outputs = all(root, "pre.compression-output").map(_.textContent)
      assertEquals(outputs, List("(A,2) (B,1)", "AAB"))
      typeInto(one(root, "textarea"), "AAAAAAAAAAAA")
      assertEquals(outputs, List("(A,12)", "AAAAAAAAAAAA"))
      val achieved = answer.now().asInstanceOf[RunLengthText].achieved
      assertEquals(achieved, Seq(0, 2), "halving the size and a run of ten are achieved")
      assertEquals(all(root, "li").map(_.textContent.take(2)), List("✓ ", "○ ", "✓ "))
    }
  }

  test("dictionary coding steps word by word, repeats become references and the table can expand") {
    val source = "a b a c d e f g h"
    val (answer, element) = editor(DictionaryText(source))
    def step = answer.now().asInstanceOf[DictionaryText].step
    mounted(element) { root =>
      assert(isDisabled(buttonNamed(root, text("previousStep"))))
      click(buttonNamed(root, text("nextStep")))
      click(buttonNamed(root, text("nextStep")))
      click(buttonNamed(root, text("nextStep")))
      assertEquals(step, 3)
      assertEquals(all(root, ".compression-active-word").map(_.textContent), List("a"), "the third word is highlighted")
      assert(all(root, "pre.compression-output").exists(_.textContent.startsWith("a b W1")), "the repeated word is a reference")
      click(buttonNamed(root, text("previousStep")))
      assertEquals(step, 2)
      click(buttonNamed(root, text("allSteps")))
      assertEquals(step, 9)
      assert(isDisabled(buttonNamed(root, text("nextStep"))))
      assertEquals(all(root, ".compression-table-wrap tbody tr").size, 1, "after the last word only referenced entries stay")
      typeInto(one(root, "textarea"), "one two three four five six seven eight")
      assertEquals(step, 0, "new text starts over")
      click(buttonNamed(root, text("allSteps")))
      click(buttonNamed(root, text("nextStep")))
      answer.update(_ => DictionaryText("one two three four five six seven eight", 7))
      assertEquals(all(root, ".compression-table-wrap tbody tr").map(_.firstChild.textContent), List("W1", "W2", "W3", "W6", "W7"),
        "long tables show their start and end")
      assert(root.textContent.contains(text("moreDictionaryEntries")))
      click(buttonNamed(root, text("tableExpand")))
      assertEquals(all(root, ".compression-table-wrap tbody tr").size, 7)
    }
  }

  test("dictionary playback advances on its own at the chosen speed and stops after the last word") {
    val (answer, element) = editor(DictionaryText("x y z"))
    mountedAsync(element) { root =>
      typeInto(one(root, "input[type=range]"), "200")
      val play = buttonNamed(root, text("play"))
      click(play)
      assert(play.textContent.contains(text("pause")))
      eventually(answer.now().asInstanceOf[DictionaryText].step == 3 && play.textContent.contains(text("play")),
        attempts = 1000, clue = "playback to the end").map(_ => assertEquals(answer.now(), DictionaryText("x y z", 3)))
    }
  }

  test("flipping a bit changes one byte of the text and flipping it again restores it") {
    val (answer, element) = editor(TextBits("Hi"))
    mounted(element) { root =>
      val bits = all(root, ".compression-bit-grid button")
      assertEquals(bits.size, 16)
      assertEquals(bits.take(8).map(_.textContent).mkString, "01001000", "H")
      click(bits(15))
      assertEquals(answer.now().asInstanceOf[TextBits].changed, "Hh")
      assertEquals(bits(15).getAttribute("aria-pressed"), "true")
      assertEquals(bits.drop(8).map(_.textContent).mkString, "01101000")
      click(bits(2))
      assertEquals(answer.now().asInstanceOf[TextBits].changed, "hi", "only one bit is flipped at a time")
      click(bits(2))
      assertEquals(answer.now().asInstanceOf[TextBits].flippedBit, None)
    }
  }

  test("image blocks render the averaged preview from the source pixels and update with the block size") {
    val pixels = Seq((0, 0, 0), (100, 50, 0), (200, 0, 100), (40, 40, 40), (10, 20, 30), (90, 60, 30), (250, 250, 250), (0, 0, 0))
    val canvas = FakeCanvas(4, 2, pixels)
    canvas.install()
    val initial = ImageBlocks("sample.png", imageWidth = 4, imageHeight = 2, zoom = 150)
    val (answer, element) = editor(initial)
    def rgb(cBlock: Int, yBlock: Int) = CompressionAlgorithms.imageBlocks(pixels.toVector, 4, 2, yBlock, cBlock)
    mountedAsync(element) { root =>
      assert(one(root, "[role=status]").textContent.contains(text("imageLoading")))
      (for {
        _ <- eventually(canvas.lastRaster.nonEmpty, clue = "first preview")
        _ = assertEquals(canvas.lastRaster, rgb(1, 1), "1×1 blocks reproduce the image")
        _ = assertEquals(one(root, "canvas").asInstanceOf[dom.html.Canvas].style.width, "150%")
        _ = assert(one(root, "img.compression-original-image").getAttribute("src").endsWith("sample.png"))
        List(brightness, colour) = all(root, "select"): @unchecked
        _ = choose(colour, "2")
        _ = assertEquals(canvas.lastRaster, rgb(2, 1), "colour is averaged in 2×2 blocks, brightness stays")
        _ = choose(brightness, "4")
        _ = assertEquals((answer.now().asInstanceOf[ImageBlocks].luminanceBlock, answer.now().asInstanceOf[ImageBlocks].chromaBlock), (4, 2))
        _ = assertEquals(canvas.lastRaster, rgb(2, 4))
        items = all(root, "li").map(_.textContent)
        _ = assertEquals(items.map(_.take(2)).init, List("○ ", "✓ ", "✓ "), "challenges")
        _ = assert(items.last.endsWith(text("estimatedSize")), items.last)
      } yield ()).transform { outcome => canvas.uninstall(); outcome }
    }
  }

  test("without separate channels one block size applies to all colours; a missing image is reported") {
    val pixels = Seq((0, 0, 0), (100, 100, 100), (50, 50, 50), (10, 10, 10))
    val canvas = FakeCanvas(2, 2, pixels)
    canvas.install()
    val (answer, element) = editor(ImageBlocks("pic.png", separateChannels = false, imageWidth = 2, imageHeight = 2,
      comparison = Some(ImageFileComparison(100, 2000, Some(LanguageMapContentId("CompressionWorkbook/compareNote"))))))
    val (_, missing) = editor(ImageBlocks("missing.png", imageWidth = 2, imageHeight = 2))
    mountedAsync(div(element, missing)) { root =>
      val List(first, second) = all(root, ".compression-editor"): @unchecked
      assertEquals(all(first, "select").size, 1, "no separate colour block size")
      assert(first.textContent.contains("test.txt: 100 Bytes · Screenshot.jpg: 2000 Bytes"))
      assert(first.textContent.contains(text("compareNote")))
      (for {
        _ <- eventually(canvas.lastRaster.nonEmpty)
        _ = choose(one(first, "select"), "2")
        _ = assertEquals(answer.now().asInstanceOf[ImageBlocks].chromaBlock, 2, "colour follows brightness")
        _ = assertEquals(canvas.lastRaster, Seq.fill(4)((40, 40, 40)), "plain RGB averaging of the whole block")
        _ = typeInto(one(first, "input[type=number]"), "200")
        _ = assertEquals(answer.now().asInstanceOf[ImageBlocks].zoom, 200)
        _ <- eventually(one(second, "[role=status]").textContent.contains(text("imageLoadFailed")), clue = "load failure")
      } yield ()).transform { outcome => canvas.uninstall(); outcome }
    }
  }

  test("a storage study switches between packages and maps each file proportionally") {
    val info = LanguageMapContentId("CompressionWorkbook/info")
    val study = StorageStudy(Seq(
      StoragePackage(LanguageMapContentId("CompressionWorkbook/holiday"), Seq(StorageFile("a.jpg", 300, info), StorageFile("b.mp4", 700, info))),
      StoragePackage(LanguageMapContentId("CompressionWorkbook/school"), Seq(StorageFile("notes.txt", 5, info)))))
    val (answer, element) = editor(study)
    mounted(element) { root =>
      assertEquals(all(root, "rect").map(_.getAttribute("width")), List("300", "700"))
      assertEquals(all(root, "rect").map(_.getAttribute("x")), List("0", "300"))
      assertEquals(all(root, "tbody tr").map(row => all(row, "td").take(2).map(_.textContent)), List(List("a.jpg", "300"), List("b.mp4", "700")))
      choose(one(root, "select"), "1")
      assertEquals(answer.now().asInstanceOf[StorageStudy].selected, 1)
      assertEquals(all(root, "rect").map(_.getAttribute("width")), List("1000"))
    }
  }
}
