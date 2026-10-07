package it.evadid.core.datastructures.state

import it.evadid.core.datastructures.state.observable.{ObservableValueImpl, ObserverDerivationLogic}
import it.evadid.core.datastructures.storage.RemoteCacheController
import it.evadid.core.datastructures.storage.RemoteCacheCollection.CacheKey
import it.evadid.core.datastructures.storage.RemoteSyncDataCache.*
import it.evadid.util.logging.{Logger, LoggingLevel}
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.SyncSuccess
import munit.FunSuite

import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import scala.collection.mutable
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

class StateObservableValueJvmAsyncTest extends FunSuite {

  test("DerivedObservableValue currentValueOrWaitForUpdate resolves while async derivation is still running") {
    val gate = Promise[Unit]()
    val base = State(1)

    val derived = base.observable.deriveValue(
      withFunc = value => {
        Await.result(gate.future, 1.second)
        value * 100
      },
      executeFunctionWith = ExecutionMethod.executeAsync,
      deriveLogic = ObserverDerivationLogic.DeriveOnlyLastValues
    )

    base.set(2)

    val runningFuture = derived.currentValueOrWaitForUpdate
    assert(!runningFuture.isCompleted)

    gate.success(())
    assertEquals(Await.result(runningFuture, 1.second), 100)
    assertEquals(Await.result(derived.currentValueOrWaitForUpdate, 1.second), 200)
  }

  test("JVM async derivations can run in true parallel using dedicated thread pool") {
    val ec = scala.concurrent.ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))
    try {
      val exec = ExecutionMethod.ExecuteLocalAsync(ec)
      val started = new CountDownLatch(2)
      val release = new CountDownLatch(1)
      val inFlight = new AtomicInteger(0)
      val maxInFlight = new AtomicInteger(0)

      def trackParallelism(): Unit = {
        val now = inFlight.incrementAndGet()
        maxInFlight.updateAndGet(cur => math.max(cur, now))
        started.countDown()
        release.await(1, TimeUnit.SECONDS)
        inFlight.decrementAndGet()
      }

      val left = State(1).observable.deriveValue(
        withFunc = value => { trackParallelism(); value + 1 },
        executeFunctionWith = exec,
        deriveLogic = ObserverDerivationLogic.DeriveOnlyLastValues
      )

      val right = State(10).observable.deriveValue(
        withFunc = value => { trackParallelism(); value + 1 },
        executeFunctionWith = exec,
        deriveLogic = ObserverDerivationLogic.DeriveOnlyLastValues
      )

      assert(started.await(1, TimeUnit.SECONDS), "Both async derivations should start")
      release.countDown()

      assertEquals(Await.result(left.currentValueOrWaitForUpdate, 1.second), 2)
      assertEquals(Await.result(right.currentValueOrWaitForUpdate, 1.second), 11)
      assert(maxInFlight.get() >= 2, s"expected true parallelism, max in flight was ${maxInFlight.get()}")
    } finally {
      ec.shutdown()
      assert(ec.awaitTermination(2, TimeUnit.SECONDS))
    }
  }

  private given ExecutionContext = ExecutionContext.global
  private val fetchedAt = LocalDateTime.of(2099, 1, 1, 0, 0)
  private val dataAt = fetchedAt.minusSeconds(100)
  private val invalidJava = "int result = ;"
  private val editedJava = "int result = 7;"
  private val silentLogger = SyncLogger(new Logger {
    override def log(message: String, level: LoggingLevel): Unit = ()
    override def getOut(): String = ""
    override def getErr(): String = ""
  })

  private class Calls[A] {
    private val entries = mutable.ArrayBuffer.empty[A]
    private val waiting = mutable.Map.empty[Int, Promise[A]]
    def add(entry: A): Unit = synchronized {
      val index = entries.size
      entries += entry
      waiting.remove(index).foreach(_.success(entry))
    }
    def at(index: Int): Future[A] = synchronized {
      if (index < entries.size) Future.successful(entries(index))
      else waiting.getOrElseUpdate(index, Promise[A]()).future
    }
    def snapshot: List[A] = synchronized(entries.toList)
    def size: Int = synchronized(entries.size)
  }

  private class Source(val name: String, initiallyAutomatic: Boolean = false) extends CacheKey[String, String] {
    private var automatic = initiallyAutomatic
    val reads = new Calls[Promise[FetchResponse[String, String]]]
    val writes = new Calls[(Map[String, String], Promise[SyncSuccess])]
    def stopAutomaticReads(): Unit = synchronized { automatic = false }
    def response(at: LocalDateTime = fetchedAt): FetchResponse[String, String] = new FetchResponse[String, String] {
      override def timestampFetchResponse: LocalDateTime = at
      override def fetchedValues: Set[DataEntryReadFromServer[String, String]] = Set(
        DataEntryReadFromServer("exercise", invalidJava, dataAt),
        DataEntryReadFromServer(s"$name-only", name, dataAt)
      )
    }
    override val reader: RemoteDataReader[String, String] = new RemoteDataReader[String, String] {
      override def fetchByKey(logger: SyncLogger, key: String): Future[FetchResponse[String, String]] = fetchAll(logger)
      override def fetchAll(logger: SyncLogger): Future[FetchResponse[String, String]] = Source.this.synchronized {
        val result = Promise[FetchResponse[String, String]]()
        reads.add(result)
        if (automatic) result.success(response())
        result.future
      }
    }
    override val writer: RemoteDataWriter[String, String] = new RemoteDataWriter[String, String] {
      override def writeForKey(logger: SyncLogger, key: String, value: String): Future[SyncSuccess] = writeAll(logger, Map(key -> value))
      override def writeAll(logger: SyncLogger, values: Map[String, String]): Future[SyncSuccess] = {
        val result = Promise[SyncSuccess]()
        writes.add(values -> result)
        result.future
      }
    }
    override def toString: String = name
  }

  private case class Hook(keys: List[Source], known: Set[String], done: Promise[Unit])
  private class Hooks {
    val calls = new Calls[Hook]
    private val actions = mutable.Queue.empty[Hook => Unit]
    def next(action: Hook => Unit): Unit = synchronized { actions.enqueue(action) }
    def invoke(keys: List[Source], known: Set[String]): Future[Unit] = {
      val hook = Hook(keys, known, Promise[Unit]())
      val action = synchronized {
        if (actions.nonEmpty) actions.dequeue() else (h: Hook) => { h.done.success(()); () }
      }
      calls.add(hook)
      action(hook)
      hook.done.future
    }
  }

  private class CacheFixture(initialKeys: Option[List[Source]] = None) {
    val keys = ObservableValueImpl[List[Source]](initialKeys)
    val hooks = new Hooks
    val controller = new RemoteCacheController[String, String, Source](silentLogger, keys) {
      override protected def onCacheKeyChange(newKeys: List[Source], knownKeys: Set[String]): Future[?] =
        hooks.invoke(newKeys, knownKeys)
    }
    def rebind(sources: Source*): Unit = keys.onNewValueArrived(Success(sources.toList))
    def ready(source: Source): Future[Unit] = {
      val result = Promise[Unit]()
      controller.observableCache.addObserver { cache =>
        val report = cache.createReportFor("exercise")
        if (report.allAvailableCacheKeys == List(source) && report.cacheStatus(source).lastKnownRemoteValue.exists(_.dataValue == invalidJava))
          result.trySuccess(())
      }
      result.future
    }
    def assertCurrent(source: Source): Unit = {
      val report = controller.currentReport("exercise")
      assertEquals(report.allAvailableCacheKeys, List(source))
      assertEquals(report.cacheStatus(source).lastKnownRemoteValue.map(_.dataValue), Some(invalidJava))
    }
  }

  private def withLoadedSource(): Future[(CacheFixture, Source)] = {
    val fixture = new CacheFixture
    val source = new Source("A", initiallyAutomatic = true)
    fixture.rebind(source)
    fixture.ready(source).flatMap(_ => fixture.controller.ensureMaxAgeSafe(fetchedAt)).map { _ =>
      source.stopAutomaticReads()
      fixture -> source
    }
  }

  private def entry = List(DataEntryToWriteToServer("exercise", editedJava, dataAt.plusSeconds(1)))

  test("RemoteCacheController loads nonempty initial keys before a queued update completes") {
    val source = new Source("initial", initiallyAutomatic = true)
    val fixture = new CacheFixture(Some(List(source)))
    fixture.ready(source).flatMap(_ => fixture.controller.requestCacheDependentUpdate(_ => fetchedAt)).map { _ =>
      fixture.assertCurrent(source)
      assert(source.reads.size > 0)
    }
  }

  test("RemoteCacheController ensure, update and store wait for empty-to-user rebind") {
    val fixture = new CacheFixture
    val source = new Source("user")
    val evaluated = new Calls[Source]
    fixture.hooks.next(_ => ())
    fixture.rebind(source)
    for {
      hook <- fixture.hooks.calls.at(0)
      ensure = fixture.controller.ensureMaxAgeSafe(fetchedAt)
      update = fixture.controller.requestCacheDependentUpdate { key => evaluated.add(key); fetchedAt }
      store = fixture.controller.requestCacheDependentStore { key => evaluated.add(key); entry }
      _ = hook.done.success(())
      read <- source.reads.at(0)
      _ = read.success(source.response())
      _ <- ensure
      _ <- update
      _ = assertEquals(evaluated.snapshot.headOption, Some(source))
      write <- source.writes.at(0)
      _ = assertEquals(write._1, Map("exercise" -> editedJava))
      _ = write._2.success(SyncSuccess.emptyNow())
      _ <- store
    } yield {
      assertEquals(evaluated.snapshot, List(source, source))
      fixture.assertCurrent(source)
    }
  }

  test("RemoteCacheController delayed old fetch cannot overwrite a rebind or redirect a queued store") {
    withLoadedSource().flatMap { (fixture, oldSource) =>
      val source = new Source("B")
      val evaluated = new Calls[Source]
      val firstRead = oldSource.reads.size
      val update = fixture.controller.requestCacheDependentUpdate(_ => fetchedAt.plusSeconds(1))
      for {
        oldRead <- oldSource.reads.at(firstRead)
        _ = fixture.rebind(source)
        store = fixture.controller.requestCacheDependentStore { key => evaluated.add(key); entry }
        _ = oldRead.success(oldSource.response(fetchedAt.plusSeconds(1)))
        _ <- update
        newRead <- source.reads.at(0)
        _ = newRead.success(source.response())
        write <- source.writes.at(0)
        _ = write._2.success(SyncSuccess.emptyNow())
        _ <- store
      } yield {
        assertEquals(evaluated.snapshot, List(source))
        assertEquals(oldSource.writes.size, 0)
        fixture.assertCurrent(source)
      }
    }
  }

  test("RemoteCacheController A-to-B-to-A hooks receive the preceding cache keys in order") {
    withLoadedSource().flatMap { (fixture, sourceA) =>
      val sourceB = new Source("B")
      val firstHook = fixture.hooks.calls.size
      val firstReadA = sourceA.reads.size
      fixture.hooks.next(_ => ())
      fixture.rebind(sourceB)
      for {
        hookB <- fixture.hooks.calls.at(firstHook)
        _ = fixture.rebind(sourceA)
        update = fixture.controller.requestCacheDependentUpdate(_ => fetchedAt)
        _ = hookB.done.success(())
        readB <- sourceB.reads.at(0)
        _ = readB.success(sourceB.response())
        hookA <- fixture.hooks.calls.at(firstHook + 1)
        _ = assertEquals(hookA.known, Set("exercise", "B-only"))
        readA <- sourceA.reads.at(firstReadA)
        _ = readA.success(sourceA.response())
        _ <- update
      } yield fixture.assertCurrent(sourceA)
    }
  }

  test("RemoteCacheController failed update propagates and a queued retry still succeeds") {
    withLoadedSource().flatMap { (fixture, source) =>
      val firstRead = source.reads.size
      val failure = new IllegalStateException("fetch failed")
      val update = fixture.controller.requestCacheDependentUpdate(_ => fetchedAt.plusSeconds(1))
      for {
        read <- source.reads.at(firstRead)
        retry = fixture.controller.requestCacheDependentUpdate(_ => fetchedAt.plusSeconds(1))
        _ = read.failure(failure)
        result <- update.transform(result => Success(result))
        _ = assertEquals(result, Failure(failure))
        nextRead <- source.reads.at(firstRead + 1)
        _ = nextRead.success(source.response(fetchedAt.plusSeconds(1)))
        _ <- retry
      } yield fixture.assertCurrent(source)
    }
  }

  List(false, true).foreach { synchronous =>
    test(s"RemoteCacheController recovers ${if (synchronous) "synchronous" else "asynchronous"} hook failures and continues") {
      val fixture = new CacheFixture
      val source = new Source("user")
      val evaluated = new Calls[Source]
      fixture.hooks.next { hook =>
        val failure = new IllegalStateException("hook failed")
        if (synchronous) throw failure else hook.done.failure(failure)
      }
      fixture.rebind(source)
      val update = fixture.controller.requestCacheDependentUpdate { key => evaluated.add(key); fetchedAt }
      for {
        read <- source.reads.at(0)
        _ = read.success(source.response())
        _ <- update
      } yield {
        assertEquals(evaluated.snapshot, List(source))
        fixture.assertCurrent(source)
      }
    }
  }

  test("RemoteCacheController allows a key-change hook to emit the next rebind") {
    val fixture = new CacheFixture
    val sourceA = new Source("A")
    val sourceB = new Source("B")
    fixture.hooks.next { hook => fixture.rebind(sourceB); hook.done.success(()); () }
    fixture.rebind(sourceA)
    for {
      readA <- sourceA.reads.at(0)
      _ = readA.success(sourceA.response())
      hookB <- fixture.hooks.calls.at(1)
      _ = assertEquals(hookB.known, Set("exercise", "A-only"))
      readB <- sourceB.reads.at(0)
      update = fixture.controller.requestCacheDependentUpdate(_ => fetchedAt)
      _ = readB.success(sourceB.response())
      _ <- update
    } yield fixture.assertCurrent(sourceB)
  }

  test("RemoteCacheController delayed old write completes before a rebind and cannot restore old cache keys") {
    withLoadedSource().flatMap { (fixture, oldSource) =>
      val source = new Source("B")
      val evaluated = new Calls[Source]
      val store = fixture.controller.requestCacheDependentStore(_ => entry)
      for {
        write <- oldSource.writes.at(0)
        _ = fixture.rebind(source)
        update = fixture.controller.requestCacheDependentUpdate { key => evaluated.add(key); fetchedAt }
        _ = write._2.success(SyncSuccess.emptyNow())
        _ <- store
        read <- source.reads.at(0)
        _ = read.success(source.response())
        _ <- update
      } yield {
        assertEquals(write._1, Map("exercise" -> editedJava))
        assertEquals(evaluated.snapshot, List(source))
        assertEquals(source.writes.size, 0)
        fixture.assertCurrent(source)
      }
    }
  }

  test("RemoteCacheController synchronous update callback failure becomes a failed future and does not stop the queue") {
    withLoadedSource().flatMap { (fixture, source) =>
      val failure = new IllegalStateException("update callback failed")
      val evaluated = new Calls[Source]
      val update = fixture.controller.requestCacheDependentUpdate(_ => throw failure)
      val retry = fixture.controller.requestCacheDependentUpdate { key => evaluated.add(key); fetchedAt }
      for {
        result <- update.transform(result => Success(result))
        _ = assertEquals(result, Failure(failure))
        _ <- retry
      } yield {
        assertEquals(evaluated.snapshot, List(source))
        fixture.assertCurrent(source)
      }
    }
  }
}
