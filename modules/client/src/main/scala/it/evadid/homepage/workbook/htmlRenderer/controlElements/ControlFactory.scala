package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L.Signal
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo

trait ControlFactory {

  def fullInfo: FullInfo = HtmlFullWorkbookApp.fullInfo
  def labelString(id: String): Signal[String] = fullInfo.signals.stringFromLanguageMapId(LanguageMapContentId(id))
  def labelString(contentId: LanguageMapContentId): Signal[String] = fullInfo.signals.stringFromLanguageMapId(contentId)



}
