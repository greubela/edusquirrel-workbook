package it.evadid.homepage.webElements.editor.code

import com.raquo.airstream.ownership.{Owner, Subscription}
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.js
import scala.util.Try

/** Follows the page theme (`data-theme` on the document element), otherwise the system scheme. */
final class EditorAppearance {
  private val dark = Var(EditorAppearance.isDarkNow)
  val theme: Signal[String] = dark.signal.map(if _ then "dark" else "light")

  def watch(owner: Owner): Unit = EditorAppearance.watch(dark.set, owner)
}

object EditorAppearance {
  def isDarkNow: Boolean = Try {
    Option(dom.document.documentElement).flatMap(root => Option(root.getAttribute("data-theme"))) match {
      case Some("dark") => true
      case Some("light") => false
      case _ => prefersDark
    }
  }.getOrElse(false)

  def watch(update: Boolean => Unit, owner: Owner): Unit = {
    update(isDarkNow)
    val stop = observe(update)
    new Subscription(owner, () => stop.foreach(_()))
  }

  private def prefersDark: Boolean =
    Try(dom.window.matchMedia("(prefers-color-scheme: dark)").matches).getOrElse(false)

  private def observe(update: Boolean => Unit): Option[() => Unit] = Try {
    val publish: js.Function0[Unit] = () => update(isDarkNow)
    val root = dom.document.documentElement
    val callback: js.Function2[js.Any, js.Any, Unit] = (_, _) => publish()
    val observer = js.Dynamic.newInstance(dom.window.asInstanceOf[js.Dynamic].MutationObserver)(callback)
    observer.observe(root, js.Dynamic.literal(attributes = true, attributeFilter = js.Array("data-theme")))
    val scheme = Try {
      val query = dom.window.matchMedia("(prefers-color-scheme: dark)")
      val listener: js.Function1[dom.Event, Unit] = _ => publish()
      query.addEventListener("change", listener)
      () => query.removeEventListener("change", listener)
    }.toOption
    () => {
      observer.disconnect()
      scheme.foreach(_())
    }
  }.toOption
}
