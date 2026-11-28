package it.evadid.homepage.control.change

import it.evadid.core.datastructures.user.{AllUserInfo, UserCredentials}
import it.evadid.homepage.control.model.HomepageInfo
import it.evadid.workbook.interaction.state.InteractionScope
import it.evadid.workbook.session.AllWorkbookInfo

import scala.concurrent.{ExecutionContext, Future}

/** Changes that concern both the page and its answers. Each forwards to the two controls in a fixed order: the
  * interaction control sends what the current answers still have to send, the homepage control selects the user,
  * credentials or workbook, and the interaction control follows the page, read once that selection applies: the same
  * sync context keeps its answers, any other starts with default answers. A full read follows.
  */
final class HomepageState(homepage: HomepageControl, interactions: InteractionControl, scopeFor: HomepageInfo => InteractionScope) {
  private given ExecutionContext = ExecutionContext.global

  /** The same person keeps the answers with the new profile and its connection; another person gets their own. */
  def switchUser(user: Option[AllUserInfo]): Future[Unit] = change(reconnect = true)(homepage.setUser(user))

  def updateCredentials(credentials: UserCredentials): Future[Unit] = change(reconnect = true)(homepage.setCredentials(credentials))

  /** Another workbook instance gets its own answers; selecting the open instance again only updates its settings. */
  def switchWorkbook(workbook: Option[AllWorkbookInfo]): Future[Unit] = {
    val open = homepage.now().workbookInfo
    if open.isDefined == workbook.isDefined && open.zip(workbook).forall((a, b) => a.loadedWorkbook eq b.loadedWorkbook) then {
      homepage.setWorkbook(workbook)
      Future.unit
    } else change(reconnect = false)(homepage.setWorkbook(workbook))
  }

  private def change(reconnect: Boolean)(select: => Unit): Future[Unit] = {
    interactions.sendPending()
    select
    interactions.follow(() => scopeFor(homepage.now()), reconnect)
    // A change made inside an Airstream transaction applies when it ends; the read starts after it.
    Future.unit.flatMap(_ => interactions.ensureSynced())
  }
}
