package it.evadid.core.datastructures.state.async

import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.async.AsyncDataState.*
import it.evadid.distribution.command.SerializedException
import munit.FunSuite
import scala.concurrent.{ExecutionContext, Future, Promise}
import upickle.default.{read, write}

class AsyncDataPackageSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global

  test("AsyncValue exposes completed success, mapping, options and errors") {
    val value = AsyncValue(3)
    assertEquals(value.stateNow().value, Some(3))
    assertEquals(value.observeAllStates.now().get.value, Some(3))
    assertEquals(value.map(_ * 2).stateNow().value, Some(6))
    assertEquals(AsyncValue(Some(4)).stateNow().value, Some(4))
    assert(AsyncValue(Option.empty[Int]).stateNow().failure.nonEmpty)
    assert(AsyncValue[Int](new IllegalArgumentException("bad")).stateNow().failure.nonEmpty)
    value.futureFirstValue.map(result => assertEquals(result, 3))
  }

  test("finished states retain data and safely turn throwing mappings into failures") {
    val success = AsyncDataSuccess[String, Int](4)
    assertEquals(success.asEither, Right(4))
    assertEquals(success.mapFinished(_ + 1).value, Some(5))
    assert(success.map[Int](_ => throw new IllegalStateException("mapping")).failure.nonEmpty)
    val failed = AsyncDataFailed[String, Int](new IllegalArgumentException("bad"), Some("details"))
    assertEquals(failed.asEither.left.toOption.get.data, Some("details"))
    assertEquals(failed.mapIfError(_.length).failure.get.data, Some(7))
    assertEquals(failed.map(_ + 1).failure.get.data, Some("details"))
    assert(failed.mapIfError[Int](_ => throw new IllegalStateException("mapping")).failure.nonEmpty)
    val loading = AsyncDataLoading[String, Int]()
    assert(loading.isLoading)
    assert(loading.loadingSince.nonEmpty)
    assertEquals(loading.value, None)
    assertEquals(loading.failure, None)
  }

  test("AsyncState finishes with a typed failed state and keeps its failure metadata") {
    val state = State[AsyncDataState[String, Int]](AsyncDataLoading())
    val value = AsyncState(state.observable)
    assert(value.stateNow().isLoading)
    state.set(AsyncDataFailed(new IllegalStateException("offline"), Some("retry later")))
    value.futureFirstState.map { finished =>
      assertEquals(finished.failure.get.data, Some("retry later"))
      assertEquals(finished.failure.get.error.msg, "offline")
    }
  }

  test("AsyncState keeps its first finished value while observing later updates") {
    val state = State[AsyncDataState[String, Int]](AsyncDataLoading())
    val value = AsyncState(state.observable)
    state.set(AsyncDataSuccess(1))
    state.set(AsyncDataSuccess(2))
    value.futureFirstValue.map { first =>
      assertEquals(first, 1)
      assertEquals(value.stateNow().value, Some(2))
      assertEquals(value.observeLoadedValues.now(), Some(2))
    }
  }

  test("AsyncFuture exposes loading state and preserves failure metadata") {
    val pending = Promise[Either[FailureInfo[String], Int]]()
    val value = AsyncFuture(pending.future)
    assert(value.stateNow().isLoading)
    pending.success(Left(FailureInfo(new IllegalStateException("offline"), "details")))
    value.futureFirstState.map { finished =>
      assertEquals(finished.failure.get.data, Some("details"))
      assertEquals(finished.failure.get.error.msg, "offline")
    }
  }

  test("AsyncFuture converts a failed underlying future into a finished failed state") {
    val value = AsyncFuture[String, Int](Future.failed(new IllegalStateException("transport")))
    value.futureFirstState.map(finished => assertEquals(finished.failure.get.error.msg, "transport"))
  }

  test("AsyncFuture mapAsync returns failures for synchronous throws and failed mapped futures") {
    val value = AsyncFuture[String, Int](Future.successful(Right(1)))
    val synchronous = value.mapAsync[Int](_ => throw new IllegalStateException("sync mapping"))
    val asynchronous = value.mapAsync[Int](_ => Future.failed(new IllegalStateException("async mapping")))
    synchronous.futureFirstState.zip(asynchronous.futureFirstState).map { (sync, async) =>
      assert(sync.failure.get.error.msg.contains("sync mapping"))
      assert(async.failure.get.error.msg.contains("async mapping"))
    }
  }

  test("AsyncValue asynchronous mapping and recovery publish results") {
    val value = AsyncValue[String, Int](AsyncDataSuccess(3))
    value.mapAsync(n => Future.successful(n * 2)).futureFirstValue.flatMap { mapped =>
      assertEquals(mapped, 6)
      val failed = AsyncValue[String, Int](AsyncDataFailed(new IllegalStateException("bad"), Some("info")))
      failed.recoverOnErrorPushError(_ => Future.successful(AsyncDataSuccess(9))).futureFirstValue.map(n => assertEquals(n, 9))
    }
  }

  test("AsyncValue mapping catches synchronous throws rather than leaving loading futures") {
    val value = AsyncValue[String, Int](AsyncDataSuccess(3))
    value.mapAsync[Int](_ => throw new IllegalStateException("mapping failed")).futureFirstState.map { failed =>
      assert(failed.failure.get.error.msg.contains("mapping failed"))
    }
  }

  test("combined async values propagate success and failures") {
    val a = AsyncValue[String, Int](AsyncDataSuccess(2))
    val b = AsyncValue[String, String](AsyncDataSuccess("value"))
    a.combineIgnoreErrorData(b).futureFirstValue.flatMap { pair =>
      assertEquals(pair, (2, "value"))
      val failure = AsyncValue[String, Int](AsyncDataFailed(new IllegalStateException("bad")))
      failure.combineIgnoreErrorData(b).futureFirstState.map { state =>
        assert(state.failure.get.error.msg.contains("Left Element"))
      }
    }
  }

  test("FailureInfo maps optional metadata while retaining its serialized cause") {
    val error = SerializedException("bad input")
    val info = FailureInfo(error, Some("details"))
    assertEquals(info.map(_.length).data, Some(7))
    assertEquals(info.map(_.length).error, error)
    assertEquals(FailureInfo[String](error, None).map(_.length).data, None)
    val restored = read[FailureInfo[String]](write(info))
    assertEquals(restored.error.msg, "bad input")
    assertEquals(restored.data, Some("details"))
    assertEquals(write(restored), write(info))
  }

  test("sealed async states and value models have default codecs") {
    val states: List[AsyncDataState[String, Int]] = List(AsyncDataLoading(), AsyncDataSuccess(4),
      AsyncDataFailed(new IllegalArgumentException("bad"), Some("details")))
    states.foreach { state =>
      val restored = read[AsyncDataState[String, Int]](write(state))
      assertEquals(restored.isLoading, state.isLoading)
      assertEquals(restored.value, state.value)
      assertEquals(restored.failure.map(_.data), state.failure.map(_.data))
      assertEquals(write(restored), write(state))
    }
    val value = AsyncValue[String, Int](AsyncDataSuccess(4))
    assertEquals(read[AsyncValue[String, Int]](write(value)).stateNow().value, Some(4))
  }
  test("ignore-error recovery converts throwing and failed callbacks into finished failures") {
    val value = AsyncValue[String, Int](AsyncDataFailed(new IllegalStateException("original")))
    val throwing = value.recoverOnErrorIgnoreError(_ => throw new IllegalStateException("synchronous recovery"))
    val failed = value.recoverOnErrorIgnoreError(_ => Future.failed(new IllegalStateException("failed recovery")))
    throwing.futureFirstState.zip(failed.futureFirstState).map { (first, second) =>
      assert(first.failure.get.error.msg.contains("synchronous recovery"))
      assert(second.failure.get.error.msg.contains("failed recovery"))
    }
  }
  test("ignore-error recovery leaves successful values intact and permits successful recovery") {
    val success = AsyncValue[String, Int](AsyncDataSuccess(4))
    val untouched = success.recoverOnErrorIgnoreError(_ => fail("recovery should not run for a successful value"))
    val failed = AsyncValue[String, Int](AsyncDataFailed(new IllegalStateException("original")))
    val recovered = failed.recoverOnErrorIgnoreError(_ => Future.successful(AsyncDataSuccess(9)))
    untouched.futureFirstValue.zip(recovered.futureFirstValue).map { (first, second) =>
      assertEquals(first, 4)
      assertEquals(second, 9)
    }
  }

}
