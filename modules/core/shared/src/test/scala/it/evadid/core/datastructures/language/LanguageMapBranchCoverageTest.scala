package it.evadid.core.datastructures.language

import it.evadid.core.datastructures.language.AppLanguage.*
import munit.FunSuite

class LanguageMapBranchCoverageTest extends FunSuite {
  test("empty and mkLanguageMap") {
    val empty = LanguageMap.empty[HumanLanguage]
    assertEquals(empty.getInLanguage(English), "[no English]")

    // unionLanguageMap is intentionally disabled pending a serializable implementation.

    val mk = LanguageMap.mkLanguageMap[HumanLanguage]("(", ",", ")", List(LanguageMap.universalMap("x")))
    assertEquals(mk.getInLanguage(English), "(x,)")
  }
}
