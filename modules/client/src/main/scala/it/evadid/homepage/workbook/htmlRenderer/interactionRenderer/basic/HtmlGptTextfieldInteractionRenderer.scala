package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.core.datastructures.chat.{Message, MessengerModel, Person}
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.State
import it.evadid.distribution.commandTypes.LLMCommands
import it.evadid.distribution.commandTypes.LLMCommands.MessengerChatCompletionRequest
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.SimpleChatEditor
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.basic.MessagingInteraction.MessengerModelScaffolding
import it.evadid.workbook.elements.interactionElements.gpt.GptInteractionElement
import it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR
import it.evadid.workbook.interaction.variable.InteractionVariable
import org.scalajs.dom.SVGSVGElement

import java.time.LocalDateTime
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object HtmlGptTextfieldInteractionRenderer extends LineBasedRenderingFactory[GptInteractionElement] {

  private val langPreferences: List[HumanLanguage] = List(English, German, Danish)

  private val systemPromptId: LanguageMapContentId = LanguageMapContentId("prompts/scaffolding-system-prompt")

  private def sendError(err: Throwable, mmState: State[MessengerModel]): Unit = {
    laminarHelper.uiAndDomLogger.logExceptionWarn(s"error while sending message to LLM, a error message will appear in the chat", err)
    val errText: String = s"@student: Unfortunately, I could not generate an answer. The error I got was ${err.getMessage}. I printed additional information on the browser console!"
    val errMsg = Message(errText, MessengerModel.pWorkbook, LocalDateTime.now())
    mmState.update(_.addMessage(errMsg))
  }

  private def onUserSendMessage(messageState: MessengerModel, mmState: State[MessengerModel]): Unit = {
    requestCompletion(messageState, mmState)
  }

  private def requestCompletion(messageState: MessengerModel, mmState: State[MessengerModel]): Unit = {
    val systemPromptFuture = fullInfo.signals.langMapIdResolver.resolveMap(systemPromptId)
    /*val curValTextarea = textInteraction.interactionVariable.currentValue
    val inputStr = if (curValTextarea.trim.nonEmpty) s"@assistant: the textarea for the solution reads '$curValTextarea'" else s"@assistant: currently no text in solution area"
    val languageStr = s", please answer in ${fullInfo.signals.currentLanguage.now()}"
    val currentStateMsg = Message(inputStr + languageStr, LLMCommands.workbookPerson, LocalDateTime.now())
    val nextMessageState = messageState.addMessage(currentStateMsg)*/

    val requestFuture = systemPromptFuture.map { systemPrompt => MessengerChatCompletionRequest(systemPrompt.getWithLanguagePreference(langPreferences), messageState) }(using ExecutionContext.global)
    LLMCommands.completeLLMCommandFactory.waitAndSendCommandTo(fullInfo.backendExecutor, requestFuture, None).onComplete {
      case Success(result) => mmState.update(_.addMessage(result.resultTyped.result))
      case Failure(err) => sendError(err, mmState)
    }(using ExecutionContext.global)

  }


  def createScaffoldingButtonSvg(): ReactiveSvgElement[SVGSVGElement] = {
    svg.svg(
      svg.cls := "button-show-scaffolder",
      svg.viewBox := "0 0 24 24",
      svg.path(
        svg.cls := "button-fill",
        svg.d := "M21 12C21 16.9706 16.9706 21 12 21C7.02944 21 3 16.9706 3 12C3 7.02944 7.02944 3 12 3C16.9706 3 21 7.02944 21 12Z"
        //svg.d := "M17 9A5 5 0 0 0 7 9a1 1 0 0 0 2 0 3 3 0 1 1 3 3 1 1 0 0 0-1 1v2a1 1 0 0 0 2 0v-1.1A5 5 0 0 0 17 9z"
      ),
      svg.path(
        svg.d := "M10.5 8.67709C10.8665 8.26188 11.4027 8 12 8C13.1046 8 14 8.89543 14 10C14 10.9337 13.3601 11.718 12.4949 11.9383C12.2273 12.0064 12 12.2239 12 12.5V12.5V13",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round"),
      svg.path(
        svg.d := "M12 16H12.01",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round")
    )
  }

  override protected def createRendering(workbookElement: GptInteractionElement): AtomarLineRendering = {

    val elements: mutable.ListBuffer[Element] = mutable.ListBuffer()

    val interactionVariable: InteractionVariable[MessengerModelScaffolding] = workbookElement.scaffoldingInteractionOp.get.interactionVariable
    if (workbookElement.scaffoldingInteractionOp.nonEmpty) {
      val boundState = interactionVariable
        .createBoundStateWithUpdateImportance(fullInfo.syncControl, MAJOR)
        .biMap(_.messengerModel, MessengerModelScaffolding.apply)
      val scaffoldingChat = SimpleChatEditor(boundState, State(""), msg => onUserSendMessage(msg, boundState))
      val openChatButton = HtmlButtonElement.withSvgContent(createScaffoldingButtonSvg(), event => {

        val currentUser: Person = fullInfo.current.userInfo.map(_.user).getOrElse(MessengerModel.pFallbackStudent)

        workbookElement.initScaffoldingIfEmpty(currentUser,fullInfo.syncControl, fullInfo.signals.langMapIdResolver).onComplete {
          case Success(bool) => if (bool) requestCompletion(workbookElement.scaffoldingInteractionOp.get.interactionVariable.currentValue.messengerModel, boundState)
          case e: Any => fullInfo.loggerSystemInfo.workbookElementLogger.logWarn(s"HtmlGptTextfieldInteractionRenderer::createRendering -> error of some sort..: ${e.toString}")
        }(using ExecutionContext.global)
        fullInfo.displayControl.setFullscreen(scaffoldingChat)
      })
      elements += openChatButton.getDomElement()
    }

    val elementsFinished: List[Element] = elements.toList
    AtomarLineRendering.basicLine(workbookElement, elementsFinished, "button-line")
  }

}
