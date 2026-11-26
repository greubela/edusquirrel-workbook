package it.evadid.homepage.webElements.basic

import com.raquo.airstream.web.DomEventStream
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.util.Try

object MediaQuery {
  /** Whether the CSS media `query` matches, following changes while observed; false where the browser API is
    * unavailable (e.g. in jsdom).
    */
  def matches(query: String): Signal[Boolean] =
    Try(dom.window.matchMedia(query)).toOption.filter(_ != null).fold[Signal[Boolean]](Val(false)) { list =>
      DomEventStream[dom.Event](list, "change").map(_ => list.matches).startWith(list.matches)
    }
}
