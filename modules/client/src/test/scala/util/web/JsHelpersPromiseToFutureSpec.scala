package util.web

import it.evadid.homepage.util.web.JsHelpers

import munit.FunSuite
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

final class JsHelpersPromiseToFutureSpec extends FunSuite {

  test("promiseToFuture maps rejected Promise to failed Future with JavaScriptException") {
    val rejectedValue = js.Dynamic.literal(message = "boom")
    val future = JsHelpers.promiseToFuture(js.Promise.reject(rejectedValue))

    future.transform {
      case scala.util.Success(_) =>
        scala.util.Failure(new AssertionError("Expected rejected Promise to produce failed Future"))
      case scala.util.Failure(err: js.JavaScriptException) =>
        assertEquals(err.exception.asInstanceOf[js.Dynamic].message.toString, "boom")
        scala.util.Success(())
      case scala.util.Failure(other) =>
        scala.util.Failure(new AssertionError(s"Expected JavaScriptException, got ${other.getClass.getName}"))
    }
  }
}
