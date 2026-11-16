package it.evadid.workbook.elements.interactionElements.codeTaskToggle

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.{InteractionFormat, DefaultSerializer}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.abstractions.WorkbookInteractionElementFull
import upickle.default.*

case class SketchDownloadInteraction(
                                      override val elementId: String,
                                      buttonLabel: LanguageMapContentId,
                                      filenameRelativeToWorkbookResources: String,
                                      sketchContent: String,
                                      unlockWhenReorderCorrect: String
                                    ) extends WorkbookInteractionElement[String] {

  override val associatedFactory = SketchDownloadInteraction.factory

  override val defaultValue: String = ""

  override val serializerInteractionContent: Serializer[String] = Serializer.fromUpickleJson(summon[ReadWriter[String]])

  override lazy val childrenOfThisElement: List[WorkbookElement] = List()
  override def format: InteractionFormat[String, Unit, Unit] = SketchDownloadInteraction
}

object SketchDownloadInteraction extends InteractionFormat[String, Unit, Unit] {
  override def stateSerializer: Serializer[String] = Serializer.fromImplicitRW
  override def historySerializer: Serializer[InteractionVariableHistory[String, Unit, Unit]] = Serializer.fromImplicitRW
  override def exerciseSerializer: Serializer[WorkbookInteractionElementFull[String, Unit, Unit]] = Serializer.fromImplicitRW
  val factory = new SimpleWorkbookElementFactory[SketchDownloadInteraction]() {
    override protected val constructorFieldOrder = List("elementId", "buttonLabel", "filename", "sketchContent", "unlockWhenReorderCorrect")

    override def finishSerialization(baseElement: WorkbookElementSerializable, e: SketchDownloadInteraction): WorkbookElementSerializable = {
      baseElement
        .withElementAddedAs("buttonLabel", e.buttonLabel)
        .withElementAdded("filename", e.filenameRelativeToWorkbookResources)
        .withElementAdded("sketchContent", e.sketchContent)
        .withElementAdded("unlockWhenReorderCorrect", e.unlockWhenReorderCorrect)
    }

    override def finishDeserialization(f: WorkbookElementSerializable): SketchDownloadInteraction = {
      SketchDownloadInteraction(f.elementId, f.getElementAs[LanguageMapContentId]("buttonLabel"), f.getElementAs("filename"), f.getElementAs("sketchContent"), f.getElementAs("unlockWhenReorderCorrect"))
    }
  }
}
