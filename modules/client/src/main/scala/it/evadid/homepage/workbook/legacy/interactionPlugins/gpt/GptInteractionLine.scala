package it.evadid.homepage.workbook.legacy.interactionPlugins.gpt

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.core.datastructures.state.State
import it.evadid.homepage.workbook.legacy.model.abstractions.HtmlWorkbookElement
import it.evadid.homepage.workbook.legacy.model.info.{FullInfo, HomepageInfo}
import it.evadid.workbook.model.interaction.WorkbookInteraction

import scala.concurrent.{ExecutionContext, Future}
import scala.util.*

case class GptInteractionLine(
                               fullInfo: FullInfo,
                               textInteraction: WorkbookInteraction[String],
                               languageMapIDExerciseText: String,
                               languageMapIdScaffoldingHints: Option[String] = None,
                               languageMapIdGradingHints: Option[String] = None,
                             ) extends HtmlWorkbookElement {


  //private var htmlGptGrader = HtmlGptGrader(fullInfo, textInteraction)
  
  val htmlGPTMessenger: Option[HtmlGPTMessenger] = languageMapIdScaffoldingHints.map(hintID => HtmlGPTMessenger(fullInfo, textInteraction, languageMapIDExerciseText, hintID))



  lazy val domElement: L.Element = {
    div(
      cls := "button-line",
      div("GptInteractionLine::domElement unfinished :(")
  //    children <-- Var(workbookChildren.map(_.getDomElement())).signal
    )
  }

  override def getDomElement(): L.Element = domElement


}
