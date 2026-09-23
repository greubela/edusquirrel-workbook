package it.evadid.workbook.elements.interactionElements.codeTaskToggle

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory

case class SketchDownloadInteraction(
                                      override val elementId: String,
                                      buttonLabel: LanguageMapContentId,
                                      filename: String,
                                      sketchContent: String,
                                      unlockWhenReorderCorrect: String
) extends WorkbookInteractionElement[String] {

  override val defaultValue: String = ""

  override val serializerInteractionContent: Serializer[String] = Serializer.stringIO

  override lazy val childrenOfThisElement: List[WorkbookElement] = List()

  override def toSerializableType: WorkbookElementFactory = toFactoryBase
    .withContentIdAdded("buttonLabel", buttonLabel).withElementAdded("filename", filename)
    .withElementAdded("sketchContent", sketchContent).withElementAdded("unlockWhenReorderCorrect", unlockWhenReorderCorrect)
}

object SketchDownloadInteraction { def fromFactory(f: WorkbookElementFactory): SketchDownloadInteraction = SketchDownloadInteraction(f.elementId, f.getElementAsContentId("buttonLabel"), f.getElementAsString("filename"), f.getElementAsString("sketchContent"), f.getElementAsString("unlockWhenReorderCorrect")) }
