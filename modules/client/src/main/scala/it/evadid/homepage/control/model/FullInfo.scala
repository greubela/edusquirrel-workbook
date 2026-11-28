package it.evadid.homepage.control.model

import it.evadid.workbook.model.InteractionInfo

import it.evadid.workbook.model.InteractionScope


import it.evadid.llm.LlmConfiguration
import com.raquo.laminar.api.L.*
import com.raquo.airstream.ownership.ManualOwner
import it.evadid.homepage.control.info.interaction.InteractionReporterRegistry
import it.evadid.core.datastructures.language.AppLanguage.German
import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.state.StateHelper.RichSignal
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.homepage.control.change.*
import it.evadid.homepage.control.info.*
import it.evadid.homepage.control.singletons.HomepageDefaults
import it.evadid.core.datastructures.state.State
import it.evadid.workbook.control.InteractionController
import it.evadid.workbook.sync.SyncConnection
import it.evadid.workbook.abstractions.ExecutionContextInformation

import scala.concurrent.{ExecutionContext, Future}


/** Page state and the controls derived from it. Each member only uses those declared above it, so the controls are
  * built in one order without lazy cycles. Long-lived observers belong to `owner`; `release()` ends them.
  */
final class FullInfo(private[control] val defaultInfo: HomepageInfo) {

  private[homepage] val owner: ManualOwner = new ManualOwner

  /** Ends every long-lived observer of this page state, e.g. after a test. */
  def release(): Unit = owner.killSubscriptions()

  val defaults: HomepageDefaults = HomepageDefaults()

  val loggerSystemInfo: HomepageLoggerInfo = HomepageLoggerInfo.singleton

  /** Every change of the page state goes through this control. */
  val homepage: HomepageControl = HomepageControl(Var(defaultInfo), loggerSystemInfo.contentControlLogger)

  def homepageInfoNow(): HomepageInfo = homepage.now()

  /* INFO */

  val signals: HomepageSignalInfo = HomepageSignalInfo(this)

  val current: HomepageCurrentInfo = HomepageCurrentInfo(this)

  private val currentLanguageObservable = signals.currentLanguage.toObservableValue(using owner)

  private def backendToken = homepageInfoNow().userInfo.flatMap(_.credentials.signedBackendToken)

  /** The backend executor with the current user's credentials. */
  def backendExecutor: ExecutionClient = defaults.backendExecutorWithCredentials(backendToken)

  /* CONTROLS */

  val displayControl: DisplayControl = DisplayControl(this)

  val contentControl: HomepageContentControl = HomepageContentControl(this, loggerSystemInfo.contentStorageLogger, loggerSystemInfo.fileDataStorageLogger)

  val workbookNavigationControl: WorkbookNavigationControl = new WorkbookNavigationControl(this)

  val userSessionControl: UserSessionControl = new UserSessionControl(this)

  /** Every change of the answers goes through this control; it starts with the default answers of `defaultInfo`. */
  val interactions: InteractionController = {
    // Stores run after the current task, so changes made together share one write.
    val controller = new InteractionController(State(InteractionInfo.empty(defaultInfo.toContext)),
      run => { scala.scalajs.js.timers.setTimeout(0)(run()); () })
    controller.bind(scopeFor(defaultInfo))
    controller
  }

  /* A hidden page may be closed without running another task: pending stores would never start and buffered
   * destinations never send. So when the page is hidden or left, both happen at once. The listeners sit on the
   * window: `visibilitychange` is fired at the document and bubbles to the window, so in the same dispatch they
   * follow the editors, which write their typed text at the document.
   */
  windowEvents(_ => FullInfo.onVisibilityChange).filter(_ => org.scalajs.dom.document.hidden)
    .foreach(_ => interactions.sendPending())(using owner)
  windowEvents(_.onPageHide).foreach(_ => interactions.sendPending())(using owner)

  /** Changes of user, credentials and workbook, which concern both the page and its answers. */
  val state: HomepageState = HomepageState(homepage, interactions, scopeFor)

  val reporters: InteractionReporterRegistry = InteractionReporterRegistry(signals.workbook, interactions, owner)

  /** What `info` binds: its sync context, the interactions of its workbook, and its user's destinations with their
    * credentials and feedback execution.
    */
  def scopeFor(info: HomepageInfo): InteractionScope = InteractionScope(info.toContext,
    info.workbookInfo.toSeq.flatMap(_.loadedWorkbook.allContainedInteractionsById.values),
    SyncConnection(info.syncDestinations(this).toSet, executionContextFor(info), loggerSystemInfo.syncControlLogger))

  /** Feedback execution for the current user. */
  def workbookContextInformation: ExecutionContextInformation = executionContextFor(homepageInfoNow())

  /** Feedback execution with `info`'s credentials; the language is read when feedback runs. */
  def executionContextFor(info: HomepageInfo): ExecutionContextInformation = {
    val token = info.userInfo.flatMap(_.credentials.signedBackendToken)
    new ExecutionContextInformation() {
      override val llmConfiguration: Option[LlmConfiguration] = info.userInfo.flatMap(_.credentials.llmApiConfig)

      override lazy val availableExecutionClients: Seq[ExecutionClient] = List(defaults.workerExecuter, defaults.backendExecutorWithCredentials(token))

      override def getCurrentLanguage: AppLanguage.HumanLanguage = currentLanguageObservable.now().getOrElse(German)

      override lazy val languageMapIdResolver: Option[LanguageMapIdResolver] = Some(new LanguageMapIdResolver(currentLanguageObservable) {
        override def resolveMap(id: LanguageMapContentId): Future[LanguageMap[AppLanguage.HumanLanguage]] = Future {
          homepage.now().languageMapStore.languageMaps(id)
        }(using ExecutionContext.global)
      })

      override def futureExecutionContext: ExecutionContext = ExecutionContext.global
    }
  }
}

object FullInfo {
  /** `visibilitychange` on the window, where it arrives after the document's listeners. */
  private val onVisibilityChange = new com.raquo.laminar.keys.EventProp[org.scalajs.dom.Event]("visibilitychange")
}
