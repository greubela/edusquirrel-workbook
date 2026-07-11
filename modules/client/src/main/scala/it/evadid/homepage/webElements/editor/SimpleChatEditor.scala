package it.evadid.homepage.webElements.editor

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.chat.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.core.util.InfoUtil
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.basic.HtmlButtonElement.ButtonConfig
import it.evadid.homepage.webElements.editor.SimpleChatEditor.{ChatEditorConfig, defaultConfig}
import it.evadid.homepage.webElements.editor.SimpleTextEditor.TextEditorConfig

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

  private lazy val sendButton: HtmlAppElement = HtmlButtonElement.withTextLabel("basic/messengerEditorSendMessageButton", _ => sendCurrentMessage(), ButtonConfig(true, List("messenger-send-button")))
  private lazy val textEditorConfig: TextEditorConfig = TextEditorConfig(false, 2, 80, LanguageMapContentId("basic/messengerEditorInputPlaceholder"), List("messenger-input"))
  private lazy val inputEditor: SimpleTextEditor = SimpleTextEditor(messageInput, Var(textEditorConfig))

  private lazy val inputArea: Element = {
    div(
      cls := "messenger-composer",
      inputEditor.getDomElement(),
      sendButton.getDomElement()
    )
  }
  /*
   controlled(
onKeyDown.filter(ev => ev.key == "Enter" && !ev.shiftKey) --> { ev =>
       ev.preventDefault()
       sendCurrentMessage()
     }
     )
    */

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
