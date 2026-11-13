package it.evadid.core.datastructures.language

import upickle.default.*

import scala.reflect.ClassTag

sealed trait LanguageMap[T <: AppLanguage]() derives ReadWriter {
  def getInLanguage(language: T): String = tryGetInLanguage(language).getOrElse(getFallback(language))

  def tryGetInLanguage(language: T): Option[String]

  def getFallback(language: T): String = "[no " + language + "]"

  def availableLanguages: Set[T]

  def getWithLanguagePreference(possibleLanguages: List[T]): String = {
    val preferredLanguage = possibleLanguages.find(availableLanguages.contains)
    getInLanguage(preferredLanguage.getOrElse(availableLanguages.head))
  }

  def withFallback(other: LanguageMap[T]): LanguageMap[T] = LanguageMap.baseMapWithFallback(this, other)

}

object LanguageMap {

  def baseMapWithFallback[T <: AppLanguage](base: LanguageMap[T], fallback: LanguageMap[T]): LanguageMap[T] = merge(base, fallback)

  def merge[T <: AppLanguage](mapA: LanguageMap[T], mapB: LanguageMap[T]): LanguageMap[T] = {
    (mapA, mapB).match {
      case (LanguageMapMapBased(pMap1), LanguageMapMapBased(pMap2)) => LanguageMapMapBased(pMap1 ++ pMap2)
      case (LanguageMapUniversal, _) => mapA
      case (_, LanguageMapUniversal) => mapB
      case (_, _) => LanguageMapBaseWithFallback(mapA, mapB)
    }
  }


  def empty[T <: AppLanguage]: LanguageMap[T] = mapBasedLanguageMap(Map.empty)

  private case class LanguageMapMapBased[T <: AppLanguage](pMap: Map[T, String]) extends LanguageMap[T] derives ReadWriter {

    def tryGetInLanguage(language: T): Option[String] = pMap.get(language)

    def map(func: String => String): LanguageMap[T] = mapBasedLanguageMap(pMap.map((key, value) => (key, func(value))))

    val availableLanguages: Set[T] = pMap.keys.toSet

    override val toString: String = "LanguageMapMapBased(" + pMap + ")"


  }

  private case class LanguageMapUniversal[T <: AppLanguage](returnString: String) extends LanguageMap[T]() derives ReadWriter {
    def tryGetInLanguage(language: T): Option[String] = Some(returnString)

    def availableLanguages: Set[T] = {
      AppLanguage.allLanguages.collect { case language: T => language }.toSet
    }

    override def toString: String = "UniversalLanguageMap('" + returnString + "')"
  }

  private case class LanguageMapBaseWithFallback[T <: AppLanguage](base: LanguageMap[T], fallback: LanguageMap[T]) extends LanguageMap[T] derives ReadWriter {
    override def tryGetInLanguage(language: T): Option[String] =
      base.tryGetInLanguage(language).orElse(fallback.tryGetInLanguage(language))

    def availableLanguages: Set[T] = base.availableLanguages ++ fallback.availableLanguages
  }



  def concatLanguageMaps[T <: AppLanguage](firstMap: LanguageMap[T], secondMap: LanguageMap[T]): LanguageMap[T] = {
    val types = firstMap.availableLanguages.intersect(secondMap.availableLanguages)
    val pMap = types.map(curLang => (curLang -> (firstMap.getInLanguage(curLang) + "" + secondMap.getInLanguage(curLang)))).toMap
    mapBasedLanguageMap(pMap)
  }


  def universalMap[T <: AppLanguage](string: String): LanguageMap[T] = LanguageMapUniversal(string)

  def mapBasedLanguageMapWithFallback[T <: AppLanguage](pMap: Map[T, String], fallback: String): LanguageMap[T] = {
    val mapLanguage = mapBasedLanguageMap[T](pMap)
    val universalFallback = universalMap[T](fallback)
    LanguageMapBaseWithFallback(mapLanguage, universalFallback)
  }

  def mapBasedLanguageMap[T <: AppLanguage](pMap: Map[T, String]): LanguageMap[T] = LanguageMapMapBased(pMap)

  def mkLanguageMap[T <: AppLanguage](start: String, sep: String, end: String, maps: List[LanguageMap[T]])(using classTag: ClassTag[T]): LanguageMap[T] = {
    val separator = universalMap[T](sep)
    def appendWithSeparator(current: LanguageMap[T], next: LanguageMap[T]): LanguageMap[T] =
      concatLanguageMaps(concatLanguageMaps(current, next), separator)
    val content = maps.foldLeft(universalMap[T](start))(appendWithSeparator)
    concatLanguageMaps(content, universalMap[T](end))
  }
}
