package contentmanagement.webElements.genericHtmlElements.editor

import com.raquo.laminar.api.L.*
import contentmanagement.model.chat.MessengerModel
import contentmanagement.model.chat.MessengerModel.{Message, SenderRole}
import contentmanagement.model.language.AppLanguage
import contentmanagement.webElements.HtmlAppElement
import workbook.model.interaction.InteractionVariable.*
import workbook.model.interaction.*
import workbook.model.interaction.history.UpdateImportance
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement
import scala.scalajs.js

case class SimpleMessengerEditor(chatExercise: InteractionVariable[MessengerModel]) extends HtmlAppElement {

  private val messageInput = Var("")

  private val domElement: Element = {
    div(
      cls := "messanger-container",
      div(
        cls := "messenger-editor",
        div(
          cls := "messenger-history",
          children <-- chatExercise.interactionSignal.map(_.orderedMessages.map(renderMessage))
        ),
        div(
          cls := "messenger-composer",
          textArea(
            cls := "messenger-input",
            rows := 2,
            placeholder := "Type your message...",
            controlled(
              value <-- messageInput.signal,
              onInput.mapToValue --> messageInput
            ),
            onKeyDown.filter(ev => ev.key == "Enter" && !ev.shiftKey) --> { ev =>
              ev.preventDefault()
              sendCurrentMessage()
            }
          ),
          button(
            cls := "messenger-send-button",
            "Send",
            onClick --> { _ => sendCurrentMessage() }
          )
        )
      )
    )
  }

  override def getDomElement(): Element = domElement

  def onUserAddedMessage(message: String): Unit = {
    println("message send :)")
  }

  private def sendCurrentMessage(): Unit = {
    val trimmed = messageInput.now().trim
    if (trimmed.nonEmpty) {
      onUserAddedMessage(trimmed)
      val currentState = chatExercise.currentValue
      val nextState = currentState.addMessage(
        text = trimmed,
        author = MessengerModel.BasicPerson(name = contentmanagement.model.language.LanguageMap.universalMap("Me")),
        senderRole = SenderRole.USER,
        timestampEpochMillis = System.currentTimeMillis()
      )
      chatExercise.updateStateFromUserInteraction(nextState, System.currentTimeMillis(), UpdateImportance.MAJOR)
      messageInput.set("")
    }
  }

  private def renderMessage(message: Message): Element = {
    val isUserMessage = message.senderRole == SenderRole.USER
    val rowClass = if (isUserMessage) "messenger-message-row-user" else "messenger-message-row-teacher"
    val bubbleClass = if (isUserMessage) "messenger-bubble-user" else "messenger-bubble-teacher"

    div(
      cls := s"messenger-message-row $rowClass",
      div(
        cls := "messenger-avatar",
        avatarSvg
      ),
      div(
        cls := "messenger-message-content",
        div(
          cls := "messenger-author",
          message.author.name.getInLanguage(AppLanguage.default())
        ),
        div(
          cls := s"messenger-bubble $bubbleClass",
          message.text
        ),
        div(
          cls := "messenger-timestamp",
          formatTimestamp(message.timestampEpochMillis)
        )
      )
    )
  }

  private val avatarSvg: SvgElement =
    svg.svg(
      svg.viewBox := "0 0 40 40",
      svg.width := "40",
      svg.height := "40",
      svg.circle(
        svg.cx := "20",
        svg.cy := "20",
        svg.r := "16",
        svg.fill := "#d7dce3"
      )
    )

  private def formatTimestamp(timestampEpochMillis: String): String = {
    timestampEpochMillis.toLongOption
      .map { epoch =>
        val date = new js.Date(epoch.toDouble)
        f"${date.getHours().toInt}%02d:${date.getMinutes().toInt}%02d"
      }
      .getOrElse(timestampEpochMillis)
    //timestampEpochMillis + " (<- formatted!)"
  }
}
