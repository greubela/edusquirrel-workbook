package it.evadid.workbook.elements.interactionElements.codeTaskToggle

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}

case class SketchDownloadInteraction(
  override val id: String,
  buttonLabel: LanguageMapContentId,
  filename: String,
  sketchContent: String,
  unlockWhenReorderCorrect: String
) extends WorkbookInteractionElement[String] {

  override val defaultValue: String = ""

  override val serializer: Serializer[String] = Serializer.stringIO

  override lazy val childrenOfThisElement: List[WorkbookElement] = List()

}
