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

  override val defaultValue: String = ""

  override val serializerInteractionContent: Serializer[String] = Serializer.stringIO

  override lazy val childrenOfThisElement: List[WorkbookElement] = List()

}

object SketchDownloadInteraction { def fromFactory(f: WorkbookElementSerializable): SketchDownloadInteraction = SketchDownloadInteraction(f.elementId, f.getElementAsContentId("buttonLabel"), f.getElementAsString("filename"), f.getElementAsString("sketchContent"), f.getElementAsString("unlockWhenReorderCorrect")) }
