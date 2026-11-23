package it.evadid.homepage.webElements.basic

import com.raquo.airstream.ownership.ManualOwner
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import org.scalajs.dom

class PageScrollLockSpec extends EditorDomSuite {
  private def locked = dom.document.documentElement.classList.contains(PageScrollLock.lockedClass)

  test("the page stays locked while any dialog holds it, also when held from a signal observer") {
    val outer, inner = new Object
    PageScrollLock.hold(outer)
    PageScrollLock.hold(inner)
    PageScrollLock.release(inner)
    assert(locked, "a nested dialog closing keeps the outer dialog's lock")
    PageScrollLock.release(outer)
    assert(!locked)
    // Dialogs open from signal observers, i.e. inside an Airstream transaction.
    val owner = new ManualOwner
    val open = Var(false)
    open.signal.foreach(isOpen => if isOpen then PageScrollLock.hold(outer) else PageScrollLock.release(outer))(using owner)
    open.set(true)
    assert(locked, "the lock applies once the transaction has run")
    open.set(false)
    assert(!locked)
    owner.killSubscriptions()
  }
}
