package it.evadid.core.datastructures.language

import it.evadid.core.datastructures.language.AppLanguage.*
import munit.FunSuite

class LanguageMapBranchCoverageTest extends FunSuite {
  test("empty, list-based combine and mkLanguageMap") {
    val empty = LanguageMap.empty[HumanLanguage]
    assertEquals(empty.getInLanguage(English), "[no English]")

    val onlyGerman: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(German -> "G"))
    val onlyEnglish: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(English -> "E"))
    val combined = LanguageMap.combinedMap(List(onlyGerman, onlyEnglish))
    assertEquals(combined.getInLanguage(English), "E")
    assertEquals(combined.getInLanguage(French), "[no French]")

    val mk = LanguageMap.mkLanguageMap[HumanLanguage]("(", ",", ")", List(LanguageMap.universalMap("x")))
    assertEquals(mk.getInLanguage(English), "(x,)")
  }
}
