package it.evadid.evacuation.html.elements

import org.scalajs.dom
import org.scalajs.dom.{Event, document}
import org.scalajs.dom.html.Input

trait ObservableInputElement[T]{


  def htmlType: String
  def defaultValue: T
  def id: String
  def attributes: Map[String, String]

  private val element: Input = {
    val field = document.createElement("input").asInstanceOf[Input]

    field.setAttribute("type", htmlType)
    field.setAttribute("value", String.valueOf(defaultValue))
    field.setAttribute("id", id)

    attributes.keys.foreach(key => field.setAttribute(key, attributes(key)))

    field.addEventListener("change", (e: Event) => handleOnChange(e))
    field
  }

  def getElement: Input = element

  def handleOnChange(e: dom.Event): Unit



}

object ObservableInputElement{

  type HtmlInputChangeListener[T] = HtmlInputChangedEvent[T] => Any

  case class HtmlInputChangedEvent[T](element: Input, oldValue: T, newValue: T)

}
