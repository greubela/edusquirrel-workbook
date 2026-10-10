package it.evadid.workbook.elements.interactionElements.compression

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import it.evadid.workbook.model.compression.CompressionExperiment
import upickle.default.*

case class CompressionExperimentInteraction(elementId: String, title: LanguageMapContentId, initial: CompressionExperiment)
    extends WorkbookInteractionElement[CompressionExperiment] {
  override val defaultValue = initial
  override lazy val childrenOfThisElement = Nil
  private def checked(value: CompressionExperiment): CompressionExperiment = {
    require(value.getClass == initial.getClass, "Stored compression experiment must match the exercise type")
    value
  }
  override val serializerInteractionContent: Serializer[CompressionExperiment] =
    Serializer.fromUpickleJson(summon[ReadWriter[CompressionExperiment]]).map(checked, checked)
  override val associatedFactory = CompressionExperimentInteraction.factory
}
object CompressionExperimentInteraction {
  val factory = new WorkbookElementFactory.SimpleWorkbookElementFactory[CompressionExperimentInteraction] {
    override protected val constructorFieldOrder = List("elementId", "title", "initial")
    override def finishSerialization(base: WorkbookElementSerializable, e: CompressionExperimentInteraction): WorkbookElementSerializable =
      base.withElementAddedAs("title", e.title).withElementAddedAs("initial", e.initial)
    override def finishDeserialization(e: WorkbookElementSerializable): CompressionExperimentInteraction =
      CompressionExperimentInteraction(e.elementId, e.getElementAs[LanguageMapContentId]("title"), e.getElementAs[CompressionExperiment]("initial"))
  }
}
