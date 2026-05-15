package workbook.model.abstractions

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMap
import sourcecode.Text.generate
import workbook.model.info.{FullInfo, HomepageInfo, UserConfig}
import workbook.model.interaction.*
import workbook.model.interaction.sync.SyncInformation

import scala.concurrent.{ExecutionContext, Future}

trait HtmlWorkbookElement extends HtmlAppElement {

  def fullInfo: FullInfo

  lazy val workbookChildren: List[HtmlWorkbookElement] = List()

  lazy val allContainedInteractions: List[WorkbookInteraction[?]] =
    workbookChildren.flatMap(_.allContainedInteractions) ++ this.match {
      case i: WorkbookInteraction[?] => List(i)
      case _ => List()
    }

}


trait WorkbookInteraction[T] extends HtmlWorkbookElement {
  def id: String

  override lazy val allContainedInteractions: List[WorkbookInteraction[?]] = List(this)

  def loadScaffoldingInformation(languageMapIdExerciseText: String, languageMapIdAdditionalHints: String): Future[ScaffoldingInformation[T]] = {
    val fut1 = fullInfo.technical.languageMapStorage.loadAsFuture(languageMapIdExerciseText)(using ExecutionContext.global)
    val fut2 = fullInfo.technical.languageMapStorage.loadAsFuture(languageMapIdAdditionalHints)(using ExecutionContext.global)
    fut1.zip(fut2).map { case (res1, res2) => ScaffoldingInformation(this, res1, res2) }(using ExecutionContext.global)
  }

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

case class ScaffoldingInformation[T](
                                      underlyingInteraction: WorkbookInteraction[T],
                                      exerciseText: LanguageMap[HumanLanguage],
                                      additionalScaffolds: LanguageMap[HumanLanguage]
                                    ) {

}
