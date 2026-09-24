package it.evadid.workbook.elements.displayElements

import it.evadid.core.datastructures.file.{CopyrightInfo, FileDescription, LoadedFile}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.TypeOfTextDisplay.URL_TYPE
import it.evadid.workbook.abstractions.WorkbookDisplayElement
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable

import scala.concurrent.Future

sealed trait ImageElement extends WorkbookDisplayElement

object ImageElement {

  def apply(elementId: String, fileDescription: FileDescription): ImageElement = FileBasedImageElement(elementId, fileDescription)

  def apply(elementId: String, languageMapContentId: LanguageMapContentId, copyrightInfo: CopyrightInfo, howToResolveUrl: URL_TYPE): ImageElement = LanguageMapBasedImageElement(elementId, languageMapContentId, copyrightInfo, howToResolveUrl)

  case class FileBasedImageElement(override val elementId: String, location: FileDescription) extends ImageElement {
  override val associatedFactory = it.evadid.workbook.jsonFactory.WorkbookElementFactory.unsupported[this.type](this.getClass.getSimpleName)

     }

  object FileBasedImageElement {

     }

  case class LanguageMapBasedImageElement(override val elementId: String, languageMapContentId: LanguageMapContentId, copyrightInfo: CopyrightInfo, howToResolveUrl: URL_TYPE) extends ImageElement {
  override val associatedFactory = it.evadid.workbook.jsonFactory.WorkbookElementFactory.unsupported[this.type](this.getClass.getSimpleName)

     }

  object LanguageMapBasedImageElement {

   }


}
