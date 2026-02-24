package contentmanagement.model.language

object TranslationMaps {


  val languageMapImageLoading: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.German -> "[Bild wird geladen]",
    AppLanguage.English -> "[image loading]"
  ))

}
