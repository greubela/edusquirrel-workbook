package it.evadid.workbook.model.interaction

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.state.State
import it.evadid.core.util.io.*
import it.evadid.workbook.model.abstractions.WorkbookElement
import it.evadid.workbook.model.interaction.sync.SyncInformation
import it.evadid.workbook.model.interaction.variable.InteractionVariable

trait WorkbookInteraction[T] extends WorkbookElement {

  override lazy val allContainedInteractions: List[WorkbookInteraction[?]] = List(this)
  lazy val isDisabledState: State[Boolean] = State(false)

  def defaultValue: T

  def serializer: Serializer[T]

  def interactionVariable: InteractionVariable[T]

  def id: String

  //   val syncDest: List[SyncInformation] = fullInfo.current.allSyncSources

  def clearHistory(syncBefore: Boolean): Unit = id.synchronized {
    if (syncBefore) {
      interactionVariable.syncToAll(true)
    }
    interactionVariable.resetHistory()
  }

  def resetInteraction(syncBefore: Boolean, syncAfter: Boolean, newSyncDest: List[SyncInformation]): Unit = id.synchronized {
    if (syncBefore) {
      interactionVariable.syncToAll()
    }
    interactionVariable.resetInteractionVariable(newSyncDest)
    if (syncAfter) {
      interactionVariable.syncFromAll()
      interactionVariable.syncToAll()
    }
  }

}

object WorkbookInteraction {

  abstract class WorkbookBasicVariableInteraction[T](override val defaultValue: T, override val serializer: Serializer[T], override val id: String) extends WorkbookInteraction[T] {
    override val interactionVariable: InteractionVariable[T] = InteractionVariable[T](this)
  }

  case class TextInteractionBasic(override val id: String) extends WorkbookBasicVariableInteraction[String]("", Serializer.stringIO, id)
  

}
