package it.evadid.core.datastructures.language

import scala.reflect.ClassTag

trait LanguageMap[T <: AppLanguage]() {
  def getInLanguage(language: T): String = tryGetInLanguage(language).getOrElse(getFallback(language))

  def tryGetInLanguage(language: T): Option[String]

  def getFallback(language: T): String = "[no " + language + "]"

  def availableLanguages: Set[T]

  def getWithLanguagePreference(possibleLanguages: List[T]): String = {
    val preferredLanguage = possibleLanguages.find(availableLanguages.contains)
    getInLanguage(preferredLanguage.getOrElse(availableLanguages.head))
  }

  def withMappedContent(func: String => String): LanguageMap[T] = new LanguageMap[T]() {
    override def tryGetInLanguage(language: T): Option[String] = try {
      Some(func(LanguageMap.this.getInLanguage(language)))
    } catch {
      case e: Throwable => None
    }

    def availableLanguages: Set[T] = LanguageMap.this.availableLanguages
  }

  def withFallback(other: LanguageMap[T]): LanguageMap[T] = LanguageMap.baseMapWithFallback(this, other)

}

object LanguageMap {

  def empty[T <: AppLanguage]: LanguageMap[T] = mapBasedLanguageMap(Map.empty)

  def baseMapWithFallback[T <: AppLanguage](base: LanguageMap[T], fallback: LanguageMap[T]): LanguageMap[T] = new LanguageMap[T] {

    override def tryGetInLanguage(language: T): Option[String] = base.tryGetInLanguage(language).match {
      case Some(resultStr) => Some(resultStr)
      case None => fallback.tryGetInLanguage(language)
    }

    def availableLanguages: Set[T] = base.availableLanguages ++ fallback.availableLanguages
  }

  def concatLanguageMaps[T <: AppLanguage](firstMap: LanguageMap[T], secondMap: LanguageMap[T]): LanguageMap[T] = {
    val types = firstMap.availableLanguages.intersect(secondMap.availableLanguages)
    val pMap = types.map(curLang => (curLang -> (firstMap.getInLanguage(curLang) + "" + secondMap.getInLanguage(curLang)))).toMap
    mapBasedLanguageMap(pMap)
  }

  def unionLanguageMap[T <: AppLanguage](maps: List[LanguageMap[T]]) = new LanguageMap[T]() {
    override def tryGetInLanguage(language: T): Option[String] =
      maps
        .find(_.availableLanguages.contains(language))
        .map(curMap => curMap.getInLanguage(language))

    def availableLanguages: Set[T] = maps.flatMap(_.availableLanguages).toSet
  }


  def universalMap[T <: AppLanguage](string: String)(using classTag: ClassTag[T]): LanguageMap[T] = new LanguageMap[T]() {
    def tryGetInLanguage(language: T): Option[String] = Some(string)

    def availableLanguages: Set[T] = {
      AppLanguage.allLanguages.collect { case language: T => language }.toSet
    }

    override def toString: String = "UniversalLanguageMap('" + string + "')"
  }

  def mapBasedLanguageMapWithFallback[T <: AppLanguage](pMap: Map[T, String], fallback: String): LanguageMap[T] = new LanguageMap[T]() {
    def tryGetInLanguage(language: T): Option[String] = pMap.get(language)

    override def getFallback(language: T): String = fallback

    def availableLanguages: Set[T] = {
      AppLanguage.allLanguages.collect { case language: T => language }.toSet
    }

    override def toString: String = "MapBasedLanguageMapWithFallback(fallback: " + fallback + ", map: " + pMap + ")"
  }

  def mapBasedLanguageMap[T <: AppLanguage](pMap: Map[T, String]): LanguageMap[T] = new LanguageMap[T]() {
    def tryGetInLanguage(language: T): Option[String] = pMap.get(language)

    def map(func: String => String): LanguageMap[T] = mapBasedLanguageMap(pMap.map((key, value) => (key, func(value))))

    override val availableLanguages: Set[T] = pMap.keys.toSet

    override def toString: String = "MapBasedLanguageMap(" + pMap + ")"
  }

  def mkLanguageMap[T <: AppLanguage](start: String, sep: String, end: String, maps: List[LanguageMap[T]])(using classTag: ClassTag[T]): LanguageMap[T] = {
    var res: LanguageMap[T] = universalMap(start)
    for (curMap <- maps) {
      res = concatLanguageMaps(res, curMap)
      res = concatLanguageMaps(res, universalMap(sep))
    }
    concatLanguageMaps(res, universalMap(end))
  }

  def emptyMap[T <: AppLanguage](): LanguageMap[T] = new LanguageMap[T]() {

    override def tryGetInLanguage(language: T): Option[String] = None

    override def getInLanguage(language: T): String = "[error: empty language map!]"

    override def availableLanguages: Set[T] = Set.empty
  }

}
