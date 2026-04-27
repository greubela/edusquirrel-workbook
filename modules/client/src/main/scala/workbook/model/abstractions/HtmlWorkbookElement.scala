package workbook.model.abstractions

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement
import workbook.model.info.{FullInfo, HomepageInfo, UserConfig}
import workbook.model.interaction.*
import workbook.model.interaction.sync.SyncInformation

trait HtmlWorkbookElement extends HtmlAppElement {
  def fullInfo: FullInfo
}


trait WorkbookInteraction[T] extends HtmlWorkbookElement {
  def id: String

  def interactionVariable: InteractionVariable[T]

  def defaultValue: T

  def resetInteraction(syncBefore: Boolean, syncAfter: Boolean): Unit = fullInfo.synchronized {

    if (syncBefore) {
      interactionVariable.syncToAll()
    }
    val syncDest: List[SyncInformation] = fullInfo.current.allSyncSources

    interactionVariable.resetInteractionVariable(defaultValue, syncDest)

    if (syncAfter) {
      interactionVariable.syncFromAll()
      interactionVariable.syncToAll()
    }
  }

}

trait WorkbookScaffolding[T] {
  def underlyingInteraction: WorkbookInteraction[T]
}

object HtmlWorkbookElement {


}