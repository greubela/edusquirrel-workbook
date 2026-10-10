package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.ProgrammingStateSnapXml
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.{JavaTurtleEditingBridge, SnapXmlParser}

/** Compare persisted XML, so Snap's regenerated images cannot trigger save/reload echoes. */
private[SnapEditor] final class SnapProjectXmlSync {
  private var loaded: Option[String] = None
  private var snapshot: Option[String] = None

  private def clean(xml: String): String = ProgrammingStateSnapXml(xml).removeBloatFromXml.snapXml

  private val runtimePose = """\s+(?:x|y|heading)="[^"]*"""".r

  private def snapshotKey(xml: String): String =
    if !JavaTurtleEditingBridge.hasSnapMetadata(xml) then xml
    else SnapXmlParser.elements(xml, "sprite").reverse.foldLeft(xml) { (current, sprite) =>
      val headerEnd = sprite.start + sprite.outer.indexOf('>')
      current.substring(0, sprite.start) + runtimePose.replaceAllIn(current.substring(sprite.start, headerEnd), "") +
        current.substring(headerEnd)
    }

  def markLoaded(xml: String): Unit = loaded = Some(clean(xml))
  def isLoaded(xml: String): Boolean = loaded.contains(clean(xml))
  def resetSnapshot(xml: String): Unit = snapshot = Some(snapshotKey(clean(xml)))

  def changedSnapshot(xml: String): Option[String] = {
    val next = clean(xml)
    val key = snapshotKey(next)
    if snapshot.contains(key) then None
    else {
      snapshot = Some(key)
      Some(next)
    }
  }

  def clear(): Unit = {
    loaded = None
    snapshot = None
  }
}
