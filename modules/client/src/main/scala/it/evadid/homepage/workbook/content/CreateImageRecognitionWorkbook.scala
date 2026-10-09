package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.user.User
import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.elements.interactionElements.choice.ChoiceInteraction
import it.evadid.workbook.elements.interactionElements.neuron.*
import it.evadid.workbook.elements.interactionElements.table.*
import it.evadid.workbook.elements.interactionElements.pixel.*
import it.evadid.workbook.model.pixel.*
import it.evadid.workbook.elements.structureElements.Workbook

/** Partial online adaptation of the pool activity and the first 3×5-pixel experiment. */
case class CreateImageRecognitionWorkbook(fullInfo: FullInfo) extends WorkbookFactory {
  override val workbookId = "workbookImageRecognition"
  private def id(key: String) = LanguageMapContentId(s"digitalWorkbooks/$key")
  override def createWorkbook: Workbook = workbook("digitalWorkbooks/workbookTitle", List(
    section("image-introduction", "digitalWorkbooks/introTitle", List(container("digitalWorkbooks/introTitle", List(
      instructionPlaintext("digitalWorkbooks/adaptationScope"),
      instructionPlaintext("digitalWorkbooks/introText"),
      ChoiceInteraction("image-prior-opinion", id("opinionPrompt"), List(id("opinionYes"), id("opinionNo"), id("opinionUnsure"))),
      createTextInput("image-opinion-reason")
    )))),
    section("image-threshold-neuron", "digitalWorkbooks/neuronTitle", List(container("digitalWorkbooks/neuronTitle", List(
      instructionPlaintext("digitalWorkbooks/binaryTableTask"),
      CreateImageRecognitionWorkbook.binaryTable,
      instructionPlaintext("digitalWorkbooks/neuronTask"),
      CreateImageRecognitionWorkbook.poolExercise,
      ChoiceInteraction("image-threshold-boundary", id("boundaryPrompt"), List(id("active"), id("inactive")), expected = Some(List(0))),
      ChoiceInteraction("image-weight-effects", id("weightPrompt"), List(id("effectWeighted"), id("effectThreshold"), id("effectAlwaysRight")),
        allowMultiple = true, expected = Some(List(0, 1))),
      instructionPlaintext("digitalWorkbooks/reflection"), createTextInput("image-neuron-reflection")
    )))),
    section("image-binary-pixels", "digitalWorkbooks/pixelChapter", List(container("digitalWorkbooks/pixelChapter", List(
      instructionPlaintext("digitalWorkbooks/pixelScope"),
      instructionPlaintext("digitalWorkbooks/pixelRecreateTask"),
      CreateImageRecognitionWorkbook.pixelRecreation,
      instructionPlaintext("digitalWorkbooks/pixelProbeTask"),
      CreateImageRecognitionWorkbook.pixelExperiment,
      ChoiceInteraction("image-pixel-uniqueness", id("pixelUniqueness"), List(id("opinionYes"), id("opinionNo")), expected = Some(List(1))),
      instructionPlaintext("digitalWorkbooks/pixelReflection"), createTextInput("image-pixel-reflection")
    ))))
  ), User("Dominic Schattka", "author-image-recognition-dominic-schattka", ""))
}
object CreateImageRecognitionWorkbook {
  private def id(key: String) = LanguageMapContentId(s"digitalWorkbooks/$key")
  // Binary encoding of the source's week: Lina available, a friend available,
  // homework finished, sunny/warm. Desired visits: Wednesday and Friday.
  lazy val binaryTable: AnswerTableInteraction = AnswerTableInteraction("image-binary-week", id("binaryTableTitle"),
    poolExercise.inputLabels, poolExercise.examples.map(_.label),
    poolExercise.inputLabels.indices.toList.map(input => poolExercise.examples.zipWithIndex.map { (example, day) =>
      val bit = example.inputs(input).toInt.toString
      // The first column is a worked example; learners complete the remaining days.
      if day == 0 then FixedTableCell(id(if bit == "1" then "one" else "zero"))
      else EditableTableCell(Some(List(bit)), List("0", "1"))
    }))
  val poolExercise = ThresholdNeuronInteraction("image-pool-neuron",
    List(id("lina"), id("friends"), id("school"), id("weather")),
    List(
      NeuronExample(id("monday"), List(1, 0, 1, 1), false),
      NeuronExample(id("tuesday"), List(0, 1, 0, 0), false),
      NeuronExample(id("wednesday"), List(1, 1, 0, 1), true),
      NeuronExample(id("thursday"), List(0, 1, 1, 0), false),
      NeuronExample(id("friday"), List(1, 1, 1, 1), true)
    ), NeuronParameters(List(1, 1, 1, 1), 4))

  // Transcribed from Workbook_Teil2.pdf, physical page 2 (chapter 3).
  val digitImages: List[BinaryPixelImage] = List(
    List("111", "101", "101", "101", "111"),
    List("010", "010", "010", "010", "010"),
    List("111", "001", "111", "100", "111"),
    List("111", "001", "111", "001", "111"),
    List("101", "101", "111", "001", "001"),
    List("111", "100", "111", "001", "111"),
    List("100", "100", "111", "101", "111"),
    List("111", "001", "001", "001", "001"),
    List("111", "101", "111", "101", "111"),
    List("111", "101", "111", "001", "001")
  ).map(BinaryPixelImage.fromRows)
  val pixelRecreation = BinaryPixelInteraction("image-pixel-recreation", id("pixelRecreateTitle"),
    BinaryPixelImage.blank(5, 3), expected = Some(digitImages(7)))
  val pixelExperiment = BinaryPixelInteraction("image-pixel-experiment", id("pixelExperimentTitle"), digitImages(7),
    probes = List(
      PixelThresholdProbe(id("pixelTopProbe"), (0 until 3).map(c => PixelPosition(0, c)).toList, 3),
      PixelThresholdProbe(id("pixelMiddleProbe"), (0 until 3).map(c => PixelPosition(2, c)).toList, 3)),
    presets = digitImages.zipWithIndex.map((image, digit) => PixelPreset(id(s"pixelDigit$digit"), image)))
}
