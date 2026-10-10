package it.evadid.core.datastructures.state

import munit.FunSuite
import scala.concurrent.{ExecutionContext, Promise}
import scala.util.{Failure, Success, Try}

class ExecutionMethodSpec extends FunSuite {
  test("synchronous execution delivers its result before returning") {
    var result: Option[Try[Int]] = None
    ExecutionMethod.executeSync.handleExecution((n: Int) => n + 1, 2, r => result = Some(r))
    assertEquals(result, Some(Success(3)))
  }

  test("synchronous execution reports function failures once") {
    val error = new IllegalArgumentException("function")
    var results = List.empty[Try[Int]]
    ExecutionMethod.executeSync.handleExecution((_: Int) => throw error, 2, (r: Try[Int]) => results = results :+ r)
    assertEquals(results, List(Failure(error)))
  }

  test("a failing callback is not called again with its own exception") {
    var calls = 0
    val error = new IllegalArgumentException("callback")
    val thrown = intercept[IllegalArgumentException] {
      ExecutionMethod.executeSync.handleExecution((n: Int) => n, 2, (_: Try[Int]) => { calls += 1; throw error })
    }
    assertEquals(thrown, error)
    assertEquals(calls, 1)
  }

  test("asynchronous execution returns the successful result through its callback") {
    val result = Promise[Int]()
    ExecutionMethod.executeAsync.handleExecution((n: Int) => n + 1, 2, r => result.complete(r))
    result.future.map(n => assertEquals(n, 3))(using ExecutionContext.global)
  }

  test("asynchronous execution reports a function exception through its callback") {
    val error = new IllegalArgumentException("async function")
    val result = Promise[Try[Int]]()
    ExecutionMethod.executeAsync.handleExecution((_: Int) => throw error, 2, (r: Try[Int]) => result.success(r))
    result.future.map(r => assertEquals(r, Failure(error)))(using ExecutionContext.global)
  }
}
