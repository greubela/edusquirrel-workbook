package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.state.Var

import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.{JavaTurtleEditingBridge, SnapXmlParser}

/** Compare persisted XML, so Snap's regenerated images cannot trigger save/reload echoes. */
private[SnapEditor] final class SnapProjectXmlSync {
  private val loaded = Var(Option.empty[ProgrammingStateSnapScalaModel])
  private val snapshot = Var(Option.empty[ProgrammingStateSnapScalaModel])

  private def clean(xml: String): String = ProgrammingStateSnapXml(xml).removeBloatFromXml.snapXml

  private val runtimePose = """\s+(?:x|y|heading)="[^"]*"""".r

  private def snapshotKey(xml: String): String =
    if !JavaTurtleEditingBridge.hasSnapMetadata(xml) then xml
    else SnapXmlParser.elements(xml, "sprite").reverse.foldLeft(xml) { (current, sprite) =>
      val headerEnd = sprite.start + sprite.outer.indexOf('>')
      current.substring(0, sprite.start) + runtimePose.replaceAllIn(current.substring(sprite.start, headerEnd), "") +
        current.substring(headerEnd)
    }

  private def model(xml: String): ProgrammingStateSnapScalaModel =
    ProgrammingStateSnapXml(xml).toSnapScalaModel.fold(message => throw IllegalArgumentException(message), _.persistent)

  def markLoaded(xml: String): Unit = loaded.set(Some(model(xml)))
  def isLoaded(xml: String): Boolean = loaded.now().exists(_.meaningfullyEquals(model(xml)))
  def resetSnapshot(xml: String): Unit = snapshot.set(Some(model(snapshotKey(clean(xml)))))

  def changedSnapshot(xml: String): Option[String] = {
    val next = clean(xml)
    val key = model(snapshotKey(next))
    if snapshot.now().exists(_.meaningfullyEquals(key)) then None
    else {
      snapshot.set(Some(key))
      Some(next)
    }
  }

  def clear(): Unit = {
    loaded.set(None)
    snapshot.set(None)
  }
}
