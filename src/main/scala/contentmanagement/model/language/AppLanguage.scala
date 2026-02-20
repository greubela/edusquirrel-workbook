package contentmanagement.model.language


sealed trait AppLanguage() {
  val name: String
}

sealed class HumanLanguage(val name: String, val nameAbbr: String) extends AppLanguage

sealed class ProgrammingLanguage(val name: String, val fileEnding: String) extends AppLanguage

object AppLanguage {

  def default(): HumanLanguage = English

  def allLanguages: Set[AppLanguage] = humanLanguages ++ programmingLanguages

  val humanLanguages: Set[HumanLanguage] = Set(English, German, French)
  val programmingLanguages: Set[ProgrammingLanguage] = Set(Python, Java, JavaScript, Rust, Lisp, Cpp, BlockDisplay)
  
  case object English extends HumanLanguage("English", "EN")
  case object German extends HumanLanguage("German", "DE")
  case object French extends HumanLanguage("French", "FR")
  case object Ukrainian extends HumanLanguage("Ukrainian", "UK")
  case object Russian extends HumanLanguage("Russian", "UK")
  case object Turkish extends HumanLanguage("Turkish", "TR")
  case object Danish extends HumanLanguage("Danish", "DK")

  case object Python extends ProgrammingLanguage("Python", "py")
  case object Java extends ProgrammingLanguage("Java", "java")
  case object JavaScript extends ProgrammingLanguage("JavaScript", "js")
  case object Rust extends ProgrammingLanguage("Rust", "rs")
  case object Lisp extends ProgrammingLanguage("LISP", "lisp")
  case object Cpp extends ProgrammingLanguage("C++", "cpp")
  case object BlockDisplay extends ProgrammingLanguage("Block Display", "bd")
}
