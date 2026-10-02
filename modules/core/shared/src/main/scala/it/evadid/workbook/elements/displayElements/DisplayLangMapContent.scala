package it.evadid.workbook.elements.displayElements

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig
import it.evadid.workbook.abstractions.*
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.NoRefsElementFactory
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.*

case class DisplayLangMapContent(override val elementId: String, content: LanguageMapContentId, contentType: LangMapContentIdType) extends WorkbookDisplayElement derives ReadWriter {
  override val associatedFactory: WorkbookElementFactory[DisplayLangMapContent] = DisplayLangMapContent.factory
}

object DisplayLangMapContent {

  val factory: NoRefsElementFactory[DisplayLangMapContent] = new NoRefsElementFactory[DisplayLangMapContent]() {

    override lazy val elementMapAndOrderForConstructorLike: Map[Int, List[VariableDisplayConfig]] =
      Map(
        0 -> List(
          VariableDisplayConfig("elementId", true)),
        1 -> List(
          VariableDisplayConfig("contentType", false),
          VariableDisplayConfig("content", false)
        )
      )
    override lazy val writerJsonRegularRefBased: Writer[DisplayLangMapContent] = {
      DisplayLangMapContent.derived$ReadWriter
    }

    override def fromSerializedElement(f: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): DisplayLangMapContent = {
      DisplayLangMapContent(
        f.elementId,
        f.getElementAs[LanguageMapContentId]("content"),
        f.getElementAs[LangMapContentIdType]("contentType")(using summon[ReadWriter[LangMapContentIdType]]))

    }
  }
}
