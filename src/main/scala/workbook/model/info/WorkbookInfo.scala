package workbook.model.info

import datastructures.core.language.{HumanLanguage, LanguageMap}
import datastructures.web.file.{FileDescription, LoadedFile}
import datastructures.web.storage.AsyncDataCache
import workbook.model.abstractions.WorkbookInteraction
import workbook.user.User
import workbook.htmlElements.container.HtmlFullScreenContainerElement
import workbook.singletons.WorkbookLanguageInfo.{LabelLanguageMapStorage, LanguageMapTriplesStorage}

case class WorkbookInfo(
                         availableLanguages: List[HumanLanguage],
                         config: WorkbookConfig,
                         estimatedDurations: Map[WorkbookInteraction[_], Double]
                       ) {


  
  
  
}


