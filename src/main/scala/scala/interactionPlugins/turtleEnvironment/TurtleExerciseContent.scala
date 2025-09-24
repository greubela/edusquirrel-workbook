package scala.interactionPlugins.turtleEnvironment

import contentmanagement.model.language.AppLanguage
import workbook.model.exercise.ExerciseContent

/**
 * Turtle specific exercise meta information. Besides the textual instructions provided by [[ExerciseContent]]
 * it also stores the target SVG the learner should recreate and the sample program that produces this SVG.
 */
class TurtleExerciseContent(
  id: String,
  titleMap: Map[AppLanguage, String],
  instructionMap: Map[AppLanguage, String],
  val targetSvg: String,
  val sampleProgram: TurtleProgramState,
  val targetDescription: Option[String] = None
) extends ExerciseContent(id, titleMap, instructionMap) {

  /** Convenience accessor mirroring the default constructor semantics of [[ExerciseContent]]. */
  def this(
    base: ExerciseContent,
    targetSvg: String,
    sampleProgram: TurtleProgramState,
    targetDescription: Option[String]
  ) = this(base.id, base.titleMap, base.instructionMap, targetSvg, sampleProgram, targetDescription)

  /** Creates a copy of this content with a different SVG while keeping the remaining information. */
  def withTargetSvg(svg: String): TurtleExerciseContent =
    new TurtleExerciseContent(id, titleMap, instructionMap, svg, sampleProgram, targetDescription)

  /** Exposes the underlying [[ExerciseContent]] representation without the turtle specific additions. */
  def asExerciseContent: ExerciseContent = ExerciseContent(id, titleMap, instructionMap)
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

  def apply(base: ExerciseContent, targetSvg: String, sampleProgram: TurtleProgramState): TurtleExerciseContent =
    new TurtleExerciseContent(base, targetSvg, sampleProgram, None)
}
