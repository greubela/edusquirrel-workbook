package it.evadid.homepage.control

import munit.FunSuite
import it.evadid.core.datastructures.language.{LanguageMapContentId, AppLanguage}
import it.evadid.core.datastructures.language.AppLanguage.{English, German, UniversalLanguage, HumanLanguage, SpecialLanguage}
import it.evadid.core.datastructures.language.control.LanguageMapStorage
import it.evadid.core.datastructures.language.serialization.abstractions.{LanguageMapEntry, ParsedTriples}

class WorkbookContentStorageSpec extends FunSuite {

  test("language map triples use universal entries as fallback for missing explicit languages") {
    val id = LanguageMapContentId("test/fallback")
    val triples = ParsedTriples(
      Set(LanguageMapEntry[HumanLanguage](id, English, "English text")),
      Set(LanguageMapEntry[SpecialLanguage](id, UniversalLanguage, "Universal text")))
    val map = LanguageMapStorage(triples, Set.empty).languageMaps(id)
    assertEquals(map.getInLanguage(English), "English text")
    assertEquals(map.getInLanguage(German), "Universal text")
  }

  test("language map triples can be backed by only a universal entry") {
    val id = LanguageMapContentId("test/universal")
    val triples = ParsedTriples(Set.empty,
      Set(LanguageMapEntry[SpecialLanguage](id, UniversalLanguage, "All languages")))
    val map = LanguageMapStorage(triples, Set.empty).languageMaps(id)
    AppLanguage.humanLanguages.foreach(language => assertEquals(map.getInLanguage(language), "All languages"))
  }
}
