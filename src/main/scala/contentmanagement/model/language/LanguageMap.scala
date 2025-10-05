package contentmanagement.model.language

trait LanguageMap[T <: AppLanguage]() {
  def getInLanguage(language: T): String
}

object LanguageMap {
  def empty[T <: AppLanguage]: LanguageMap[T] = mapBasedLanguageMap(Map.empty)

  def universalMap[T <: AppLanguage](string: String): LanguageMap[T] = new LanguageMap[T]() {
    def getInLanguage(language: T): String = string
  }

  def mapBasedLanguageMap[T <: AppLanguage](map: Map[T, String]): LanguageMap[T] = new LanguageMap[T]() {
    def getInLanguage(language: T): String = map.getOrElse(language, "[no " + language.name + "]")
  }

}