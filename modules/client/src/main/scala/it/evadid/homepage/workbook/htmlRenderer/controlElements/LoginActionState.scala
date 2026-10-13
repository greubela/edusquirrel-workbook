package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L.*
import it.evadid.homepage.control.change.AuthenticationFailure

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/** Tracks asynchronous form actions, including synchronous validation failures. */
private[controlElements] final class LoginActionState(using ExecutionContext) {
  val pending: Var[Boolean] = Var(false)
  val messageKey: Var[String] = Var("")
  val failed: Var[Boolean] = Var(false)

  def run(action: => Future[?], successKey: String, failureKey: String): Future[Unit] = {
    if (pending.now()) Future.successful(())
    else {
      pending.set(true)
      failed.set(false)
      messageKey.set("login/statusPending")
      val result = Try(action).fold(Future.failed, identity)
      result.transform { outcome =>
        pending.set(false)
        outcome match {
          case Success(_) => messageKey.set(successKey)
          case Failure(err) =>
            failed.set(true)
            messageKey.set(err match {
              case AuthenticationFailure(key) => key
              case _ => failureKey
            })
        }
        Success(()) // The error is now observed and displayed by the view.
      }
    }
  }
}
