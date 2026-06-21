package it.evadid.evacuation.html.elements

import it.evadid.evacuation.html.elements.ObservableInputElement.{HtmlInputChangeListener, HtmlInputChangedEvent}
import org.scalajs.dom

case class BasicObservableInputElement[T](id: String, htmlType: String, defaultValue: T, onChange: HtmlInputChangeListener[T], parseContent: String => Either[T, Exception], attributes: Map[String, String]) extends ObservableInputElement[T] {

  private var curValue = defaultValue

  override def handleOnChange(event: dom.Event): Unit = {
    val parserResult = parseContent(getElement.value)
    parserResult match {
      case Left(parsedValue) => {
        val oldValue = curValue
        curValue = parsedValue
        onChange.apply(HtmlInputChangedEvent(getElement, oldValue, parsedValue))
      }
      case Right(exception) => {
        println("[WARNING] cannot parse value <" + getElement.value + "> for input type " + htmlType + "(element " + id + ")")
      }
    }
  }

}

