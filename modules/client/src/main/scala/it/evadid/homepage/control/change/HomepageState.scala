package it.evadid.homepage.control.change

import it.evadid.core.datastructures.user.{AllUserInfo, UserCredentials}
import it.evadid.homepage.control.model.HomepageInfo
import it.evadid.workbook.interaction.sync.SyncConnection
import it.evadid.workbook.session.AllWorkbookInfo

import scala.concurrent.{ExecutionContext, Future}

/** Changes that concern both the page and its answers. Each runs the transitions of the two controls in a fixed
  * order and only forwards: the interaction control sends what the previous state still has to send, the homepage
  * control selects the new user, credentials or workbook, and the interaction control either keeps the answers with
  * the new connection (same sync context) or binds default answers for the new context. A full read follows.
  */
final class HomepageState(homepage: HomepageControl, interactions: InteractionControl, connectionFor: HomepageInfo => SyncConnection) {
  private given ExecutionContext = ExecutionContext.global

  /** Another person gets default answers for their context; the same person keeps the answers with the new profile. */
  def switchUser(user: Option[AllUserInfo]): Future[Unit] = {
    val samePerson = homepage.now().userInfo.map(_.user.personId) == user.map(_.user.personId)
    interactions.sendPending()
    val next = homepage.setUser(user)
    if samePerson then reconnect(next) else bindAnew(next)
  }

  def updateCredentials(credentials: UserCredentials): Future[Unit] = {
    interactions.sendPending()
    reconnect(homepage.setCredentials(credentials))
  }

  /** Another workbook instance gets its own answers; selecting the open instance again changes nothing. */
  def switchWorkbook(workbook: Option[AllWorkbookInfo]): Future[Unit] = {
    val previous = homepage.now().workbookInfo
    val sameInstance = previous.isDefined == workbook.isDefined && previous.zip(workbook).forall((a, b) => a.loadedWorkbook eq b.loadedWorkbook)
    if sameInstance then { homepage.setWorkbook(workbook); Future.unit }
    else {
      interactions.sendPending()
      bindAnew(homepage.setWorkbook(workbook))
    }
  }

  private def reconnect(next: HomepageInfo): Future[Unit] = {
    interactions.connect(connectionFor(next))
    afterTransaction(interactions.ensureSynced())
  }

  private def bindAnew(next: HomepageInfo): Future[Unit] = {
    interactions.bind(next.toContext, HomepageState.interactionsOf(next), connectionFor(next))
    afterTransaction(interactions.ensureSynced())
  }

  /** A change made inside an Airstream transaction applies when it ends; the read starts after it. */
  private def afterTransaction(operation: => Future[Unit]): Future[Unit] = Future.unit.flatMap(_ => operation)
}

object HomepageState {
  def interactionsOf(info: HomepageInfo) = info.workbookInfo.toSeq.flatMap(_.loadedWorkbook.allContainedInteractionsById.values)
}
