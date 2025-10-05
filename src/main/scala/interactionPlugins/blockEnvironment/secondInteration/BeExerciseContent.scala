package interactionPlugins.blockEnvironment.secondInteration

import contentmanagement.datastructures.tree.Tree
import contentmanagement.datastructures.tree.nodeImpl.{NodeBasedTreeImpl, NodeBasedTreePosition}
import contentmanagement.model.language.AppLanguage
import interactionPlugins.blockEnvironment.programming.{BeBlock, BeDataType, BeProgram}
import interactionPlugins.blockEnvironment.programming.blocks.{BeBlockFunctionDefinition, BeBlockValue, BeMotionBlocks}
import interactionPlugins.blockEnvironment.programming.rendering.BeRendererConfig
import workbook.model.exercise.ExerciseContent

case class BeExerciseContent(
  id: String,
  titleMap: Map[AppLanguage, String],
  instructionMap: Map[AppLanguage, String],
  paletteModel: BeBlockPaletteModel,
  rendererConfig: BeRendererConfig,
  initialProgram: BeProgram,
  estimatedDurationInMinutes: Double = 5.0
) extends ExerciseContent {

  override def estimatedTimeInMinutes: Double = estimatedDurationInMinutes
}

object BeExerciseContent {

  def sampleExercise(rendererConfig: BeRendererConfig): BeExerciseContent = {
    val categories = List(
      BePaletteCategory(
        id = "motion",
        label = "Motion",
        entries = List(
          BePaletteEntry("forward", "Move forward", role => BeMotionBlocks.BeBlockForward(role))
        )
      ),
      BePaletteCategory(
        id = "values",
        label = "Values",
        entries = List(
          BePaletteEntry("number", "Number literal", role => BeBlockValue(BeDataType.Numeric, role, Some("10")))
        )
      )
    )

    val palette = BeBlockPaletteModel(categories)

    val initialTree: Tree[BeBlock, NodeBasedTreePosition] = {
      var tree: Tree[BeBlock, NodeBasedTreePosition] = NodeBasedTreeImpl.empty[BeBlock]()
      tree = tree.addChild(tree.rootPosition, BeBlockFunctionDefinition.starterBlock())
      tree
    }

    BeExerciseContent(
      id = "be-sample",
      titleMap = Map(AppLanguage.English -> "Sample block environment"),
      instructionMap = Map(AppLanguage.English -> "Drag blocks into the workspace to assemble a program."),
      paletteModel = palette,
      rendererConfig = rendererConfig,
      initialProgram = BeProgram(initialTree)
    )
  }
}
