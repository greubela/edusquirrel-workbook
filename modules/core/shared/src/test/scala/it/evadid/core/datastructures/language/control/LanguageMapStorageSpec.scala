package it.evadid.core.datastructures.language.control

import it.evadid.core.datastructures.language.{LanguageMapContentId, LanguageMap}
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.serialization.abstractions.{LanguageMapEntry, ParsedTriples}
import it.evadid.core.datastructures.language.serialization.LanguageMapInputSource
import it.evadid.util.logging.{BasicLogger, Logger}
import scala.concurrent.Future
import munit.FunSuite
import upickle.default.*

class LanguageMapStorageSpec extends FunSuite {
  private val id = LanguageMapContentId("test", "message")

  test("storage reports triple counts and numeric language coverage without dumping translations") {
    val entries = Set(LanguageMapEntry[HumanLanguage](id, English, "private English text"),
      LanguageMapEntry[HumanLanguage](id, German, "private German text"),
      LanguageMapEntry[HumanLanguage](LanguageMapContentId("test", "other"), English, "private other text"))
    val logger = BasicLogger()
    val storage = LanguageMapStorage.empty.withLoadedTriples(logger, Set.empty, ParsedTriples(entries, Set.empty))
    assertEquals(storage.parsedTriples.size, 3)
    assertEquals(storage.languageMaps.size, 2)
    assert(logger.getOut().contains("from 0 to 3 triples"))
    assert(logger.getOut().contains("fewest entries (1)"))
    assert(logger.getOut().contains("most entries (2)"))
    assert(!logger.getOut().contains("private"))
    val restored = read[LanguageMapStorage](write(storage))
    assertEquals(restored.parsedTriples, storage.parsedTriples)
    assertEquals(restored.languageMaps(id).getInLanguage(German), "private German text")
  }

  test("empty storage has a useful coverage summary") {
    val logger = BasicLogger()
    val storage = LanguageMapStorage.empty.withLoadedTriples(logger, Set.empty, ParsedTriples(Set.empty, Set.empty))
    assert(storage.languageMaps.isEmpty)
    assert(logger.getOut().contains("no maps are present"))
  }

  test("merging loaded triples tracks all sources without triggering IO or persisting runtime sources") {
    def source = new LanguageMapInputSource {
      def loadAllTriples(logger: Logger): Future[ParsedTriples] = throw AssertionError("Unexpected IO")
    }
    val previous = source
    val additional = source
    val storage = LanguageMapStorage(ParsedTriples(Set.empty, Set.empty), Set(previous))
      .withLoadedTriples(BasicLogger(), Set(additional), ParsedTriples(Set.empty, Set.empty))
    assertEquals(storage.loadedSources, Set(previous, additional))
    assertEquals(read[LanguageMapStorage](write(storage)).loadedSources, Set.empty[LanguageMapInputSource])
  }

  test("joined language maps preserve language intersections, empty input and trailing separators") {
    val first = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(English -> "one", German -> "eins"))
    val second = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(English -> "two"))
    val joined = LanguageMap.mkLanguageMap[HumanLanguage]("[", ";", "]", List(first, second))
    assertEquals(joined.availableLanguages, Set[HumanLanguage](English))
    assertEquals(joined.getInLanguage(English), "[one;two;]")
    assertEquals(LanguageMap.mkLanguageMap[HumanLanguage]("[", ";", "]", Nil).getInLanguage(German), "[]")
  }
}
