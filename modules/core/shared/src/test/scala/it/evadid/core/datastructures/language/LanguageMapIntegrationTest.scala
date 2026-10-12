package it.evadid.core.datastructures.language

import it.evadid.core.datastructures.language.AppLanguage.*
import munit.FunSuite

class LanguageMapIntegrationTest extends FunSuite {
  test("combined and translation maps integrate") {
    val first = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(English -> "A", German -> "B"))
    val second = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(English -> "1", German -> "2"))
    val combined = LanguageMap.concatLanguageMaps(first, second)
    assertEquals(combined.getInLanguage(English), "A1")
    assertEquals(combined.getInLanguage(German), "B2")
    assertEquals(upickle.default.read[LanguageMap[HumanLanguage]](upickle.default.write(combined)), combined)
  }
}
