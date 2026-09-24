package it.evadid.workbook.elements.interactionElements.codeTaskToggle

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable

case class SketchDownloadInteraction(
                                      override val elementId: String,
                                      buttonLabel: LanguageMapContentId,
                                      filename: String,
                                      sketchContent: String,
                                      unlockWhenReorderCorrect: String
) extends WorkbookInteractionElement[String] {
  override val associatedFactory = SketchDownloadInteraction.factory

  override val defaultValue: String = ""

  override val serializerInteractionContent: Serializer[String] = Serializer.stringIO

  override lazy val childrenOfThisElement: List[WorkbookElement] = List()

}

object SketchDownloadInteraction { val factory = it.evadid.workbook.jsonFactory.WorkbookElementFactory.simple[SketchDownloadInteraction](e => WorkbookElementSerializable(e.elementId, classOf[SketchDownloadInteraction].getSimpleName, Map()).withContentIdAdded("buttonLabel", e.buttonLabel).withElementAdded("filename", e.filename).withElementAdded("sketchContent", e.sketchContent).withElementAdded("unlockWhenReorderCorrect", e.unlockWhenReorderCorrect), fromFactory)
 def fromFactory(f: WorkbookElementSerializable): SketchDownloadInteraction = SketchDownloadInteraction(f.elementId, f.getElementAsContentId("buttonLabel"), f.getElementAsString("filename"), f.getElementAsString("sketchContent"), f.getElementAsString("unlockWhenReorderCorrect")) }
