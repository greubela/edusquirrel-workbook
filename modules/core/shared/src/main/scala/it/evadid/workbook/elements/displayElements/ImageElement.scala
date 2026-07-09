package it.evadid.workbook.elements.displayElements

import it.evadid.core.datastructures.file.{CopyrightInfo, FileDescription}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.model.abstractions.TypeOfTextDisplay.*
import it.evadid.workbook.model.abstractions.{WorkbookDisplayElement, WorkbookElement}

sealed trait ImageElement extends WorkbookDisplayElement {


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


