package it.evadid.workbook.interactions.codeTaskToggle

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.{DefaultSerializer}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookElement, WorkbookInteractionElement, *}
import it.evadid.workbook.serialization.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.serialization.WorkbookElementSerializable
import it.evadid.workbook.history.InteractionHistory
import upickle.default.*
import it.evadid.workbook.abstractions.*
import it.evadid.workbook.abstractions.InteractionFormat.*
case class SketchDownloadInteraction(
                                      override val elementId: String,
                                      buttonLabel: LanguageMapContentId,
                                      filenameRelativeToWorkbookResources: String,
                                      sketchContent: String,
                                      unlockWhenReorderCorrect: String
                                    ) extends WorkbookInteractionElement[TextInteractionFormat.type] {

  override val associatedFactory = SketchDownloadInteraction.factory

  override val defaultValue: String = ""

  override lazy val childrenOfThisElement: Seq[WorkbookElement] = List()

}

object SketchDownloadInteraction  {

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
