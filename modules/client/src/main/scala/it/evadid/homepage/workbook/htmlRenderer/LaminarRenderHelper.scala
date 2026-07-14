package it.evadid.homepage.workbook.htmlRenderer

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.TypeOfTextDisplay
import it.evadid.workbook.abstractions.TypeOfTextDisplay.{PLAINTEXT, PLAINTEXT_UNDERSCORE_REPLACABLE}
import org.scalajs.dom
import org.scalajs.dom.{MouseEvent, html}

case class LaminarRenderHelper() {

  /* Logging */
  val uiAndDomLogger: Logger = fullInfo.loggerSystemInfo.uiAndDomLogger

  /* Custom Elements */
  def createTooltip(text: String): Seq[Modifier[ReactiveHtmlElement[html.Element]]] = {
    Seq(
      cls := "custom-tooltip-target",
      dataAttr("tooltip") := text
    )
  }

  def createTooltip(text: Signal[String]): Seq[Modifier[ReactiveHtmlElement[html.Element]]] = {
    Seq(
      cls := "custom-tooltip-target",
      dataAttr("tooltip") <-- text
    )
  }

  def onClickedOutside(onClickedOutside: MouseEvent => Unit): Modifier[HtmlElement] = {
    onClick --> { (event: MouseEvent) =>
      val dialog = event.target.asInstanceOf[dom.html.Element]
      val rect = dialog.getBoundingClientRect()
      val clickedOutside = event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom
      if (clickedOutside) onClickedOutside(event)
    }
  }

  /* Signal Fetching */

  def plaintextStringSignal(contentIdAsString: String): Signal[String] = plaintextStringSignal(LanguageMapContentId.apply(contentIdAsString))

  def plaintextStringSignal(contentId: LanguageMapContentId): Signal[String] = contentIdStringSignal(contentId, PLAINTEXT, List())

  def contentIdStringSignal(contentIdAsString: String, typeOfTextDisplay: TypeOfTextDisplay, additionalInfo: List[String]): Signal[String] = {
    contentIdStringSignal(LanguageMapContentId.apply(contentIdAsString), typeOfTextDisplay, additionalInfo)
  }

  def contentIdStringSignal(contentId: LanguageMapContentId, typeOfTextDisplay: TypeOfTextDisplay, additionalInfo: List[String] = List()): Signal[String] = {
    val plaintext = HtmlFullWorkbookApp.fullInfo.signals.stringFromLanguageMapId(contentId)
    typeOfTextDisplay.match {
      case PLAINTEXT_UNDERSCORE_REPLACABLE => plaintext.map(str => replaceUnderscores(str, additionalInfo, Some(contentId)))
      case PLAINTEXT => plaintext
      case _ => {
        uiAndDomLogger.logWarn(s"LaminarRenderHelper::contentIdStringSignal for typeOfTextDisplay '${typeOfTextDisplay}' not implemented yet, treading type as plaintext instead'")
        plaintext
      }
    }
  }

  /* Helper */

  private def replaceUnderscores(plaintextUnderscoreReplaceable: String, additionalInfo: List[String], contentId: Option[LanguageMapContentId] = None): String = {
    val singleUnderscores = plaintextUnderscoreReplaceable.replaceAll("_+", "_")
    val parts = singleUnderscores.split("_")
    val toInsert = parts.size - 1

    val msgStart = s"LaminarRendererHelper::replaceUnderscores('${plaintextUnderscoreReplaceable}' ($toInsert underscores), ${additionalInfo.mkString("(", ", ", ")")})"

    if (toInsert == 0 && contentId.nonEmpty && plaintextUnderscoreReplaceable.contains(contentId.get.fullId)) {
      uiAndDomLogger.logInfo(msgStart + ": found  no underscores but the full id (so probably just loading).")
      plaintextUnderscoreReplaceable
    } else if (toInsert == 0) {
      uiAndDomLogger.logWarn(msgStart + ": found  no underscores, returning as ist. Maybe a wrong entry in LanguageMap?")
      plaintextUnderscoreReplaceable
    } else {
      //uiAndDomLogger.logInfo(msgStart + s": ${toInsert} to Insert, ${additionalInfo.size} elements, parts: ${parts.mkString("(",", ", ")")}")
      val result: String = parts.zipAll(additionalInfo, "[?]", "").map { case (a, b) => s"$a$b" }.mkString
      if (toInsert > 0 && additionalInfo.isEmpty) uiAndDomLogger.logWarn(msgStart + ": additional info is empty, filling everything up with '[?]'")
      else if (toInsert > additionalInfo.size) uiAndDomLogger.logWarn(msgStart + s": (${toInsert - additionalInfo.size} elements missing, filling up with '[?]'")
      else if (toInsert < additionalInfo.size) uiAndDomLogger.logWarn(msgStart + s": (provided ${additionalInfo.size - toInsert} unnecessary elements, those will be ignored)")
      else uiAndDomLogger.logInfo(msgStart + s" --> ${result}")
      result
    }
  }


}

object LaminarRenderHelper {


  val singleton: LaminarRenderHelper = LaminarRenderHelper()

}
