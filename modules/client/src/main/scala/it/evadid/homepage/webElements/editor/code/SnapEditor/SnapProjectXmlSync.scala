package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.ProgrammingStateSnapXml
import it.evadid.workbook.elements.interactionElements.programming.state.*

/** Compare persisted XML, so Snap's regenerated images cannot trigger save/reload echoes. */
private[SnapEditor] final class SnapProjectXmlSync {
  private var loaded: Option[String] = None
  private var snapshot: Option[String] = None

  private def clean(xml: String): String = ProgrammingStateSnapXml(xml).removeBloatFromXml.snapXml

  def markLoaded(xml: String): Unit = loaded = Some(clean(xml))
  def isLoaded(xml: String): Boolean = loaded.contains(clean(xml))
  def resetSnapshot(xml: String): Unit = snapshot = Some(clean(xml))

  def changedSnapshot(xml: String): Option[String] = {
    val next = clean(xml)
    if snapshot.contains(next) then None
    else {
      snapshot = Some(next)
      Some(next)
    }
  }

  def clear(): Unit = {
    loaded = None
    snapshot = None
  }
}
