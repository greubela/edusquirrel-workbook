package it.evadid.homepage.webElements.basic

import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** Locks page scrolling while a modal dialog is open, by a class on `<html>`.
  *
  * A `:has(dialog[open])` rule on the document root would make the browser restyle the whole page whenever
  * matching DOM changes anywhere, e.g. on every keystroke in an editor. Dialogs may be nested; the page stays
  * locked while any holder remains.
  */
object PageScrollLock {
  val lockedClass = "page-scroll-locked"
  private val holders = Var(Set.empty[AnyRef])

  // Follows committed changes: inside an Airstream transaction (e.g. a dialog's signal observer) a Var update
  // only takes effect when the transaction ends.
  holders.signal.map(_.nonEmpty).distinct.foreach { locked =>
    val root = dom.document.documentElement.classList
    if locked then root.add(lockedClass) else root.remove(lockedClass)
  }(using unsafeWindowOwner)

  def hold(owner: AnyRef): Unit = holders.update(_ + owner)
  def release(owner: AnyRef): Unit = holders.update(_ - owner)

  /** Holds the lock while the element is mounted, e.g. for a dialog shown on mount. */
  def whileMounted: Modifier[HtmlElement] = {
    val owner = new Object
    Seq(onMountCallback(_ => hold(owner)), onUnmountCallback(_ => release(owner)))
  }
}
