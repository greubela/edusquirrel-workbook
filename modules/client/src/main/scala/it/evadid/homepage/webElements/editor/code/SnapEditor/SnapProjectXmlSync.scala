package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.vm.conversion.snapxml.SnapXmlParser

import com.raquo.airstream.state.Var

import it.evadid.workbook.elements.interactionElements.programming.state.*

/** Compare persisted XML, so Snap's regenerated images cannot trigger save/reload echoes. */
private[SnapEditor] final class SnapProjectXmlSync {
  private val loaded = Var(Option.empty[ProgrammingStateSnapScalaModel])
  private val snapshot = Var(Option.empty[ProgrammingStateSnapScalaModel])

  private def clean(xml: String): String = ProgrammingStateSnapXml(xml).removeBloatFromXml.snapXml

  private def model(xml: String): ProgrammingStateSnapScalaModel =
    ProgrammingStateSnapXml(xml).toSnapScalaModel.fold(message => throw IllegalArgumentException(message), _.persistent)

  def markLoaded(xml: String): Unit = loaded.set(Some(model(xml)))
  def isLoaded(xml: String): Boolean = loaded.now().exists(_.meaningfullyEquals(model(xml)))
  def resetSnapshot(xml: String): Unit = snapshot.set(Some(model(clean(xml))))

  def changedSnapshot(xml: String): Option[String] = {
    val next = clean(xml)
    val key = model(next)
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
