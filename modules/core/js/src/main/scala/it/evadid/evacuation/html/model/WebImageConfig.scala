package it.evadid.evacuation.html.model

import org.scalajs.dom
import org.scalajs.dom.{DragEvent, Element, MouseEvent, document}

case class WebImageConfig(imgId: String, imgSrc: String, imgDescription: String, mouseEvents: Map[String, MouseEvent => Any], dragEvents: Map[String, DragEvent => Any], additionalAttributes: Map[String, String], asSVG: Boolean = false) {

  def createImageInDiv(divAttributes: Map[String, String] = Map()): Element = {

    val res = document.createElement("div")
    res.setAttribute("id", imgId)
    additionalAttributes.foreach(tup => res.setAttribute(tup._1, tup._2))

    res.appendChild( createImage(false) )
    res

  }

  def createImage(withID: Boolean = true): Element = {
    //val img = if(asSVG) document.createElement("svg") else document.createElement("img")
    val img = document.createElement("img")
    if(withID)    img.setAttribute("id", imgId)
    img.setAttribute("name", imgId)
    img.setAttribute("src", imgSrc)
    img.setAttribute("alt", imgDescription)

    additionalAttributes.foreach(tup => img.setAttribute(tup._1, tup._2))

    dragEvents.toList.foreach(tup => img.addEventListener(tup._1, tup._2))

    mouseEvents.toList.foreach(tup => {
      val str = tup._1
      val event = tup._2
      img.addEventListener(str, { (e: dom.MouseEvent) => {
        event(e)
      }
      })
    })
    img

  }

}

