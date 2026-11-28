package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.user.{AllUserInfo, User, UserConfig}
import it.evadid.distribution.commandTypes.SubmissionCommands.{SubmitSessionEvent, SubmitSessionResponse}
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.UiDomSuite
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.util.logging.BasicLogger
import it.evadid.workbook.elements.display.WorkbookSubmissionElement
import it.evadid.workbook.interactions.basic.TextInteraction
import it.evadid.workbook.sync.UpdateImportance
import it.evadid.workbook.abstractions.ExecutionContextInformation
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.workbook.elements.structure.{Workbook, WorkbookSection}
import it.evadid.workbook.elements.structure.Workbook.WorkbookMetadata
import it.evadid.workbook.elements.structure.WorkbookSection.WorkbookSectionMetadata
import it.evadid.workbook.session.{AllWorkbookInfo, WorkbookConfig}
import org.scalajs.dom

import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class HtmlSubmissionFormSpec extends UiDomSuite {
  private val online = AllUserInfo.createNewUser("Online Student", "online@example.org", UserConfig.defaults(isLocalAccount = false))
  private val local = AllUserInfo.createNewUser("Anonymous", "anonymous@evadid.it", UserConfig.defaults(isLocalAccount = true))

  /** A form over the page's open workbook whose backend records each event and answers with `reply`. */
  private final class Fixture(user: Option[AllUserInfo], reply: SubmitSessionEvent => Future[SubmitSessionResponse] =
      _ => Future.successful(SubmitSessionResponse(1, confirmationSent = true))) {
    val currentUser: Var[Option[AllUserInfo]] = Var(user)
    val sent: Var[Seq[SubmitSessionEvent]] = Var(Seq.empty)
    val form: HtmlSubmissionForm = HtmlSubmissionForm(fullInfo, submitter => fullInfo.homepageInfoNow().toSubmissionBy(submitter, fullInfo.interactions.info),
      currentUser.signal, event => { sent.update(_ :+ event); reply(event) }, BasicLogger())
  }

  /** Keeps a one-section workbook with `interactions` open until `body`'s future completes. */
  private def inOpenWorkbookUntil[T](interactions: TextInteraction*)(body: => Future[T]): Future[T] = {
    val section = WorkbookSection("submission-section", WorkbookSectionMetadata(LanguageMapContentId("ui/section")), interactions)
    val workbook = Workbook("ui/submission", WorkbookMetadata(Set.empty, Set.empty, LanguageMapContentId("ui/title"),
      Seq(AppLanguage.English)), Seq(section))
    val control = fullInfo.workbookNavigationControl
    control.changeWorkbook(Some(AllWorkbookInfo(workbook, WorkbookConfig(Some(section)), Map.empty)))
    scala.util.Try(body).fold(Future.failed, identity).andThen(_ => control.changeWorkbook(None))
  }

  private def nameInput(root: dom.Element) = one(root, "input.login-name-input")
  private def mailInput(root: dom.Element) = one(root, "input.login-email-input")
  private def status(root: dom.Element): String = one(root, ".login-status").textContent
  private def submitButton(root: dom.Element) = buttonNamed(root, loadingText("login/buttonSubmit"))
  /** Lets the submission future and the status update complete. */
  private def settled: Future[Unit] = Future.unit.flatMap(_ => Future.unit)

  test("online accounts prefill name and address; local accounts and later sign-outs leave them blank") {
    val fixture = Fixture(Some(online))
    mounted(fixture.form.getDomElement()) { root =>
      assertEquals(valueOf(nameInput(root)), online.user.name)
      assertEquals(valueOf(mailInput(root)), online.user.mail)
      fixture.currentUser.set(Some(local))
      assertEquals(valueOf(nameInput(root)), "")
      assertEquals(valueOf(mailInput(root)), "")
    }
    mounted(Fixture(Some(local)).form.getDomElement())(root => assertEquals(valueOf(nameInput(root)), ""))
  }

  test("a submission carries every current answer under a new, local-only submitter and leaves the bound user untouched") {
    val answered = TextInteraction("submitted-answer")
    val untouched = TextInteraction("submitted-untouched")
    inOpenWorkbookUntil(answered, untouched) {
      fullInfo.interactions.setStateFromUserInteraction(answered, "my solution", UpdateImportance.MAJOR)
      val boundBefore = fullInfo.homepageInfoNow().userInfo
      val fixture = Fixture(Some(online))
      mountedAsync(fixture.form.getDomElement()) { root =>
        typeInto(nameInput(root), "  Exam Student ")
        typeInto(mailInput(root), "exam@example.org")
        click(submitButton(root))
        settled.map { _ =>
          val Seq(event) = fixture.sent.now()
          val session = event.sessionData
          assertEquals((session.userInfo.name, session.userInfo.mail), ("Exam Student", "exam@example.org"))
          assert(session.userInfo.id != online.user.id, "the submitter is a new user, not the signed-in account")
          assertEquals(session.userConfig, Some(UserConfig.defaults(isLocalAccount = true)))
          assert(session.userConfig.exists(config => !config.isOnlineAccount && config.syncDestinations.forall(!_.syncDestination.isRemote)))
          val expected = fullInfo.homepageInfoNow().toSubmissionBy(session.userInfo, fullInfo.interactions.info).get.knownUsageData
          assertEquals(session.knownUsageData, expected, "every interaction's current history is submitted")
          assertEquals(session.knownUsageData.size, 2)
          val text = session.knownUsageData.values.map(_.toTyped.latestAnswer.map(_.value.toString)).toSet
          assert(text.contains(Some("my solution")), text.toString)
          assertEquals(fullInfo.homepageInfoNow().userInfo, boundBefore, "submitting never signs in or switches the user")
          assert(status(root).contains(loadingText("login/submissionSent")), status(root))
        }
      }
    }
  }

  test("missing names and invalid addresses are rejected before anything is sent") {
    inOpenWorkbook(TextInteraction("validated")) {
      val fixture = Fixture(None)
      mounted(fixture.form.getDomElement()) { root =>
        typeInto(mailInput(root), "exam@example.org")
        click(submitButton(root))
        assert(status(root).contains(loadingText("login/submissionErrorName")), status(root))
        typeInto(nameInput(root), "Exam Student")
        typeInto(mailInput(root), "not an address")
        click(submitButton(root))
        assert(status(root).contains(loadingText("login/errorEmail")), status(root))
        assertEquals(fixture.sent.now(), Nil)
      }
    }
  }

  test("the button waits for the backend; failures and missing confirmations are reported and can be retried") {
    inOpenWorkbookUntil(TextInteraction("retried")) {
      val pending = Promise[SubmitSessionResponse]()
      val replies = Var(List(pending.future, Future.successful(SubmitSessionResponse(1, confirmationSent = false))))
      val fixture = Fixture(Some(online), _ => { val next = replies.now().head; replies.update(_.tail); next })
      mountedAsync(fixture.form.getDomElement()) { root =>
        click(submitButton(root))
        assert(isDisabled(submitButton(root)))
        click(submitButton(root))
        assertEquals(fixture.sent.now().size, 1, "a pending submission is not sent twice")
        pending.failure(IllegalStateException("backend unavailable"))
        settled.flatMap { _ =>
          assert(!isDisabled(submitButton(root)))
          assert(status(root).contains(loadingText("login/submissionError")), status(root))
          click(submitButton(root))
          settled
        }.map { _ =>
          assertEquals(fixture.sent.now().size, 2)
          assert(status(root).contains(loadingText("login/submissionSentWithoutConfirmation")), status(root))
        }
      }
    }
  }

  test("workbooks render submission elements as the submission form") {
    val rendering = HtmlRenderFactory.renderWorkbookElement(fullInfo, WorkbookSubmissionElement("rendered-submission"))
    mounted(div(rendering.getDomElement())) { root =>
      assertEquals(all(root, "input.login-name-input").size, 1)
      assertEquals(all(root, "input.login-email-input").size, 1)
      submitButton(root)
    }
  }
}
