package it.evadid.workbook.elements.displayElements

import it.evadid.core.datastructures.file.CopyrightInfo
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.TypeOfTextDisplay.URL_TYPE
import it.evadid.workbook.abstractions.{TypeOfTextDisplay, WorkbookDisplayElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}

sealed trait WorkbookImageElement extends WorkbookDisplayElement

object WorkbookImageElement {

  def apply(elementId: String, languageMapContentId: LanguageMapContentId, howToResolveUrl: URL_TYPE): WorkbookImageElement = LanguageMapBasedWorkbookImageElement(elementId, languageMapContentId, howToResolveUrl)

  object LanguageMapBasedWorkbookImageElement {
    val factory: WorkbookElementFactory[LanguageMapBasedWorkbookImageElement] = new SimpleWorkbookElementFactory[LanguageMapBasedWorkbookImageElement]() {
      override protected val constructorFieldOrder = List("elementId", "content", "howToResolveUrl")

      override def finishSerialization(baseElement: WorkbookElementSerializable, infoElement: LanguageMapBasedWorkbookImageElement): WorkbookElementSerializable = {
        baseElement
          .withElementAddedAs("content", infoElement.languageMapContentId)
          .withElementAddedAs("howToResolveUrl", infoElement.howToResolveUrl.asInstanceOf[TypeOfTextDisplay])
          .withElementAddedAs("description", infoElement.description)
          .withElementAddedAs("copyright", infoElement.copyright)

      }

      override def finishDeserialization(element: WorkbookElementSerializable): LanguageMapBasedWorkbookImageElement = {
        LanguageMapBasedWorkbookImageElement(element.elementId,
          element.getElementAs[LanguageMapContentId]("content"),
          element.getElementAs[TypeOfTextDisplay]("howToResolveUrl").asInstanceOf[URL_TYPE],
          element.getOptionalElementAs("description", None),
          element.getOptionalElementAs("copyright", None)
        )
      }
    }
  }

  case class LanguageMapBasedWorkbookImageElement(
                                                   override val elementId: String,
                                                   languageMapContentId: LanguageMapContentId,
                                                   howToResolveUrl: URL_TYPE,
                                                   description: Option[LanguageMapContentId] = None,
                                                   copyright: Option[CopyrightInfo] = None
                                                 ) extends WorkbookImageElement {
    override val associatedFactory: WorkbookElementFactory[LanguageMapBasedWorkbookImageElement] = LanguageMapBasedWorkbookImageElement.factory
  }


}
