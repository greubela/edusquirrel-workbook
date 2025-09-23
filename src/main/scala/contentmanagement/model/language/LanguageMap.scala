package contentmanagement.model.language

case class LanguageMap(map: Map[AppLanguage, String]) {

  def getInLanguage(language: AppLanguage): String = map.getOrElse(language, "[no" + language.nameAbbr + "]")

}
