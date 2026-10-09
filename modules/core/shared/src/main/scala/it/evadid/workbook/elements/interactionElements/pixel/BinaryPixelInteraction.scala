package it.evadid.workbook.elements.interactionElements.pixel

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import it.evadid.workbook.model.pixel.*
import upickle.default.*

/** Unit-weight threshold detector over explicitly selected pixels; no training is implied. */
case class PixelThresholdProbe(label: LanguageMapContentId, cells: List[PixelPosition], threshold: Int) derives ReadWriter {
  require(cells.nonEmpty && cells.distinct == cells, "Probe pixels must be nonempty and distinct")
  require(threshold >= 0 && threshold <= cells.size, "Probe threshold must be between zero and the number of pixels")
  def activeCount(image: BinaryPixelImage): Int = cells.count(image.at)
  def activates(image: BinaryPixelImage): Boolean = activeCount(image) >= threshold
}
case class PixelPreset(label: LanguageMapContentId, image: BinaryPixelImage) derives ReadWriter

case class BinaryPixelInteraction(elementId: String, title: LanguageMapContentId, initial: BinaryPixelImage,
    expected: Option[BinaryPixelImage] = None, probes: List[PixelThresholdProbe] = Nil, presets: List[PixelPreset] = Nil)
    extends WorkbookInteractionElement[BinaryPixelImage] {
  require(expected.forall(initial.sameDimensions) && presets.forall(p => initial.sameDimensions(p.image)),
    "Targets and presets must match the canvas dimensions")
  probes.foreach(_.cells.foreach(initial.index))
  override lazy val childrenOfThisElement = Nil
  override val defaultValue = initial
  private def checked(image: BinaryPixelImage): BinaryPixelImage = {
    require(initial.sameDimensions(image), "Stored pixel dimensions do not match the exercise")
    image
  }
  override val serializerInteractionContent: Serializer[BinaryPixelImage] =
    Serializer.fromUpickleJson(summon[ReadWriter[BinaryPixelImage]]).map(checked, checked)
  override val associatedFactory = BinaryPixelInteraction.factory
  def toggle(image: BinaryPixelImage, position: PixelPosition): BinaryPixelImage = checked(image).toggle(position)
  def matchingPixels(image: BinaryPixelImage): Option[Int] = {
    val valid = checked(image)
    expected.map(valid.matchingPixels)
  }
  def isCorrect(image: BinaryPixelImage): Option[Boolean] = {
    checked(image)
    expected.map(_ == image)
  }
  def outputs(image: BinaryPixelImage): List[Boolean] = {
    val valid = checked(image)
    probes.map(_.activates(valid))
  }
}
object BinaryPixelInteraction {
  val factory = new WorkbookElementFactory.SimpleWorkbookElementFactory[BinaryPixelInteraction] {
    override protected val constructorFieldOrder = List("elementId", "title", "initial", "expected", "probes", "presets")
    override def finishSerialization(base: WorkbookElementSerializable, e: BinaryPixelInteraction): WorkbookElementSerializable =
      base.withElementAddedAs("title", e.title).withElementAddedAs("initial", e.initial)
        .withElementAddedAs("expected", e.expected).withElementAddedAs("probes", e.probes).withElementAddedAs("presets", e.presets)
    override def finishDeserialization(e: WorkbookElementSerializable): BinaryPixelInteraction =
      BinaryPixelInteraction(e.elementId, e.getElementAs[LanguageMapContentId]("title"), e.getElementAs[BinaryPixelImage]("initial"),
        e.getElementAs[Option[BinaryPixelImage]]("expected"), e.getElementAs[List[PixelThresholdProbe]]("probes"),
        e.getElementAs[List[PixelPreset]]("presets"))
  }
}
