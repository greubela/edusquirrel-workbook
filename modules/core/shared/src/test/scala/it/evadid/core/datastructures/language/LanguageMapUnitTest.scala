package it.evadid.core.datastructures.language

import it.evadid.core.datastructures.language.AppLanguage.*
import munit.FunSuite

class LanguageMapUnitTest extends FunSuite {
  test("map based and universal maps") {
    val map = LanguageMap.mapBasedLanguageMap(Map(English -> "hello", German -> "hallo"))
    assertEquals(map.getInLanguage(German), "hallo")
    val fallback = map.getInLanguage(French)
    assert(Set(
      "[no French (available: Set(English, German))]",
      "[no French (available: Set(German, English))]"
    ).contains(fallback), fallback)

    val universal = LanguageMap.universalMap[HumanLanguage]("same")
    assertEquals(universal.getInLanguage(Danish), "same")
  }

  test("missing translations retain absence and describe the available language") {
    val map = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(English -> "hello"))
    assertEquals(map.tryGetInLanguage(French), None)
    assertEquals(map.getFallback(French), "[no French (available: Set(English))]")
    assertEquals(map.getInLanguage(French), "[no French (available: Set(English))]")
  }

}
