package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.state.Var
import it.evadid.facades.Snap.IDEMorph
import org.scalajs.dom

import SnapProjectRuntime.snapshotProjectXml

/** Publishes the editor's project when its persisted XML changed.
  *
  * The persisted project itself is compared rather than `IDE_Morph.version`: that value is only updated on Snap
  * edit paths that call `recordUnsavedChanges` and is not a reliable content revision. Checks are throttled, so
  * serialization does not happen on every animation frame.
  */
private[SnapEditor] final class SnapProjectChangeTracker {
  import SnapProjectChangeTracker.*

  private val sync = new SnapProjectXmlSync
  private val lastCheckAt = Var(0.0)
  private val listener = Var[String => Unit](_ => ())

  def onChanged(callback: String => Unit): Unit = listener.set(callback)

  /** `xml` is what the editor shows, so restoring it again would only echo the editor's own change. */
  def markLoaded(xml: String): Unit = sync.markLoaded(xml)
  def isLoaded(xml: String): Boolean = sync.isLoaded(xml)

  /** Takes the editor's current project as the published baseline, e.g. after a load or a language change. */
  def reset(ide: IDEMorph): Unit = {
    sync.resetSnapshot(snapshotProjectXml(ide))
    lastCheckAt.set(dom.window.performance.now())
  }

  /** Whether the throttle allows a check at `now`; a due check is recorded. */
  def due(now: Double): Boolean =
    if now - lastCheckAt.now() < CheckIntervalMs then false
    else {
      lastCheckAt.set(now)
      true
    }

  /** Publishes the project if it differs from the last published one. */
  def publish(ide: IDEMorph): Unit = sync.changedSnapshot(snapshotProjectXml(ide)).foreach(listener.now())

  def clear(): Unit = {
    sync.clear()
    lastCheckAt.set(0.0)
  }
}

private[SnapEditor] object SnapProjectChangeTracker {
  private val CheckIntervalMs = 500.0
}
