package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.user.User
import it.evadid.homepage.control.model.*
import it.evadid.workbook.abstractions.{WorkbookElement, TypeOfTextDisplay}
import it.evadid.workbook.elements.structureElements.Workbook
import it.evadid.workbook.elements.displayElements.{CollapsibleInstructionElement, ImageElement}
import it.evadid.workbook.elements.interactionElements.compression.CompressionExperimentInteraction
import it.evadid.workbook.elements.interactionElements.choice.ChoiceInteraction
import it.evadid.workbook.elements.interactionElements.slideshow.{Slideshow, SlideshowPanel}
import it.evadid.workbook.model.compression.*

/** Native reconstruction of every chapter and activity in the retained standalone workbook. */
case class CreateCompressionWorkbook(override val fullInfo: FullInfo) extends WorkbookFactory {
  override val availableLanguages: List[HumanLanguage] = List(German)
  override val workbookId = "CompressionWorkbook"
  private def t(key: String): String = s"CompressionWorkbook/$key"
  private def source(key: String): String = t("src_" + key.replace('.', '_'))
  private def id(key: String): LanguageMapContentId = LanguageMapContentId(source(key))
  private def experiment(elementId: String, title: String, initial: CompressionExperiment): WorkbookElement =
    CompressionExperimentInteraction(elementId, id(title), initial)

  private def activity(widget: String): WorkbookElement = widget match {
    case "calculator-video" => experiment(widget, "section0.task1.title", VideoBudget())
    case "calculator-photo" => experiment(widget, "section0.task2.title", PhotoBudget())
    case "widget-s1-rle" => experiment(widget, "lossless.task2.title", RunLengthText())
    case "widget-s1-rle-text" => experiment(widget, "lossless.task3.title", RunLengthText(CreateCompressionWorkbook.sampleText))
    case "widget-s1-dict" => experiment(widget, "widget.dict.dictTableLabel", DictionaryText(CreateCompressionWorkbook.sampleText))
    case "widget-s1-efficiency" => sortingReasonExercise(widget,
      List("optionRle", "optionDict", "optionNone").map(k => source(s"widget.efficiency.$k")),
      List(1,2,1,0).zipWithIndex.map((correct,i) => (source(s"widget.efficiency.files.${i}.nativeLabel"), correct,
        source(s"widget.efficiency.files.${i}.feedbackWrong"), source("widget.efficiency.reasonPrompt"))))
    case "widget-s2-bitflip" => experiment(widget, "lossy.task1.title", BitComparison())
    case "widget-s2-jpeg-slideshow" => Slideshow(widget, (0 until 6).map { i =>
      SlideshowPanel.ImageSlide(s"jpeg-slide-$i", ImageElement.LanguageMapBasedImageElement(s"jpeg-image-$i",
        LanguageMapContentId(t(s"jpegImage$i")), TypeOfTextDisplay.URL_RELATIVE_TO_TECHNICAL_RESOURCES),
        id("lossy.task2.title"), id(s"lossy.task2.slides.$i.caption"))
    }.toList)
    case "widget-s2-blockavg" => experiment(widget, "lossy.task3.title", ImageBlocks(CreateCompressionWorkbook.imageResource))
    case "widget-s2-blocksize" => experiment(widget, "lossy.task4.title", ImageBlocks(CreateCompressionWorkbook.imageResource, separateChannels = false))
    case "widget-s2-text-vs-jpeg" => experiment(widget, "lossy.task5.title", ImageBlocks(CreateCompressionWorkbook.screenshotResource,
      imageWidth = CompressionSourceData.screenshotWidth, imageHeight = CompressionSourceData.screenshotHeight, comparison = Some(ImageFileComparison(
        CompressionSourceData.screenshotTextBytes, CompressionSourceData.screenshotBytes, Some(LanguageMapContentId(t("screenshotFormatNote")))))))
    case "widget-s2-lossy-closing" => sortingReasonExercise(widget,
      List("optionLossy", "optionLossless", "optionNone").map(k => source(s"widget.lossyClosing.$k")),
      List(0,0,1,2).zipWithIndex.map((correct,i) => (source(s"widget.lossyClosing.files.${i}.nativeLabel"), correct,
        source(s"widget.lossyClosing.files.${i}.feedbackWrong"), source("widget.lossyClosing.reasonPrompt"))))
    case "widget-s3-fileinspector" => experiment(widget, "filetypes.task1.title", FileInspection(CompressionSourceData.inspectorText, formattedContent = Some(LanguageMapContentId(t("inspectorFormattedLetter")))))
    case "widget-s3-zip" => experiment(widget, "filetypes.task4.title", TransferSimulation(CompressionSourceData.archiveFiles))
    case "widget-s4-filesystem" => experiment(widget, "final.task1.title", FileOverview(CompressionSourceData.scenarios.filterNot(_.tutorial)))
    case key if key.startsWith("widget-s4-sim-") =>
      val scenario = key.stripPrefix("widget-s4-sim-")
      experiment(widget, s"widget.filesystemSimulator.scenarios.$scenario", CompressionSourceData.scenarios.find(_.scenario == scenario).get)
    case key => throw IllegalArgumentException(s"Unmapped compression activity: $key")
  }
  private def element(n: CompressionSourceContent.Node): WorkbookElement = n.kind match {
    case "text" => instructionHtml(source(n.value))
    case "answer" => experiment(n.id, "common.answerPlaceholder", WrittenAnswer(
      if n.value.isEmpty then "" else CompressionSourceData.skeletons(n.value), n.value.nonEmpty))
    case "widget" => activity(n.id)
    case "hint" => CollapsibleInstructionElement(n.id, id("common.showHint"), id(n.value))
    case "ethics" => experiment(n.id, "intro.ethicsQuestion", EthicalReflection())
    case "choice" => ChoiceInteraction(n.id, id(n.value), n.extra.split('|').toList.map(id))
    case "compare" => experiment(n.id, "final.task1.b.thenLabel", PreviousAnswer("answer-intro"))
    case key => throw IllegalArgumentException(s"Unmapped source node: $key")
  }
  override lazy val createWorkbook: Workbook = workbook(t("workbookTitle"), CompressionSourceContent.sections.map { s =>
    section(s.id, source(s.title), s.blocks.flatMap { b =>
      val downloads = if s.id == "filetypes" && b == s.blocks.head then List(instructionHtml(t("materialsDownloads"))) else Nil
      val content = downloads ++ b.nodes.filterNot(n => n.kind == "text" && n.value == b.title).map(element)
      if b.title == s.title then content else List(container(source(b.title), content))
    })
  }, User.YanneckDimitrov)
}
object CreateCompressionWorkbook {
  val sampleText = CompressionSourceData.sampleText
  val imageResource = "workbookresources/compression/source-cat.jpg"
  val screenshotResource = "programs/20260907Datenkompression/Material/Sektion 2/Screenshot.jpg"
}
