package it.evadid.evacuation.html.elements

import it.evadid.evacuation.core.graphic.model.EvaFileInformation
import org.scalajs.dom
import org.scalajs.dom.html.Input
import org.scalajs.dom.{Event, document}

import scala.scalajs.js.typedarray.{ArrayBuffer, DataView}

case class ObservableFileInputElement(id: String, onFileSelected: EvaFileInformation => Any, attributes: Map[String, String]) {

  def getElement: Input = element

  private val element: Input = {
    val field = document.createElement("input").asInstanceOf[Input]
    attributes.keys.foreach(key => field.setAttribute(key, attributes(key)))

    field.setAttribute("type", "file")
    field.setAttribute("id", id)

    field.addEventListener("change", (e: Event) => handleOnChange(e))
    field
  }

  def handleOnChange(e: Event): Unit = {
    val reader = new dom.FileReader()
    val file = getElement.files(0)
    reader.readAsArrayBuffer(file)

    reader.onload = (e: dom.Event) => {
      val data = new DataView(reader.result.asInstanceOf[ArrayBuffer])
      val res = new Array[Byte](data.byteLength)
      for (index <- 0 until data.byteLength) {
        res(index) = data.getInt8(index)
      }
      onFileSelected(EvaFileInformation(file.name, res))
    }
  }


}

object ObservableFileInputElement {


}
