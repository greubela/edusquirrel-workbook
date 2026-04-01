package interactionPlugins.blockEnvironment.config

import datastructures.core.language.{AppLanguage, HumanLanguage, LanguageMap}

object BlockEnvironmentLanguageMap {


  val languageMapOpenEditor: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Open Editor to Program",
    AppLanguage.German -> "Editor zum Programmieren öffnen",
    AppLanguage.French -> "Ouvrir l'éditeur pour programmer",
    AppLanguage.Ukrainian -> "Відкрити редактор для програмування",
    AppLanguage.Russian -> "Открыть редактор для программирования",
    AppLanguage.Turkish -> "Programlamak için düzenleyiciyi aç",
    AppLanguage.Danish -> "Åbn editoren for at programmere"
  ))

  val languageMapYourProgram: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Your Program",
    AppLanguage.German -> "Dein Programm",
    AppLanguage.French -> "Ton programme",
    AppLanguage.Ukrainian -> "Твоя програма",
    AppLanguage.Russian -> "Твоя программа",
    AppLanguage.Turkish -> "Programın",
    AppLanguage.Danish -> "Dit program"
  ))

  val languageMapProgramOutcome: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Expected Turtle Drawing",
    AppLanguage.German -> "Erwartete Turtle Zeichnung",
    AppLanguage.French -> "Dessin Turtle attendu",
    AppLanguage.Ukrainian -> "Очікуваний малюнок Turtle",
    AppLanguage.Russian -> "Ожидаемый рисунок Turtle",
    AppLanguage.Turkish -> "Beklenen Turtle çizimi",
    AppLanguage.Danish -> "Forventet Turtle-tegning"
  ))

}
