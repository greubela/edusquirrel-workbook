package contentmanagement.model.language


class AppLanguage(val name: String, val nameAbbr: String) {

}

object AppLanguage {

  val allLanguages: List[AppLanguage] = List(English, German)

  // still in printing

  case object English extends AppLanguage("English", "EN")

  case object German extends AppLanguage("German", "DE")


}
