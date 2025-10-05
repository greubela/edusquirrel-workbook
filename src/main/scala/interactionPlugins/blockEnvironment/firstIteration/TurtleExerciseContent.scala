package interactionPlugins.blockEnvironment.firstIteration

import contentmanagement.model.language.AppLanguage
import workbook.model.exercise.ExerciseContent

/**
 * Turtle specific exercise meta information. Besides the textual instructions provided by [[ExerciseContent]]
 * it also stores the target SVG the learner should recreate and the sample program that produces this SVG.
 */
case class TurtleExerciseContent(
  id: String,
  titleMap: Map[AppLanguage, String],
  instructionMap: Map[AppLanguage, String],
   targetSvg: String,
   sampleProgram: TurtleProgramState,
   targetDescription: Option[String] = None
) extends ExerciseContent {

  override def estimatedTimeInMinutes: Double = 5

  /** Creates a copy of this content with a different SVG while keeping the remaining information. */
  def withTargetSvg(svg: String): TurtleExerciseContent =
    new TurtleExerciseContent(id, titleMap, instructionMap, svg, sampleProgram, targetDescription)

 }

object TurtleExerciseContent {

  /** Factory method that mirrors the [[ExerciseContent]] companion in shape. */
  def apply(
    id: String,
    titleMap: Map[AppLanguage, String],
    instructionMap: Map[AppLanguage, String],
    targetSvg: String,
    sampleProgram: TurtleProgramState,
    targetDescription: Option[String] = None
  ): TurtleExerciseContent =
    new TurtleExerciseContent(id, titleMap, instructionMap, targetSvg, sampleProgram, targetDescription)

 /* def apply(base: ExerciseContent, targetSvg: String, sampleProgram: TurtleProgramState): TurtleExerciseContent =
    new TurtleExerciseContent(base, targetSvg, sampleProgram, None)*/
}
