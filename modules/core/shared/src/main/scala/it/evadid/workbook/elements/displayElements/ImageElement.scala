package it.evadid.workbook.elements.displayElements

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.TypeOfTextDisplay.URL_TYPE
import it.evadid.workbook.abstractions.{TypeOfTextDisplay, WorkbookDisplayElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}

sealed trait ImageElement extends WorkbookDisplayElement

object ImageElement {

  //def apply(elementId: String, fileDescription: FileDescription): ImageElement = FileBasedImageElement(elementId, fileDescription)

  def apply(elementId: String, languageMapContentId: LanguageMapContentId, howToResolveUrl: URL_TYPE): ImageElement = LanguageMapBasedImageElement(elementId, languageMapContentId, howToResolveUrl)

  /* case class FileBasedImageElement(override val elementId: String, location: FileDescription) extends ImageElement {
     override val associatedFactory: SimpleWorkbookElementFactory[FileBasedImageElement] = new SimpleWorkbookElementFactory[FileBasedImageElement] {
       override def finishSerialization(baseElement: WorkbookElementSerializable, infoElement: FileBasedImageElement): WorkbookElementSerializable = {
       }

       override def finishDeserialization(element: WorkbookElementSerializable): FileBasedImageElement = {
       }
     }
   }*/

  object LanguageMapBasedImageElement {
    val factory: WorkbookElementFactory[LanguageMapBasedImageElement] = new SimpleWorkbookElementFactory[LanguageMapBasedImageElement]() {
      override def finishSerialization(baseElement: WorkbookElementSerializable, infoElement: LanguageMapBasedImageElement): WorkbookElementSerializable = {
        baseElement
          .withContentIdAdded("content", infoElement.languageMapContentId)
          .withElementAddedAs("howToResolveUrl", infoElement.howToResolveUrl.asInstanceOf[TypeOfTextDisplay])
      }

      override def finishDeserialization(element: WorkbookElementSerializable): LanguageMapBasedImageElement = {
        LanguageMapBasedImageElement(element.elementId,
          element.getElementAsContentId("content"),
          element.getElementAs[TypeOfTextDisplay]("howToResolveUrl").asInstanceOf[URL_TYPE])
      }
    }
  }

  case class LanguageMapBasedImageElement(override val elementId: String, languageMapContentId: LanguageMapContentId, howToResolveUrl: URL_TYPE) extends ImageElement {
    override val associatedFactory: WorkbookElementFactory[LanguageMapBasedImageElement] = LanguageMapBasedImageElement.factory
  }
}
