package it.evadid.homepage.workbook.htmlRenderer.interactionEditors

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.chat.*
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.homepage.webElements.HtmlAppElement

import java.time.LocalDateTime

//todo: to State[Var]... aber problematisch mit listener. später (:
case class HtmlSimpleChatEditor(interactionVar: State[MessengerModel], onUserAddedMessage: MessengerModel => Any) extends HtmlAppElement {


  private val messageInput = Var("")


  private val domElement: Element = {
    div(
      cls := "messanger-container",
      div(
        cls := "messenger-editor",
        div(
          cls := "messenger-history",
          //children <-- Var(List(div("SimpleMessengerEditor::domElement unfinished :("))).signal
          children <-- interactionVar.toAirstreamVar.signal.map(_.orderedMessages.map(renderMessage))
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

  private def renderMessage(message: Message): Element = {
    val isUserMessage = message.author.role == SenderRole.USER
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
          message.author.name
        ),
        div(
          cls := s"messenger-bubble $bubbleClass",
          message.text
        ),
        div(
          cls := "messenger-timestamp",
          message.timestamp.toString
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


}
