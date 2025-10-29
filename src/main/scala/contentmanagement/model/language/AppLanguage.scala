package contentmanagement.model.language


sealed trait AppLanguage() {
  val name: String
}

sealed class HumanLanguage(val name: String, val nameAbbr: String) extends AppLanguage

sealed class ProgrammingLanguage(val name: String, val fileEnding: String) extends AppLanguage

object AppLanguage {

  def default(): HumanLanguage = English

  //def allLanguages: List[AppLanguage] = humanLanguages ++ programmingLanguages

  /*val humanLanguages: List[HumanLanguage] = List(English, German)
  val programmingLanguages: List[ProgramingLanguage] = List(Python, Java, BeLanguage)*/


  case object English extends HumanLanguage("English", "EN")

  case object German extends HumanLanguage("German", "DE")

  case object Python extends ProgrammingLanguage("Python", "py")
  case object Java extends ProgrammingLanguage("Java", "java")
  case object JavaScript extends ProgrammingLanguage("JavaScript", "js")
  case object Rust extends ProgrammingLanguage("Rust", "rs")
  case object Lisp extends ProgrammingLanguage("LISP", "lisp")

}
