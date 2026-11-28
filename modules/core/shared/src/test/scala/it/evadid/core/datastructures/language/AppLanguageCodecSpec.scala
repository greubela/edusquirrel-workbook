package it.evadid.core.datastructures.language

import it.evadid.core.datastructures.language.AppLanguage.*
import munit.FunSuite
import upickle.default.*

class AppLanguageCodecSpec extends FunSuite {
  test("languages are saved by name and read back as their kind") {
    assertEquals(write[AppLanguage](Cpp), "\"C++\"")
    assertEquals(read[ProgrammingLanguage](write[ProgrammingLanguage](Cpp)), Cpp)
    assertEquals(read[HumanLanguage](write[HumanLanguage](German)), German)
    assertEquals(read[SpecialLanguage](write[AppLanguage](UniversalLanguage)), UniversalLanguage)
  }

  test("a saved language of another kind is rejected") {
    val error = intercept[Exception](read[ProgrammingLanguage](write[AppLanguage](German)))
    assert(Iterator.iterate[Throwable](error)(_.getCause).takeWhile(_ != null).exists(_.getMessage.contains("German is not a programming language")))
  }
}
