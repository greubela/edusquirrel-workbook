package it.evadid.workbook.model.elements

import it.evadid.core.datastructures.file.{CopyrightInfo, FileDescription}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.model.abstractions.TypeOfTextContent.*
import it.evadid.workbook.model.abstractions.WorkbookElement

sealed trait ImageElement extends WorkbookElement {


}

object ImageElement {

  def apply(fileDescription: FileDescription): ImageElement = FileBasedImageElement(fileDescription)

  def apply(languageMapContentId: LanguageMapContentId, copyrightInfo: CopyrightInfo, howToResolveUrl: URL_TYPE): ImageElement = LanguageMapBasedImageElement(languageMapContentId, copyrightInfo, howToResolveUrl)
  
  
  
  case class FileBasedImageElement(location: FileDescription) extends ImageElement {

  }

  case class LanguageMapBasedImageElement(
                                           languageMapContentId: LanguageMapContentId,
                                           copyrightInfo: CopyrightInfo,
                                           howToResolveUrl: URL_TYPE
                                         ) extends ImageElement {
        

  }

}


