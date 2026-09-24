package it.evadid.workbook.elements.displayElements
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.*
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable
case class DisplayLangMapContent(override val elementId: String, content: LanguageMapContentId, contentType: LangMapContentIdType) extends WorkbookDisplayElement {
  override val associatedFactory = DisplayLangMapContent.factory

}
object DisplayLangMapContent {
 val factory = it.evadid.workbook.jsonFactory.WorkbookElementFactory.simple[DisplayLangMapContent](e => WorkbookElementSerializable(e.elementId, classOf[DisplayLangMapContent].getSimpleName, Map()).withContentIdAdded("content", e.content).withElementAdded("role", e.contentType.contentRole.toString).withElementAdded("displayType", e.contentType.contentType.toString), fromFactory)

 def fromFactory(f: WorkbookElementSerializable): DisplayLangMapContent = {
  val display = f.getElementAsString("displayType") match { case "PLAINTEXT" => TypeOfTextDisplay.PLAINTEXT; case "HTML" => TypeOfTextDisplay.HTML; case "MARKDOWN" => TypeOfTextDisplay.MARKDOWN; case "PLAINTEXT_UNDERSCORE_REPLACABLE" => TypeOfTextDisplay.PLAINTEXT_UNDERSCORE_REPLACABLE; case other => throw IllegalArgumentException(s"Unsupported display type $other") }
  DisplayLangMapContent(f.elementId, f.getElementAsContentId("content"), LangMapContentIdType(RoleInWorkbook.valueOf(f.getElementAsString("role")), display))
 }
}
