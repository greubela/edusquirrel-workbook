package it.evadid.homepage.control.change

import it.evadid.core.datastructures.user.{AllUserInfo, UserCredentials}
import it.evadid.homepage.control.model.HomepageInfo
import it.evadid.workbook.control.InteractionController
import it.evadid.workbook.model.InteractionScope
import it.evadid.workbook.session.AllWorkbookInfo

import scala.concurrent.{ExecutionContext, Future}

/** Changes that concern both the page and its answers. Each forwards to the two controls in a fixed order: the
  * answers send what they still have to send, the page selects the user, credentials or workbook, and the answers
  * follow the changed page in the same step: the same sync context keeps its answers, any other starts with default
  * answers. A full read follows.
  */
final class HomepageState(homepage: HomepageControl, interactions: InteractionController, scopeFor: HomepageInfo => InteractionScope) {
  private given ExecutionContext = ExecutionContext.global

  /** The same person keeps the answers with the new profile and its connection; another person gets their own. */
  def switchUser(user: Option[AllUserInfo]): Future[Unit] = change(HomepageControl.withUser(user), reconnect = true)

  def updateCredentials(credentials: UserCredentials): Future[Unit] = change(HomepageControl.withCredentials(credentials), reconnect = true)

  /** Another workbook instance gets its own answers; selecting the open instance again only updates its settings. */
  def switchWorkbook(workbook: Option[AllWorkbookInfo]): Future[Unit] = {
    val open = homepage.now().workbookInfo
    if open.isDefined == workbook.isDefined && open.zip(workbook).forall((a, b) => a.loadedWorkbook eq b.loadedWorkbook) then {
      homepage.setWorkbook(workbook)
      Future.unit
    } else change(HomepageControl.withWorkbook(workbook), reconnect = false)
  }

  private def change(transition: HomepageInfo => HomepageInfo, reconnect: Boolean): Future[Unit] = {
    interactions.sync.sendPending()
    homepage.change(transition, next => interactions.follow(scopeFor(next), reconnect))
    // A change made inside an Airstream transaction applies when it ends; the read starts after it.
    Future.unit.flatMap(_ => interactions.sync.ensureSynced())
  }
}
