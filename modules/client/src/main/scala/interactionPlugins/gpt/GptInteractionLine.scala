package interactionPlugins.gpt

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.core.datastructures.state.State
import workbook.model.abstractions.{HtmlWorkbookElement, WorkbookInteraction}
import workbook.model.info.{FullInfo, HomepageInfo}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.*

case class GptInteractionLine(
                               fullInfo: FullInfo,
                               textInteraction: WorkbookInteraction[String],
                               languageMapIDExerciseText: String,
                               languageMapIdScaffoldingHints: Option[String] = None,
                               languageMapIdGradingHints: Option[String] = None,
                             ) extends HtmlWorkbookElement {

  
  override lazy val workbookChildren: List[HtmlWorkbookElement] = List(htmlGPTMessenger).flatten
  
  //private var htmlGptGrader = HtmlGptGrader(fullInfo, textInteraction)
  
  val htmlGPTMessenger: Option[HtmlGPTMessenger] = languageMapIdScaffoldingHints.map(hintID => HtmlGPTMessenger(fullInfo, textInteraction, languageMapIDExerciseText, hintID))

  lazy val domElement: L.Element = {
    div(
      cls := "button-line",
      children <-- Var(workbookChildren.map(_.getDomElement())).signal
    )
  }

  override def getDomElement(): L.Element = domElement


}
