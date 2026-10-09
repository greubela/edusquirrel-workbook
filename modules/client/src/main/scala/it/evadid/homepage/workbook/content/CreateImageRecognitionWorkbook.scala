package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.user.User
import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.elements.interactionElements.choice.ChoiceInteraction
import it.evadid.workbook.elements.interactionElements.neuron.*
import it.evadid.workbook.elements.structureElements.Workbook

/** First online chapter adapted from Workbook_Teil1.pdf, chapter 2, pages 6–8. */
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
      instructionPlaintext("digitalWorkbooks/neuronTask"),
      CreateImageRecognitionWorkbook.poolExercise,
      ChoiceInteraction("image-threshold-boundary", id("boundaryPrompt"), List(id("active"), id("inactive")), expected = Some(List(0))),
      ChoiceInteraction("image-weight-effects", id("weightPrompt"), List(id("effectWeighted"), id("effectThreshold"), id("effectAlwaysRight")),
        allowMultiple = true, expected = Some(List(0, 1))),
      instructionPlaintext("digitalWorkbooks/reflection"), createTextInput("image-neuron-reflection")
    ))))
  ), User("Dominic Schattka", "author-image-recognition-dominic-schattka", ""))
}
object CreateImageRecognitionWorkbook {
  private def id(key: String) = LanguageMapContentId(s"digitalWorkbooks/$key")
  // Binary encoding of the source's week: Lina available, a friend available,
  // homework finished, sunny/warm. Desired visits: Wednesday and Friday.
  val poolExercise = ThresholdNeuronInteraction("image-pool-neuron",
    List(id("lina"), id("friends"), id("school"), id("weather")),
    List(
      NeuronExample(id("monday"), List(1, 0, 1, 1), false),
      NeuronExample(id("tuesday"), List(0, 1, 0, 0), false),
      NeuronExample(id("wednesday"), List(1, 1, 0, 1), true),
      NeuronExample(id("thursday"), List(0, 1, 1, 0), false),
      NeuronExample(id("friday"), List(1, 1, 1, 1), true)
    ), NeuronParameters(List(1, 1, 1, 1), 4))
}
