package contentmanagement.model.language


sealed trait AppLanguage() {
  val name: String
}

sealed class HumanLanguage(val name: String, val nameAbbr: String) extends AppLanguage

sealed class ProgrammingLanguage(val name: String, val fileEnding: String) extends AppLanguage

object AppLanguage {

  def default(): HumanLanguage = English

  def allLanguages: Set[AppLanguage] = humanLanguages ++ programmingLanguages

  val humanLanguages: Set[HumanLanguage] = Set(English, German)
  val programmingLanguages: Set[ProgrammingLanguage] = Set(Python, Java, JavaScript, Rust, Lisp, BlockDisplay)
  
  case object English extends HumanLanguage("English", "EN")

  case object German extends HumanLanguage("German", "DE")

  case object Python extends ProgrammingLanguage("Python", "py")
  case object Java extends ProgrammingLanguage("Java", "java")
  case object JavaScript extends ProgrammingLanguage("JavaScript", "js")
  case object Rust extends ProgrammingLanguage("Rust", "rs")
  case object Lisp extends ProgrammingLanguage("LISP", "lisp")
  case object BlockDisplay extends ProgrammingLanguage("Block Display", "bd")
}
