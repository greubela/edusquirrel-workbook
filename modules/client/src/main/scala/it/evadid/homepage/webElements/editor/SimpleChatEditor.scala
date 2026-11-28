package it.evadid.homepage.webElements.editor


import it.evadid.homepage.control.model.FullInfo

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.chat.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.InfoUtil
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.basic.HtmlButtonElement.ButtonConfig
import it.evadid.homepage.webElements.editor.SimpleChatEditor.{ChatEditorConfig, defaultConfig}
import it.evadid.homepage.webElements.editor.abstractions.SimpleWebEditor
import it.evadid.homepage.webElements.editor.abstractions.WebEditorConfig
import it.evadid.homepage.webElements.editor.config.TextEditorConfig

import java.time.Instant


object SimpleChatEditor {

  val drawUsersRight: Message => Boolean = _.author.role == SenderRole.USER

  case class ChatEditorConfig(drawRight: Message => Boolean) extends WebEditorConfig {
    protected def additionalCssClasses: List[String] = List("chat-editor")
  }

  val defaultConfig: ChatEditorConfig = ChatEditorConfig(drawUsersRight)

}

/** Chat editor bound to the answer Var. Its owner reacts to new messages by observing the answer; `bindings`
  * attach such observers to this editor's mount, so they end when the editor closes.
  */
case class SimpleChatEditor(fullInfo: FullInfo, answer: Var[MessengerModel], editorConfig: ChatEditorConfig = defaultConfig,
    bindings: Seq[Binder[Element]] = Seq.empty) extends SimpleWebEditor[MessengerModel, ChatEditorConfig] {

  private val messageInput = Var("")

  private val historyArea: Element = div(
    cls := "messenger-history",
    children <-- answer.signal.map(_.orderedMessages.map(renderMessage))
  )

  private lazy val sendButton: HtmlAppElement = HtmlButtonElement.withTextLabel(fullInfo, "basic/messengerEditorSendMessageButton", _ => sendCurrentMessage(), ButtonConfig(true, List("messenger-send-button")))
  private lazy val textEditorConfig: TextEditorConfig = TextEditorConfig(false, 2, 80, LanguageMapContentId("basic/messengereditorinputplaceholder"), List("messenger-input"))
  private lazy val inputEditor: SimpleTextEditor = SimpleTextEditor(fullInfo, messageInput, Var(textEditorConfig))

  private lazy val inputArea: Element = {
    div(
      cls := "messenger-composer",
      inputEditor.getDomElement(),
      sendButton.getDomElement()
    )
  }
  private def renderMessage(message: Message): Element = try {
    val cssStrPosition = if (editorConfig.drawRight(message)) "message-position-left" else "message-position-right"
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

  private val domElement: Element = div(cls := "messenger-editor", historyArea, inputArea, bindings)

  override def getDomElement(): Element = domElement

  override val underlyingVar: Var[MessengerModel] = answer

  override val config: Var[ChatEditorConfig] = Var(editorConfig)

  private def sendCurrentMessage(): Unit = {
    val trimmed = messageInput.now().trim
    if (trimmed.nonEmpty) {
      answer.update(_.addMessage(
        text = trimmed,
        author = ChatParticipant("Student", "it.evadid.student", SenderRole.USER, None),
        timestamp = Instant.now()
      ))
      messageInput.set("")
    }
  }


  private val fallbackAvatar: SvgElement =
    svg.svg(
      svg.viewBox := "0 0 40 40",
      svg.cls := "chat-avatar",
      svg.circle(
        svg.cx := "20",
        svg.cy := "20",
        svg.r := "16"
      )
    )


}
