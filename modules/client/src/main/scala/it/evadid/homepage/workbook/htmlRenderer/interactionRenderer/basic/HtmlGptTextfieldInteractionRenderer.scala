package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import it.evadid.workbook.interaction.state.InteractionSignals.*

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.core.datastructures.chat.{ChatParticipant, Message, MessengerModel, SenderRole}
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMapContentId

import it.evadid.distribution.commandTypes.LLMCommands
import it.evadid.distribution.commandTypes.LLMCommands.MessengerChatCompletionRequest
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.SimpleChatEditor
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.basic.MessagingInteraction.MessengerModelScaffolding
import it.evadid.workbook.elements.interactionElements.gpt.GptInteractionElement
import it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR
import org.scalajs.dom.SVGSVGElement

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object HtmlGptTextfieldInteractionRenderer extends LineBasedRenderingFactory[GptInteractionElement] {

  private val langPreferences: Seq[HumanLanguage] = List(English, German, Danish)

  private val systemPromptId: LanguageMapContentId = LanguageMapContentId("prompts/scaffolding-system-prompt")

  private def sendError(fullInfo: FullInfo, err: Throwable, mmState: Var[MessengerModel]): Unit = {
    laminarHelper(fullInfo).uiAndDomLogger.logExceptionWarn(s"error while sending message to LLM, a error message will appear in the chat", err)
    val errText: String = s"@student: Unfortunately, I could not generate an answer. The error I got was ${err.getMessage}. I printed additional information on the browser console!"
    val errMsg = Message(errText, MessengerModel.pWorkbook, LocalDateTime.now())
    mmState.update(_.addMessage(errMsg))
  }

  private def requestCompletion(fullInfo: FullInfo, messageState: MessengerModel, mmState: Var[MessengerModel]): Unit = {
    val systemPromptFuture = fullInfo.workbookContextInformation.languageMapIdResolver.get.resolveMap(systemPromptId)
    val requestFuture = systemPromptFuture.map { systemPrompt => MessengerChatCompletionRequest(systemPrompt.getWithLanguagePreference(langPreferences), messageState, fullInfo.workbookContextInformation.llmConfiguration) }(using ExecutionContext.global)
    LLMCommands.completeLLMCommandFactory.waitAndSendCommandTo(fullInfo.backendExecutor, requestFuture, None).onComplete {
      case Success(result) => mmState.update(_.addMessage(result.result))
      case Failure(err) => sendError(fullInfo, err, mmState)
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

  override protected def createRendering(fullInfo: FullInfo, workbookElement: GptInteractionElement): AtomarLineRendering = {

    // The chat button exists only for exercises with a scaffolding conversation.
    val elements: Seq[Element] = workbookElement.scaffoldingInteractionOp.toList.map { scaffolding =>
      val chat: Var[MessengerModel] = fullInfo.interactions.boundVar(scaffolding, MAJOR)
        .bimap(_.messengerModel)(MessengerModelScaffolding.apply)
      // While the chat is open, every message the student adds asks the assistant for a reply.
      def openChat(): SimpleChatEditor = SimpleChatEditor(fullInfo, chat, bindings = Seq(
        chat.signal.changes.filter(_.orderedMessages.lastOption.exists(_.author.role == SenderRole.USER)) --> (requestCompletion(fullInfo, _, chat))))
      val openChatButton = HtmlButtonElement.withSvgContent(createScaffoldingButtonSvg(), event => {

        val currentUser: ChatParticipant = fullInfo.current.userInfo.map(_.user).getOrElse(MessengerModel.pFallbackStudent)

        workbookElement.initScaffoldingIfEmpty(fullInfo.interactions, currentUser, fullInfo.workbookContextInformation.languageMapIdResolver.get).onComplete {
          case Success(bool) => if (bool) requestCompletion(fullInfo, chat.now(), chat)
          case e: Any => fullInfo.loggerSystemInfo.workbookElementLogger.logWarn(s"HtmlGptTextfieldInteractionRenderer::createRendering -> error of some sort..: ${e.toString}")
        }(using ExecutionContext.global)
        fullInfo.displayControl.setFullscreen(openChat())
      })
      openChatButton.getDomElement()
    }

    AtomarLineRendering.basicLine(fullInfo, workbookElement, elements, "button-line")
  }

}
