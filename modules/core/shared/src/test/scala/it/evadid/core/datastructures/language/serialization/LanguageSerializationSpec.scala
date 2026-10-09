package it.evadid.core.datastructures.language.serialization

import it.evadid.core.datastructures.file.{CopyrightInfo, FileDescription, LoadedFile}
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.serialization.LanguageMapInputSource.*
import it.evadid.core.datastructures.language.serialization.abstractions.*
import it.evadid.core.datastructures.language.serialization.abstractions.LanguageMapEntry.LanguageTripel
import it.evadid.distribution.command.SerializedException
import it.evadid.util.logging.BasicLogger
import munit.FunSuite
import scala.concurrent.{ExecutionContext, Future}
import upickle.default.*

class LanguageSerializationSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val id = LanguageMapContentId("lesson", "hello")
  private val regular = LanguageMapEntry[HumanLanguage](id, English, "Hello")
  private val universal = LanguageMapEntry[SpecialLanguage](id, UniversalLanguage, "fallback")
  private val empty = ParsedTriples(Set.empty, Set.empty)

  private def file(name: String, content: String, failure: Option[Throwable] = None, synchronous: Boolean = false): FileDescription = new FileDescription {
    def copyrightInfo = CopyrightInfo.unknownCopyrightInfo
    def asUrlString = name
    def loadData(): Future[LoadedFile] = failure match {
      case Some(error) if synchronous => throw error
      case Some(error) => Future.failed(error)
      case None => Future.successful(LoadedFile(this, content.getBytes("UTF-8")))
    }
    def getChildrenFile(name: String, copyright: CopyrightInfo): Option[FileDescription] = Some(file(asUrlString + "/" + name, content))
  }
  private def info[T <: AppLanguage](description: FileDescription, language: T) =
    LanguageMapFileBasedSourceInfo(description, "lesson", language, summon[ExecutionContext])
  private def source(result: => Future[ParsedTriples]): LanguageMapInputSource = new LanguageMapInputSource {
    def loadAllTriples(logger: it.evadid.util.logging.Logger) = result
  }

  test("JSON files decode UTF-8 translations with content identifiers") {
    val loader = LanguageMapSourceFileBased.forEvaFile(info(file("map.JSON", "{\"hello\":\"Grüße 🌳\"}"), German)).get
    loader.loadAllTriples(BasicLogger()).map { result =>
      assertEquals(result.regularTriples, Set(LanguageMapEntry[HumanLanguage](id, German, "Grüße 🌳")))
      assertEquals(result.universalTriples, Set.empty[LanguageMapEntry[SpecialLanguage]])
    }
  }
  test("universal files produce special rather than human language triples") {
    LanguageMapSourceFileBased.forEvaFile(info(file("map.json", "{\"hello\":\"fallback\"}"), UniversalLanguage)).get
      .loadAllTriples(BasicLogger()).map(result => assertEquals(result, ParsedTriples(Set.empty, Set(universal))))
  }
  test("empty files do not invoke their parser") {
    val loader = LanguageMapSourceFileBased(info(file("map.json", " \n"), English), (_, _) => fail("empty file was parsed"))
    loader.loadAllTriples(BasicLogger()).map(result => assertEquals(result, empty))
  }
  test("CSV retains quoted separators, multiline values and skips incomplete rows") {
    val logger = BasicLogger()
    val loader = LanguageMapSourceFileBased.forEvaFile(info(file("map.CSV", "hello;\"a;b\"\nother;\"line1\nline2\"\nbroken\n"), English)).get
    loader.loadAllTriples(logger).map { result =>
      assertEquals(result.regularTriples.map(e => e.contentId.entryKey -> e.value), Set("hello" -> "a;b", "other" -> "line1\nline2"))
      assert(logger.getOut().contains("ignored 1 entries"))
    }
  }
  test("malformed JSON returns no triples and logs the parse failure") {
    val logger = BasicLogger()
    LanguageMapSourceFileBased.forEvaFile(info(file("map.json", "{bad"), English)).get.loadAllTriples(logger).map { result =>
      assertEquals(result, empty)
      assert(logger.getOut().contains("Exception"))
    }
  }
  test("failed file loads recover ordinary and serialized exceptions as IO errors") {
    Future.traverse(List(new IllegalArgumentException("missing"), SerializedException("missing"))) { error =>
      val logger = BasicLogger()
      LanguageMapSourceFileBased.forEvaFile(info(file("map.json", "", Some(error)), English)).get.loadAllTriples(logger).map { result =>
        assertEquals(result, empty)
        assert(logger.getOut().contains("IO Error: missing"))
      }
    }
  }
  test("synchronous file loader exceptions are recovered through the returned future") {
    LanguageMapSourceFileBased.forEvaFile(info(file("map.json", "", Some(SerializedException("missing")), true), English)).get
      .loadAllTriples(BasicLogger()).map(result => assertEquals(result, empty))
  }
  test("Snap translation files remap keys and recover malformed payloads") {
    val good = LanguageMapSourceFileBased.forSnapFile(info(file("map.js", "dictionary = {\n\"Hello\": \"Hi\"\n}"), English), _.toLowerCase).get
    val bad = LanguageMapSourceFileBased.forSnapFile(info(file("bad.js", "dictionary = {\nbad\n}"), English), identity).get
    good.loadAllTriples(BasicLogger()).flatMap { result =>
      assertEquals(result.regularTriples, Set(LanguageMapEntry[HumanLanguage](id, English, "Hi")))
      bad.loadAllTriples(BasicLogger()).map(result => assertEquals(result, empty))
    }
  }
  test("unsupported file types are rejected and directory factories create nine language sources") {
    assertEquals(LanguageMapSourceFileBased.forEvaFile(info(file("map.txt", ""), English)), None)
    val collection = forEvaLanguageMapFiles(Set(EvaDirectorySource("lesson", file("translations", "{}"))))
    assertEquals(collection.inputSources.size, 9)
    assertEquals(collection.inputSources.collect { case f: LanguageMapSourceFileBased[?] => f.associatedLanguage },
      Set[AppLanguage](English, German, French, Ukrainian, Russian, Turkish, Danish, Spanish, UniversalLanguage))
  }
  test("empty source collections avoid loading sources") {
    val collection = LanguageMapCollectionSource(Set.empty, summon[ExecutionContext])
    collection.loadTriples(BasicLogger(), source(fail("source loaded"))).flatMap { result =>
      assertEquals(result, empty)
      collection.loadAllTriples(BasicLogger()).map(result => assertEquals(result, empty))
    }
  }
  test("collections combine successful sources and recover asynchronous source errors") {
    val collection = LanguageMapCollectionSource(Set(
      source(Future.successful(ParsedTriples(Set(regular), Set.empty))),
      source(Future.successful(ParsedTriples(Set(regular), Set(universal)))),
      source(Future.failed(SerializedException("unavailable")))
    ), summon[ExecutionContext])
    collection.loadAllTriples(BasicLogger()).map(result => assertEquals(result, ParsedTriples(Set(regular), Set(universal))))
  }
  test("collections isolate synchronous source errors without losing successful sources") {
    val collection = LanguageMapCollectionSource(Set(source(throw new IllegalStateException("offline")), source(Future.successful(ParsedTriples(Set(regular), Set.empty)))), summon[ExecutionContext])
    collection.loadAllTriples(BasicLogger()).map(result => assertEquals(result, ParsedTriples(Set(regular), Set.empty)))
  }
  test("translation triple serializer round-trips quotes, backslashes, newlines and Unicode") {
    for (value <- List("Hello", "\"quotes\"", "path\\file\nnext", "Grüße 🌳")) {
      val triple = LanguageTripel(0, 1, value)
      assertEquals(LanguageMapEntry.tripSerializer.deserialize(LanguageMapEntry.tripSerializer.serialize(triple)), triple)
    }
  }
  test("translation triple serialization rejects wrong constructors and invalid payloads") {
    for (input <- List("Other(0)(1)(\"x\")", "Trip(0)(1)", "Trip(0)(1)(3)", "Trip(0)(1)(\"x\")(4)")) {
      intercept[SerializedException](LanguageMapEntry.tripSerializer.deserialize(input))
    }
  }
  test("translation triples resolve references and diagnose invalid indices") {
    assertEquals(regular.serializeWith(List(id), List[HumanLanguage](English)).resolveRegular(List(id), List(English)), regular)
    assertEquals(universal.serializeWith(List(id), List[SpecialLanguage](UniversalLanguage)).resolveSpecial(List(id), List(UniversalLanguage)), universal)
    intercept[SerializedException](LanguageTripel(-1, 0, "x").resolveRegular(List(id), List(English)))
    intercept[SerializedException](LanguageTripel(0, 9, "x").resolveSpecial(List(id), List(UniversalLanguage)))
  }
  test("parsed triples preserve text through their existing default codec") {
    val triples = ParsedTriples(Set(regular.copy(value = "\"Hi\"\n\\")), Set(universal))
    assertEquals(read[ParsedTriples](write(triples)), triples)
    assertEquals(triples.union(triples), triples)
    assertEquals(triples.size, 2)
    assert(triples.toString.contains("2 triples"))
  }
  test("translation maps combine regular overrides and universal fallbacks") {
    val onlyRegular = LanguageMapContentId("lesson", "regular")
    val onlyUniversal = LanguageMapContentId("lesson", "universal")
    val triples = ParsedTriples(Set(regular, regular.copy(contentId = onlyRegular)), Set(universal, universal.copy(contentId = onlyUniversal)))
    val maps = triples.createMapsFromTriples().map(m => m.contentId -> m.languageMap).toMap
    assertEquals(maps(id).getInLanguage(English), "Hello")
    assertEquals(maps(id).getInLanguage(German), "fallback")
    assertEquals(maps(onlyUniversal).getInLanguage(German), "fallback")
    assertEquals(maps(onlyRegular).tryGetInLanguage(German), None)
    assertEquals(empty.createMapsFromTriples(), Set.empty[LanguageMapWithId])
  }
  test("serialized triple tables skip unresolved references while retaining valid entries") {
    val table = ParsedTriples.ParsedTriplesSerialized(List(id), List(English), List(UniversalLanguage),
      List(LanguageTripel(0, 0, "Hello"), LanguageTripel(5, 0, "bad")), List(LanguageTripel(0, 0, "fallback"), LanguageTripel(0, 5, "bad")))
    assertEquals(table.toTypedMainType, ParsedTriples(Set(regular), Set(universal)))
  }
  test("triples and language entries expose default codecs without changing the triple wire format") {
    val triple = LanguageTripel(0, 0, "\"Hello\"\n")
    assertEquals(write(triple), ujson.write(LanguageMapEntry.tripSerializer.serialize(triple)))
    assertEquals(read[LanguageTripel](write(triple)), triple)
    assertEquals(read[LanguageMapEntry[HumanLanguage]](write(regular)), regular)
    assertEquals(read[LanguageMapEntry[SpecialLanguage]](write(universal)), universal)
    val map = LanguageMapWithId(id, LanguageMap.mapBasedLanguageMap(Map[HumanLanguage, String](English -> "Hello")))
    assertEquals(read[LanguageMapWithId](write(map)).languageMap.getInLanguage(English), "Hello")
  }
  test("serialized triple tables expose their existing codec as a default ReadWriter") {
    val table = ParsedTriples(Set(regular), Set(universal)).toSerializableSubType
    assertEquals(read[ParsedTriples.ParsedTriplesSerialized](write(table)), table)
  }
  test("refresh replaces cached text for the same key without losing other languages or keys") {
    val old = ParsedTriples(Set(regular, regular.copy(language = German, value = "Hallo")), Set(universal))
    val fresh = ParsedTriples(Set(regular.copy(value = "Updated")), Set(universal.copy(value = "new fallback")))
    val replaced = old.withOverrides(fresh)
    assertEquals(replaced.regularTriples.size, 2)
    assertEquals(replaced.universalTriples.size, 1)
    val store = it.evadid.core.datastructures.language.control.LanguageMapStorage(old, Set.empty)
      .withLoadedTriples(BasicLogger(), Set.empty, fresh)
    assertEquals(store.languageMaps(id).getInLanguage(English), "Updated")
    assertEquals(store.languageMaps(id).getInLanguage(German), "Hallo")
    assertEquals(replaced.withOverrides(empty), replaced)
  }

  test("a large indexed cache retains the existing wire format and all text") {
    val entries = (0 until 3000).map(index => LanguageMapEntry[HumanLanguage](
      LanguageMapContentId("large", index.toString), English, s"Text $index 🌳\n")).toSet
    val triples = ParsedTriples(entries, Set(universal))
    val serialized = ParsedTriples.serializer.serialize(triples)
    assert(serialized.contains("Trip("))
    assertEquals(ParsedTriples.serializer.deserialize(serialized), triples)
  }

}
