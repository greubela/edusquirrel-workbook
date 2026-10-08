package it.evadid.core.datastructures.storage

import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.async.AsyncDataState.*
import it.evadid.core.datastructures.state.async.AsyncDataState
import it.evadid.core.datastructures.storage.AsyncDataCache.*
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import munit.FunSuite

import java.time.LocalDateTime
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future, Promise}

class AsyncDataCacheSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val logger = Logger.withNameAndPrefixes(printMap = PrintToStdLogger.printNothing)

  private class Cache(retry: Boolean = true) extends AsyncDataCache[String, String](logger, retry) {
    val requests = ListBuffer.empty[(String, Promise[String])]
    override protected def executeLoading(input: String)(ec: ExecutionContext): Future[String] = {
      val pending = Promise[String]()
      requests += input -> pending
      pending.future
    }
    override protected def formatInputForLogging(input: String): String = input
    override protected def formatOutputForLogging(output: String): String = output
  }

  test("concurrent requests share one load and successful results are reused") {
    val cache = new Cache()
    val first = cache.loadAsFuture("a")
    val second = cache.loadAsFuture("a")
    assertEquals(cache.requests.size, 1)
    assertEquals(cache.cacheCopy(), Map.empty[String, String])
    cache.requests.head._2.success("loaded")
    first.zip(second).flatMap { values =>
      assertEquals(values, ("loaded", "loaded"))
      assertEquals(cache.getSyncIfInCache("a", false), Some("loaded"))
      assertEquals(cache.cacheCopy(), Map("a" -> "loaded"))
      cache.loadAsFuture("a").map { value =>
        assertEquals(value, "loaded")
        assertEquals(cache.requests.size, 1)
      }
    }
  }

  test("force reload replaces a successful request and updates its existing observable") {
    val cache = new Cache()
    val observable = cache.loadIntoVariable("a")
    val oldPublished = Promise[Unit]()
    val newPublished = Promise[Unit]()
    observable.observeAllStates.addObserver(state => {
      if (state.value.contains("old")) oldPublished.trySuccess(())
      if (state.value.contains("new")) newPublished.trySuccess(())
    })
    assert(observable.stateNow().isLoading)
    val first = cache.loadAsFuture("a")
    cache.requests.head._2.success("old")
    first.zip(oldPublished.future).flatMap { _ =>
      assertEquals(observable.stateNow().value, Some("old"))
      val next = cache.loadAsFuture("a", forceReloading = true)
      assertEquals(cache.requests.size, 2)
      cache.requests.last._2.success("new")
      next.zip(newPublished.future).map { (value, _) =>
        assertEquals(value, "new")
        assertEquals(observable.stateNow().value, Some("new"))
      }
    }
  }

  test("failed requests retain the cause when automatic retries are disabled") {
    val cache = new Cache(retry = false)
    val failure = new IllegalStateException("offline")
    val first = cache.loadAsFuture("a")
    cache.requests.head._2.failure(failure)
    first.failed.flatMap { error =>
      assert(error eq failure)
      assertEquals(cache.cacheCopy(), Map.empty[String, String])
      cache.loadAsFuture("a").failed.map { nextError =>
        assert(nextError eq failure)
        assertEquals(cache.requests.size, 1)
      }
    }
  }

  test("failed requests retry on the next fetch and recover") {
    val cache = new Cache()
    val first = cache.loadAsFuture("a")
    cache.requests.head._2.failure(new IllegalStateException("offline"))
    first.failed.flatMap { _ =>
      val retry = cache.loadAsFuture("a")
      assertEquals(cache.requests.size, 2)
      cache.requests.last._2.success("recovered")
      retry.map(value => assertEquals(value, "recovered"))
    }
  }

  test("deleting a result removes it from snapshots and the next request reloads it") {
    val cache = new Cache()
    val first = cache.loadAsFuture("a")
    cache.requests.head._2.success("old")
    first.flatMap { _ =>
      cache.removeFromCache(List("a", "unknown"))
      assertEquals(cache.cacheCopy(), Map.empty[String, String])
      assertEquals(cache.getSyncIfInCache("a", false), None)
      val next = cache.loadAsFuture("a")
      assertEquals(cache.requests.size, 2)
      cache.requests.last._2.success("new")
      next.map(value => assertEquals(value, "new"))
    }
  }

  test("batch requests preserve successes and failures independently") {
    val cache = new Cache(retry = false)
    val error = new IllegalArgumentException("bad input")
    val batch = cache.loadAllAsFuture(List("good", "bad"))
    cache.requests.find(_._1 == "good").get._2.success("result")
    cache.requests.find(_._1 == "bad").get._2.failure(error)
    batch.map { result =>
      assertEquals(result("good"), Right("result"))
      assertEquals(result("bad"), Left(error))
    }
  }

  test("SucceededRequest retains its timestamp, state and completed future") {
    val cache = new Cache()
    val state = State[AsyncDataState[Nothing, String]](AsyncDataSuccess("value"))
    val timestamp = LocalDateTime.of(2026, 1, 1, 0, 0)
    val request = SucceededRequest(cache, state, "value", timestamp)
    assertEquals(request.outputIfPresent, Some("value"))
    assertEquals(request.requestCompletedAt, Some(timestamp))
    assert(request.getVariable eq state)
    request.createFuture.map(value => assertEquals(value, "value"))
  }

  test("StartedRequest notifies all waiting futures and publishes success") {
    val cache = new Cache()
    val state = State[AsyncDataState[Nothing, String]](AsyncDataLoading())
    val request = StartedRequest(cache, "a", state)
    assertEquals(request.outputIfPresent, None)
    assertEquals(request.requestCompletedAt, None)
    assert(request.getVariable eq state)
    val first = request.createFuture
    val second = request.createFuture
    request.succeeded("done")
    first.zip(second).map { result =>
      assertEquals(result, ("done", "done"))
      assertEquals(state.now().value, Some("done"))
    }
  }

  test("FailedRequest exposes its state and timestamp without an output") {
    val cache = new Cache(retry = false)
    val state = State[AsyncDataState[Nothing, String]](AsyncDataLoading())
    val cause = new IllegalStateException("failed")
    val timestamp = LocalDateTime.of(2026, 1, 1, 0, 0)
    val request = FailedRequest(cache, "a", state, cause, timestamp)
    assertEquals(request.outputIfPresent, None)
    assertEquals(request.requestCompletedAt, Some(timestamp))
    assert(request.getVariable eq state)
    request.createFuture.failed.map(error => assert(error eq cause))
  }

  test("DeletedRequest reloads while preserving the bound state") {
    val cache = new Cache()
    val state = State[AsyncDataState[Nothing, String]](AsyncDataSuccess("old"))
    val request = DeletedRequest(cache, "a", state)
    val published = Promise[Unit]()
    state.observable.addObserver(value => if (value.value.contains("new")) published.trySuccess(()))
    assertEquals(request.outputIfPresent, None)
    assertEquals(request.requestCompletedAt, None)
    val loaded = request.createFuture
    cache.requests.head._2.success("new")
    loaded.zip(published.future).map { (value, _) =>
      assertEquals(value, "new")
      assertEquals(state.now().value, Some("new"))
    }
  }

  test("age-based loads reuse a result at its timestamp and reload older results") {
    val cache = new Cache()
    val before = LocalDateTime.now().minusSeconds(1)
    val first = cache.loadAsFuture("a")
    cache.requests.head._2.success("old")
    first.flatMap { _ =>
      cache.loadAsFuture("a", before).flatMap { reused =>
        assertEquals(reused, "old")
        assertEquals(cache.requests.size, 1)
        val next = cache.loadAsFuture("a", LocalDateTime.now().plusDays(1))
        assertEquals(cache.requests.size, 2)
        cache.requests.last._2.success("new")
        next.map(value => assertEquals(value, "new"))
      }
    }
  }

  test("empty batch loads do not invoke the loader") {
    val cache = new Cache()
    cache.loadAllAsFuture(List.empty[String], LocalDateTime.now()).map { result =>
      assertEquals(result, Map.empty[String, Either[Throwable, String]])
      assertEquals(cache.requests.size, 0)
    }
  }
}
