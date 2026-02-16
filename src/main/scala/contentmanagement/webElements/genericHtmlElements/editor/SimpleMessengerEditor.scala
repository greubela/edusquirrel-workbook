package contentmanagement.webElements.genericHtmlElements.editor

import com.raquo.laminar.api.*
import com.raquo.laminar.api.L.*
import contentmanagement.model.chat.MessengerModel
import workbook.model.exercise.InteractionVariable
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

case class SimpleMessengerEditor(chatExercise: InteractionVariable[MessengerModel]) extends HtmlWorkbookElement {

  private val domElement: Element = div(???)

  override def getDomElement(): L.Element = domElement

  def onUserAddedMessage(message: String): Unit = {
    println("message send :)")
  }


}
