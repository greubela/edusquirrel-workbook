package it.evadid.homepage.webElements.editor

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.chat.{Message, MessengerModel, Person, SenderRole}
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.core.util.InfoUtil
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.SimpleChatEditor.{ChatEditorConfig, defaultConfig}

import java.time.LocalDateTime


object SimpleChatEditor {

  val drawUsersRight: Message => Boolean = _.author.role == SenderRole.USER

  case class ChatEditorConfig(drawRight: Message => Boolean)

  val defaultConfig: ChatEditorConfig = ChatEditorConfig(drawUsersRight)

}

//todo: to State[Var]... aber problematisch mit listener. später (:
case class SimpleChatEditor(interactionVar: State[MessengerModel], messageInputState: State[String], onUserAddedMessage: MessengerModel => Any, config: ChatEditorConfig = defaultConfig) extends HtmlAppElement {

  private val messageInput = messageInputState.toAirstreamVar

  private lazy val historyArea: Element = {
    div(
      cls := "messenger-history",
      //children <-- Var(List(div("SimpleMessengerEditor::domElement unfinished :("))).signal
      children <-- interactionVar.toAirstreamVar.signal.map(_.orderedMessages.map(renderMessage))
    )
  }

  private lazy val inputArea: Element = {
    div(
      cls := "messenger-composer",
      textArea(
        cls := "messenger-input",
        rows := 2,
        placeholder <-- laminarHelper.plaintextStringSignal("basic/messengerEditorInputPlaceholder"),
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
        text <-- laminarHelper.plaintextStringSignal("basic/messengerEditorSendMessageButton"),
        onClick --> { _ => sendCurrentMessage() }
      )
    )
  }

  private def renderMessage(message: Message): Element = try {
    val cssStrPosition = if (config.drawRight(message)) "message-position-left" else "message-position-right"
    val cssStrRole = s"message-author-role-${message.author.role}"

    div(
      cls := s"messenger-message ${cssStrPosition} ${cssStrRole}",
      div(cls := "author-avatar", message.author.abbreviation.map(span(_)).getOrElse(fallbackAvatar)),
      div(
        cls := s"message-content",
        div(cls := s"message-author", message.author.name),
        div(cls := s"message-text", message.text),
        div(cls := "message-timestamp", InfoUtil.datetimeFormattedForHumans(message.timestamp))
      )
    )
  } catch case (e: Throwable) => {
    e.printStackTrace()
    span("error rendering msg: " + e.getMessage)
  }

  private val domElement: Element = {
    div(
      cls := "messenger-editor",
      historyArea,
      inputArea,
    )
  }

  override def getDomElement(): Element = domElement

  private def sendCurrentMessage(): Unit = {
    val trimmed = messageInput.now().trim
    if (trimmed.nonEmpty) {
      val currentState = interactionVar.now()
      val nextState = currentState.addMessage(
        text = trimmed,
        author = Person("Student", "it.evadid.student", SenderRole.USER, None),
        timestamp = LocalDateTime.now()
      )
      interactionVar.set(nextState) //setStateFromUserInteraction(nextState, UpdateImportance.MAJOR)
      messageInput.set("")
      onUserAddedMessage(nextState)
    }
  }


  private val fallbackAvatar: SvgElement =
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


}
