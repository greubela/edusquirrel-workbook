package it.evadid.homepage.workbook.htmlRenderer.controlElements

import it.evadid.homepage.control.change.AuthenticationFailure
import munit.FunSuite
import scala.concurrent.{ExecutionContext, Future, Promise}

class LoginActionStateSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global

  test("pending actions block duplicate requests and display completion") {
    val state = new LoginActionState
    val promise = Promise[Unit]()
    val result = state.run(promise.future, "done", "error")
    assert(state.pending.now())
    assertEquals(state.messageKey.now(), "login/statusPending")
    var duplicateExecuted = false
    state.run({ duplicateExecuted = true; Future.successful(()) }, "done", "error")
    assert(!duplicateExecuted)
    promise.success(())
    result.map { _ =>
      assert(!state.pending.now())
      assert(!state.failed.now())
      assertEquals(state.messageKey.now(), "done")
    }
  }

  test("account rejection is visible and can be retried") {
    val state = new LoginActionState
    state.run(Future.failed(AuthenticationFailure("login/errorAccountExists")), "done", "error").flatMap { _ =>
      assert(state.failed.now())
      assertEquals(state.messageKey.now(), "login/errorAccountExists")
      assert(!state.pending.now())
      state.run(Future.successful(()), "done", "error").map { _ =>
        assert(!state.failed.now())
        assertEquals(state.messageKey.now(), "done")
      }
    }
  }

  test("transport failures get a visible fallback message") {
    val state = new LoginActionState
    state.run(Future.failed(new RuntimeException("backend unavailable")), "done", "login/errorLogin").map { _ =>
      assert(state.failed.now())
      assertEquals(state.messageKey.now(), "login/errorLogin")
      assert(!state.pending.now())
    }
  }

  test("synchronous validation failures are observed as well") {
    val state = new LoginActionState
    state.run(throw AuthenticationFailure("login/errorEmail"), "done", "error").map { _ =>
      assertEquals(state.messageKey.now(), "login/errorEmail")
      assert(state.failed.now())
      assert(!state.pending.now())
    }
  }
}
