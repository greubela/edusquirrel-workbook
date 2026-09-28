package it.evadid.workbook.elements.interactionElements.codeTaskToggle

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable

case class SketchDownloadInteraction(
                                      override val elementId: String,
                                      buttonLabel: LanguageMapContentId,
                                      filenameRelativeToWorkbookResources: String,
                                      sketchContent: String,
                                      unlockWhenReorderCorrect: String
                                    ) extends WorkbookInteractionElement[String] {
  override val associatedFactory = SketchDownloadInteraction.factory

  override val defaultValue: String = ""

  override val serializerInteractionContent: Serializer[String] = Serializer.stringIO

  override lazy val childrenOfThisElement: List[WorkbookElement] = List()

}

object SketchDownloadInteraction {
  val factory = new SimpleWorkbookElementFactory[SketchDownloadInteraction]() {

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
