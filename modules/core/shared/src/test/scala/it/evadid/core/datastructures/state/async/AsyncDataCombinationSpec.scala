package it.evadid.core.datastructures.state.async

import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.async.AsyncDataState.*
import munit.FunSuite

import scala.concurrent.{ExecutionContext, Future, Promise}

class AsyncDataCombinationSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global

  test("factories wrap options, errors and futures") {
    assertEquals(AsyncData.forOption(Some(2)).stateNow().value, Some(2))
    assert(AsyncData.forOption[Int](None).stateNow().failure.exists(_.error.msg.contains("empty option")))
    assert(AsyncData.forError[Int](IllegalStateException("down")).stateNow().failure.exists(_.error.msg == "down"))
    for {
      plain <- AsyncData.forFuture(Future.successful(3)).futureFirstValue
      state <- AsyncData.forStateFuture(Future.successful(AsyncDataSuccess[String, Int](4))).futureFirstValue
      withInfo <- AsyncData.forFutureWithFailureInfo[String, Int](Future.successful(Right(5))).futureFirstValue
      failed <- AsyncData.forFuture[Int](Future.failed(IllegalStateException("lost"))).futureFirstState
    } yield {
      assertEquals((plain, state, withInfo), (3, 4, 5))
      assert(failed.failure.exists(_.error.msg.contains("lost")), failed)
    }
  }

  test("loaded-state views skip loading states") {
    val source = State[AsyncDataState[String, Int]](AsyncDataLoading())
    val data = AsyncState(source.observable)
    val seen = State(Vector.empty[Int])
    data.observeLoadedValues.addObserver(value => seen.update(_ :+ value))
    assertEquals(data.observeLoadedStates.now(), None)
    source.set(AsyncDataSuccess(7))
    assertEquals(seen.now(), Vector(7))
    assertEquals(data.observeLoadedStates.now(), Some(AsyncDataSuccess(7)))
  }

  test("a first failed value fails futureFirstValue while mapAsync skips work after errors") {
    val failed = AsyncValue[Int](IllegalArgumentException("bad input"))
    val calls = State(0)
    for {
      error <- failed.futureFirstValue.failed
      mapped <- failed.mapAsync(value => { calls.update(_ + 1); Future.successful(value + 1) }).futureFirstState
      renamed = failed.mapIfError(_ => "renamed").stateNow()
    } yield {
      assert(error.getMessage.contains("bad input"), error.getMessage)
      assert(mapped.failure.exists(_.error.msg.contains("already an error")), mapped)
      assertEquals(calls.now(), 0)
      assert(renamed.failure.nonEmpty)
    }
  }

  test("combinations wait for both sides, combine values and surface either error") {
    val left = State[AsyncDataState[Nothing, Int]](AsyncDataLoading())
    val right = State[AsyncDataState[Nothing, String]](AsyncDataLoading())
    val combined = AsyncState(left.observable).combineAndMapIgnoreErrorData(AsyncState(right.observable), (n, s) => s * n)
    assert(combined.stateNow().isLoading)
    left.set(AsyncDataSuccess(2))
    assert(combined.stateNow().isLoading)
    right.set(AsyncDataSuccess("ab"))
    assertEquals(combined.stateNow().value, Some("abab"))

    val failing = AsyncValue(1).combineIgnoreErrorData(AsyncValue[Int](IllegalStateException("right side")))
    assert(failing.stateNow().failure.exists(_.error.msg.contains("Right Element")), failing.stateNow())
    val failingLeft = AsyncValue[Int](IllegalStateException("left side")).combineIgnoreErrorData(AsyncValue(1))
    assert(failingLeft.stateNow().failure.nonEmpty, failingLeft.stateNow())
  }

  test("recovering after an error pushes the recovered state") {
    val promise = Promise[AsyncDataStateFinished[Nothing, Int]]()
    val recovered = AsyncValue[Int](IllegalStateException("x")).recoverOnErrorPushError(_ => promise.future)
    promise.success(AsyncDataSuccess(9))
    recovered.futureFirstValue.map(value => assertEquals(value, 9))
  }
}
