package it.evadid.core.datastructures.language

import upickle.default.*

sealed trait AppLanguage derives ReadWriter {
  val name: String
}

object AppLanguage {

  sealed trait HumanLanguage(val name: String, val nameAbbr: String) extends AppLanguage derives ReadWriter

  sealed trait ProgrammingLanguage(val name: String, val fileEnding: String) extends AppLanguage derives ReadWriter

  sealed trait SpecialLanguage(val name: String) extends AppLanguage derives ReadWriter

  case object UniversalLanguage extends SpecialLanguage("universal")

  def default(): HumanLanguage = English

  def allLanguages: Set[AppLanguage] = humanLanguages ++ programmingLanguages ++ List(UniversalLanguage)

  val humanLanguages: Set[HumanLanguage] = Set(English, German, French, Ukrainian, Russian, Turkish, Danish, Spanish)
  val programmingLanguages: Set[ProgrammingLanguage] = Set(Python, Java, JavaScript, Rust, Lisp, Cpp, C, BlockDisplay)

  case object English extends HumanLanguage("English", "EN")

  case object German extends HumanLanguage("German", "DE")

  case object French extends HumanLanguage("French", "FR")

  case object Ukrainian extends HumanLanguage("Ukrainian", "UK")

  case object Russian extends HumanLanguage("Russian", "UK")

  case object Turkish extends HumanLanguage("Turkish", "TR")

  case object Danish extends HumanLanguage("Danish", "DK")

  case object Spanish extends HumanLanguage("Spanish", "ES")



  case object Python extends ProgrammingLanguage("Python", "py")

  case object Java extends ProgrammingLanguage("Java", "java")

  case object JavaScript extends ProgrammingLanguage("JavaScript", "js")

  case object Rust extends ProgrammingLanguage("Rust", "rs")

  case object Lisp extends ProgrammingLanguage("LISP", "lisp")

  case object Cpp extends ProgrammingLanguage("C++", "cpp")

  case object C extends ProgrammingLanguage("C", "c")

  case object BlockDisplay extends ProgrammingLanguage("Block Display", "bd")

  val turtleStitchLangMap: Map[HumanLanguage, String] = Map(
    English -> "en",
    German -> "de",
    French -> "fr",
    Ukrainian -> "ua",
    Russian -> "ru",
    Turkish -> "tr",
    Danish -> "dk",
    Spanish -> "es"
  )
}
