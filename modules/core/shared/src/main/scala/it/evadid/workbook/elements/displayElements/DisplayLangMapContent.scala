package it.evadid.workbook.elements.displayElements

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.*
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.ReadWriter

case class DisplayLangMapContent(override val elementId: String, content: LanguageMapContentId, contentType: LangMapContentIdType) extends WorkbookDisplayElement {
  override val associatedFactory: WorkbookElementFactory[DisplayLangMapContent] = DisplayLangMapContent.factory

}

object DisplayLangMapContent {
  val factory: SimpleWorkbookElementFactory[DisplayLangMapContent] = new SimpleWorkbookElementFactory[DisplayLangMapContent]() {
    override def finishSerialization(baseElement: WorkbookElementSerializable, e: DisplayLangMapContent): WorkbookElementSerializable = {
      baseElement
        .withElementAddedAs("content", e.content)
        .withElementAddedAs[LangMapContentIdType]("contentType", e.contentType)(using summon[ReadWriter[LangMapContentIdType]])
    }

    override def finishDeserialization(f: WorkbookElementSerializable): DisplayLangMapContent = {

      DisplayLangMapContent(
        f.elementId,
        f.getElementAs[LanguageMapContentId]("content"),
        f.getElementAs[LangMapContentIdType]("contentType")(using summon[ReadWriter[LangMapContentIdType]]))
    }
  }

}
