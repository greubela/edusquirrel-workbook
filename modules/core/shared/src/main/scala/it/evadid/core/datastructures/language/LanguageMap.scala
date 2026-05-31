package it.evadid.core.datastructures.language

import scala.reflect.ClassTag

trait LanguageMap[T <: AppLanguage]() {
  def getInLanguage(language: T): String

  protected def availableLanguages: Set[T]
  
  def getWithLanguagePreference(possibleLanguages: List[T]): String = {
    val preferredLanguage = possibleLanguages.find(availableLanguages.contains)
    getInLanguage(preferredLanguage.getOrElse(availableLanguages.head))
  }

}

object LanguageMap {
   
  def empty[T <: AppLanguage]: LanguageMap[T] = mapBasedLanguageMap(Map.empty)

  def combinedMap[T <: AppLanguage](maps: List[LanguageMap[T]]) = new LanguageMap[T]() {
    override def getInLanguage(language: T): String =
      maps
        .find(_.availableLanguages.contains(language))
        .map(curMap => curMap.getInLanguage(language))
        .getOrElse("[no " + language + "]")

    protected def availableLanguages: Set[T] = maps.flatMap(_.availableLanguages).toSet
  }

  def universalMap[T <: AppLanguage](string: String)(using classTag: ClassTag[T]): LanguageMap[T] = new LanguageMap[T]() {
    def getInLanguage(language: T): String = string

    def map(func: String => String): LanguageMap[T] = LanguageMap.universalMap(func(string))

    protected def availableLanguages: Set[T] = {
      AppLanguage.allLanguages.collect { case language: T => language }.toSet
    }

    override def toString: String = "UniversalLanguageMap('" + string + "')"
  }

  def mapBasedLanguageMap[T <: AppLanguage](pMap: Map[T, String]): LanguageMap[T] = new LanguageMap[T]() {
    def getInLanguage(language: T): String = pMap.getOrElse(language, "[no " + language.name + "]")

    def map(func: String => String): LanguageMap[T] = mapBasedLanguageMap(pMap.map((key, value) => (key, func(value))))

    protected def availableLanguages: Set[T] = pMap.keys.toSet

    override def toString: String = "MapBasedLanguageMap(" + pMap + ")"
  }

  def mkLanguageMap[T <: AppLanguage](start: String, sep: String, end: String, maps: List[LanguageMap[T]])(using classTag: ClassTag[T]): LanguageMap[T] = {
    var res: LanguageMap[T] = universalMap(start)
    for (curMap <- maps) {
      res = combinedMap(res, curMap)
      res = combinedMap(res, universalMap(sep))
    }
    combinedMap(res, universalMap(end))
  }

  def combinedMap[T <: AppLanguage](firstMap: LanguageMap[T], secondMap: LanguageMap[T]): LanguageMap[T] = {
    val types = firstMap.availableLanguages.intersect(secondMap.availableLanguages)
    val pMap = types.map(curLang => (curLang -> (firstMap.getInLanguage(curLang) + "" + secondMap.getInLanguage(curLang)))).toMap
    mapBasedLanguageMap(pMap)
  }


}
