package it.evadid.homepage.control.change

import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.core.datastructures.language.serialization.abstractions.{LanguageMapEntry, ParsedTriples}
import it.evadid.homepage.control.change.LanguageMapStorageControl.STARTUP_STRATEGY.*
import munit.FunSuite

import scala.concurrent.{ExecutionContext, Future, Promise}

class LanguageCacheStartupSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val empty = ParsedTriples(Set.empty, Set.empty)
  private val cached = ParsedTriples(Set(LanguageMapEntry[AppLanguage.HumanLanguage](LanguageMapContentId("basic/hello"), AppLanguage.English, "Hello")), Set.empty)

  private class Harness {
    val local = Promise[ParsedTriples]()
    val remote = Promise[Unit]()
    var installed = List.empty[ParsedTriples]
    var errors = List.empty[Throwable]
    var scheduled = List.empty[() => Unit]
    var refreshCalls = 0
    def start(strategy: LanguageMapStorageControl.STARTUP_STRATEGY) = LanguageCacheStartup.run(
      strategy, () => local.future, triples => installed = installed :+ triples,
      () => { refreshCalls += 1; remote.future },
      work => scheduled = scheduled :+ work,
      error => errors = errors :+ error)
  }

  test("cached startup completes before scheduled remote refresh and remains successful if refresh fails") {
    val h = new Harness
    val ready = h.start(CONTINUE_AFTER_LOCAL_CACHE_SUCCESS)
    h.local.success(cached)
    ready.flatMap { _ =>
      assertEquals(h.installed, List(cached))
      assertEquals(h.refreshCalls, 0)
      assertEquals(h.scheduled.size, 1)
      h.scheduled.head()
      h.remote.failure(new IllegalStateException("offline"))
      Future.unit.map { _ =>
        assertEquals(h.refreshCalls, 1)
        assert(ready.value.get.isSuccess)
        assertEquals(h.errors.map(_.getMessage), List("offline"))
      }
    }
  }

  test("empty cache waits for the first remote load") {
    val h = new Harness
    val ready = h.start(CONTINUE_AFTER_LOCAL_CACHE_SUCCESS)
    h.local.success(empty)
    Future.unit.flatMap { _ =>
      assert(ready.value.isEmpty)
      assertEquals(h.refreshCalls, 1)
      assertEquals(h.installed, Nil)
      h.remote.success(())
      ready
    }
  }

  test("corrupt cache falls back to remote without installing anything") {
    val h = new Harness
    val ready = h.start(CONTINUE_AFTER_LOCAL_CACHE_SUCCESS)
    h.local.failure(new IllegalArgumentException("corrupt"))
    Future.unit.flatMap { _ =>
      assert(ready.value.isEmpty)
      assertEquals(h.errors.map(_.getMessage), List("corrupt"))
      assertEquals(h.installed, Nil)
      h.remote.success(())
      ready
    }
  }

  test("full-load strategy installs cache but waits for remote completion") {
    val h = new Harness
    val ready = h.start(CONTINUE_AFTER_FULL_LOAD)
    h.local.success(cached)
    Future.unit.flatMap { _ =>
      assertEquals(h.installed, List(cached))
      assertEquals(h.refreshCalls, 1)
      assertEquals(h.scheduled, Nil)
      assert(ready.value.isEmpty)
      h.remote.success(())
      ready
    }
  }

  test("immediate strategy does not wait for either cache or remote") {
    val h = new Harness
    val ready = h.start(CONTINUE_IMMEDIATELY)
    assert(ready.value.get.isSuccess)
    h.local.success(cached)
    ready
  }

  test("a synchronously unavailable IndexedDB falls back to remote") {
    var installed = false
    val ready = LanguageCacheStartup.run(CONTINUE_AFTER_LOCAL_CACHE_SUCCESS,
      () => throw new IllegalStateException("IndexedDB unavailable"),
      _ => installed = true, () => Future.unit, work => work(), _ => ())
    ready.map(_ => assert(!installed))
  }

  test("a cold-start remote failure fails readiness rather than hanging") {
    val ready = LanguageCacheStartup.run(CONTINUE_AFTER_LOCAL_CACHE_SUCCESS,
      () => Future.successful(empty), _ => (),
      () => Future.failed(new IllegalStateException("offline")), work => work(), _ => ())
    ready.transform { result =>
      assertEquals(result.failed.get.getMessage, "offline")
      scala.util.Success(())
    }
  }
}
