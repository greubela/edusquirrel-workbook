package it.evadid.core.datastructures.language

import it.evadid.core.datastructures.language.AppLanguage.*
import munit.FunSuite

class LanguageMapIntegrationTest extends FunSuite {
  test("combined and translation maps integrate") {
    val first = LanguageMap.mapBasedLanguageMap(Map(English -> "A", German -> "B"))
    val second = LanguageMap.mapBasedLanguageMap(Map(English -> "1", German -> "2"))
    val combined = LanguageMap.concatLanguageMaps(first, second)
    assertEquals(combined.getInLanguage(English), "A1")
    assertEquals(TranslationMaps.languageMapImageLoading.getInLanguage(English), "[image loading]")
  }
}
