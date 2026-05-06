package it.evadid.core.datastructures.language

import it.evadid.core.datastructures.language.AppLanguage.*
import munit.FunSuite

class LanguageMapUnitTest extends FunSuite {
  test("map based and universal maps") {
    val map = LanguageMap.mapBasedLanguageMap(Map(English -> "hello", German -> "hallo"))
    assertEquals(map.getInLanguage(German), "hallo")
    assertEquals(map.getInLanguage(French), "[no French]")

    val universal = LanguageMap.universalMap[HumanLanguage]("same")
    assertEquals(universal.getInLanguage(Danish), "same")
  }
}
