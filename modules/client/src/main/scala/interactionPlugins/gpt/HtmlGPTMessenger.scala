package interactionPlugins.gpt

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.svg
import com.raquo.laminar.nodes.ReactiveSvgElement
import contentmanagement.webElements.genericHtmlElements.editor.SimpleMessengerEditor
import it.evadid.core.datastructures.chat.SenderRole.WORKBOOK
import it.evadid.core.datastructures.chat.{Message, MessengerModel, SenderRole, Message as errText}
import it.evadid.core.datastructures.language.AppLanguage.{Danish, English, German, HumanLanguage}
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.core.util.io.Serializer
import it.evadid.distribution.commandTypes.LLMCommands
import it.evadid.distribution.commandTypes.LLMCommands.MessengerChatCompletionRequest
import it.evadid.util.Logger
import org.scalajs.dom.SVGSVGElement
import workbook.htmlElements.basic.*
import workbook.model.abstractions.{ScaffoldingInformation, WorkbookInteraction}
import workbook.model.info.FullInfo
import workbook.model.interaction.*
import workbook.model.interaction.history.UpdateImportance.MAJOR

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.*

case class HtmlGPTMessenger(
                             fullInfo: FullInfo,
                             textInteraction: WorkbookInteraction[String],
                             languageMapIdExerciseText: String,
                             languageMapIdScaffoldingInfo: String
                           ) extends WorkbookInteraction[MessengerModel] {

  override def id: String = textInteraction.id + "_scaffolding"

  private val systemPromptId: String = "prompts/scaffolding-system-prompt"


  override val defaultValue: MessengerModel = MessengerModel.apply(List())

  val interactionVariable: InteractionVariable[MessengerModel] = {
    val res = InteractionVariable[MessengerModel](this, Serializer.messengerIo)
    HtmlGPTMessenger.seedMessenger(textInteraction, res, languageMapIdExerciseText, languageMapIdScaffoldingInfo)
    res
  }

  private def sendError(err: Throwable): Unit = interactionVariable.synchronized {
    println(s"error while sending message to LLM: ${err.getMessage}\n    ${err.getStackTrace.mkString("\n    ")}")
    val errText: String = s"@student: Unfortunately, I could not generate an answer. The error I got was ${err.getMessage}. I printed additional information on the browser console!"
    val errMsg = Message(errText, LLMCommands.workbookPerson, LocalDateTime.now())
    interactionVariable.updateStateFromUserInteraction(_.addMessage(errMsg), MAJOR)
  }

  private def onUserSendMessage(messageState: MessengerModel): Unit = {
    val systemPromptFuture = fullInfo.technical.languageMapStorage.loadAsFuture(systemPromptId)(using ExecutionContext.global)
    val curValTextarea = textInteraction.interactionVariable.currentValue
    val inputStr = if (curValTextarea.trim.nonEmpty) s"@assistant: the textarea for the solution reads '$curValTextarea'" else s"@assistant: currently no text in solution area"
    val languageStr = s", please answer in ${fullInfo.signals.currentLanguage.now()}"
    val currentStateMsg = Message(inputStr + languageStr, LLMCommands.workbookPerson, LocalDateTime.now())
    val nextMessageState = messageState.addMessage(currentStateMsg)

    val requestFuture = systemPromptFuture.map { systemPrompt => MessengerChatCompletionRequest(systemPrompt.getWithLanguagePreference(LLMCommands.langPreference), nextMessageState) }(using ExecutionContext.global)
    LLMCommands.completeLLMCommandFactory.waitAndSendCommandTo(fullInfo.technical.backendServerExecutor, Logger(), requestFuture).onComplete {
      case Success(result) if result.result.isSuccess => interactionVariable.setStateFromUserInteraction(result.typedResult.get.result, MAJOR)
      case Success(result) if result.result.isFailure => sendError(new Exception("Received invalid result from worker", result.result.failed.get))
      case Failure(err) => sendError(err)
      case e: Any => sendError(new Exception("Received invalid result from worker: " + e.toString)) // should be unrechable
    }(using ExecutionContext.global)
  }

  private val scaffoldingEditor = SimpleMessengerEditor(interactionVariable, onUserSendMessage)

  private val scaffoldingButton = HtmlButtonElement.withSvgContent(fullInfo, HtmlGPTMessenger.scaffoldingButtonSvg, event => {
    fullInfo.technical.makeFullscreen(scaffoldingEditor)
  })

  private val domElement: L.Element = scaffoldingButton.getDomElement()

  override def getDomElement(): L.Element = domElement

}

object HtmlGPTMessenger {

  private def seedMessenger(textInteraction: WorkbookInteraction[String], interactionVariable: InteractionVariable[MessengerModel], languageMapIdExerciseText: String, languageMapIdScaffoldingInfo: String): Unit = {

    val scaffoldingInformation: Future[ScaffoldingInformation[String]] = textInteraction.loadScaffoldingInformation(languageMapIdExerciseText, languageMapIdScaffoldingInfo)

    scaffoldingInformation.onComplete {
      case Success(scaffoldingInfo) => {
        val exText: String = scaffoldingInfo.exerciseText.getWithLanguagePreference(LLMCommands.langPreference)
        val msg1: Message = Message("@assistant: the current exercise has the following instruction:\n" + exText, LLMCommands.workbookPerson, LocalDateTime.now())

        val scText: String = scaffoldingInfo.additionalScaffolds.getWithLanguagePreference(LLMCommands.langPreference)
        val msg2 = Message("@assistant: please note while giving feedback to the student: " + scText, LLMCommands.workbookPerson, LocalDateTime.now())

        if (!interactionVariable.currentValue.messages.exists(_.author.role == WORKBOOK)) {
          interactionVariable.updateStateFromUserInteraction(_.addMessage(msg1).addMessage(msg2), MAJOR)
        }
      }
      case _ => {
        val errText1: String = s"@teacher: I could not load the instruction and hints based on the languageMapIDs '$languageMapIdExerciseText' and '$languageMapIdScaffoldingInfo'. Please analyze whether you made an error setting up the workbook"
        val errText2: String = s"@student: Due to an internal error, I do not know what exercise you´re currently working on. Please copy the exercise text into this box before asking your question!"
        val msg1: Message = Message(errText1, LLMCommands.workbookPerson, LocalDateTime.now())
        val msg2: Message = Message(errText2, LLMCommands.workbookPerson, LocalDateTime.now())

        if (!interactionVariable.currentValue.messages.exists(_.author.role == WORKBOOK)) {
          interactionVariable.updateStateFromUserInteraction(_.addMessage(msg1).addMessage(msg2), MAJOR)
        }
      }
    }(using ExecutionContext.global)

  }


  def scaffoldingButtonSvg: ReactiveSvgElement[SVGSVGElement] = {
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

}