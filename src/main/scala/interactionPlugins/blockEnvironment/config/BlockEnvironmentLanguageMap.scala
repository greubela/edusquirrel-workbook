package interactionPlugins.blockEnvironment.config

import contentmanagement.model.language.*

object BlockEnvironmentLanguageMap {


  val languageMapOpenEditor: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Open Editor to Program",
    AppLanguage.German -> "Editor zum Programmieren öffnen"
  ))

  val languageMapYourProgram: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Your Program",
    AppLanguage.German -> "Dein Programm"
  ))

  val languageMapProgramOutcome: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Expected Turtle Drawing",
    AppLanguage.German -> "Erwartete Turtle Zeichnung"
  ))

}
