package it.evadid.homepage.webElements.editor.config

import it.evadid.core.datastructures.chat.Message
import it.evadid.homepage.webElements.editor.abstractions.WebEditorConfig

case class ChatEditorConfig(drawRight: Message => Boolean) extends WebEditorConfig {

  protected def additionalCssClasses: List[String] = List("chat-editor")

}

object ChatEditorConfig {

  val defaultConfig: ChatEditorConfig = ChatEditorConfig(Message => true)

}
