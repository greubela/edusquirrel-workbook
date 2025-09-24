package workbook.model.display

import contentmanagement.model.language.{AppLanguage, LanguageMap}
import workbook.model.display.InteractionComponent.InteractionContentRole
import workbook.model.display.InteractionComponent.InteractionContentRole.*

case class FullInteractionLabelModel(labelMap: Map[AppLanguage, Map[InteractionContentRole, String]]) {

  def supportedInteractionRoles: Set[InteractionContentRole] = labelMap.values.flatMap(_.keys).toSet

  def getLabelFor(language: AppLanguage, role: InteractionContentRole): String = {
    val alternativeString =
      if (language == AppLanguage.English)
        s"[label not found for '$role' in language '$language']"
      else getLabelFor(AppLanguage.English, role)

    labelMap.get(language).flatMap(_.get(role)).getOrElse(alternativeString)
  }
}


object FullInteractionLabelModel {


  val defaultInteractionLabeling = FullInteractionLabelModel(
    Map[AppLanguage, Map[InteractionContentRole, String]](

      AppLanguage.German -> Map[InteractionContentRole, String](
        Editor -> "Lösung",
        ScaffoldingStateEditor -> "Hilfe (Frage)",
        GradingStateEditor -> "Bewertung (Einstellungen)",
        ScaffoldingResult -> "Hilfe (Antwort)",
        GradingResult -> "Bewertung (Ergebnis)"
      ),

      AppLanguage.English -> Map[InteractionContentRole, String](
        Editor -> "Solution",
        ScaffoldingStateEditor -> "Help (Question)",
        GradingStateEditor -> "Evaluation (Config)",
        ScaffoldingResult -> "Help (Answer)",
        GradingResult -> "Evaluation (Grade)",
      )
    ))

}