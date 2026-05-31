package it.evadid.homepage.workbook.legacy.model.abstractions

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.homepage.HtmlAppElement
import it.evadid.homepage.workbook.legacy.model.info.{FullInfo, HomepageInfo, UserConfig}
import it.evadid.workbook.model.interaction.WorkbookInteraction
import it.evadid.workbook.model.interaction.sync.SyncInformation
import sourcecode.Text.generate

import scala.concurrent.{ExecutionContext, Future}

trait HtmlWorkbookElement extends HtmlAppElement {
  def fullInfo: FullInfo
  
  def loadScaffoldingInformation(languageMapIdExerciseText: String, languageMapIdAdditionalHints: String): Future[ScaffoldingInformation[?]] = {
    println("should not call loadScaffoldingInformation on plain HtmlWorkbookElement!")
    Future.failed(new IllegalArgumentException("Do not call HtmlWorkbookElement::loadScaffoldingInformation, but only on WorkbookInteraction"))
  }

}


