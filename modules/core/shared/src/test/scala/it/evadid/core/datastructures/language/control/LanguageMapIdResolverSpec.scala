package it.evadid.core.datastructures.language.control

import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage}
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.observable.ConstantValueObservable
import munit.FunSuite
import scala.concurrent.{ExecutionContext, Future, Promise}

class LanguageMapIdResolverSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val first = LanguageMapContentId("test/first")
  private val second = LanguageMapContentId("test/second")
  private val translations = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(English -> "hello", German -> "hallo"))

  test("resolution follows the current language across calls") {
    val language = State[HumanLanguage](German)
    val resolver = new LanguageMapIdResolver(language.observable) {
      def resolveMap(id: LanguageMapContentId): Future[LanguageMap[HumanLanguage]] = Future.successful(translations)
    }
    resolver.resolveToString(first).flatMap { text =>
      assertEquals(text, "hallo")
      language.set(English)
      resolver.resolveToString(first).map(text => assertEquals(text, "hello"))
    }
  }

  test("missing selected-language translations fall back to English") {
    val resolver = new LanguageMapIdResolver(ConstantValueObservable[HumanLanguage](German)) {
      def resolveMap(id: LanguageMapContentId): Future[LanguageMap[HumanLanguage]] =
        Future.successful(LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(English -> "fallback")))
    }
    resolver.resolveToString(first).map(text => assertEquals(text, "fallback"))
  }

  test("single resolution preserves source failures") {
    val error = new IllegalArgumentException("missing map")
    val resolver = new LanguageMapIdResolver(ConstantValueObservable[HumanLanguage](English)) {
      def resolveMap(id: LanguageMapContentId): Future[LanguageMap[HumanLanguage]] = Future.failed(error)
    }
    resolver.resolveToString(first).failed.map(actual => assertEquals(actual, error))
  }

  test("batch resolution omits failed entries and returns successful ones") {
    val resolver = new LanguageMapIdResolver(ConstantValueObservable[HumanLanguage](English)) {
      def resolveMap(id: LanguageMapContentId): Future[LanguageMap[HumanLanguage]] =
        if (id == second) Future.failed(new IllegalArgumentException("unavailable")) else Future.successful(translations)
    }
    resolver.resolveAll(Seq(first, second)).map { (resolved, language) =>
      assertEquals(resolved, Map(first -> "hello"))
      assertEquals(language, English)
    }
  }

  test("empty batches succeed with the current language") {
    val resolver = new LanguageMapIdResolver(ConstantValueObservable[HumanLanguage](German)) {
      def resolveMap(id: LanguageMapContentId): Future[LanguageMap[HumanLanguage]] = throw new AssertionError("unexpected lookup")
    }
    resolver.resolveAll(Nil).map { (resolved, language) =>
      assertEquals(resolved, Map.empty[LanguageMapContentId, String])
      assertEquals(language, German)
    }
  }

  test("resolution waits for an asynchronously supplied map") {
    val pending = Promise[LanguageMap[HumanLanguage]]()
    val resolver = new LanguageMapIdResolver(ConstantValueObservable[HumanLanguage](English)) {
      def resolveMap(id: LanguageMapContentId): Future[LanguageMap[HumanLanguage]] = pending.future
    }
    val text = resolver.resolveToString(first)
    assert(!text.isCompleted)
    pending.success(translations)
    text.map(actual => assertEquals(actual, "hello"))
  }
}
