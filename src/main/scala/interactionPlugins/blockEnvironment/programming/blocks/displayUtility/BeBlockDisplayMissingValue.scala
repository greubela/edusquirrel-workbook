package interactionPlugins.blockEnvironment.programming.blocks.displayUtility

import com.raquo.laminar.api.L
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import interactionPlugins.blockEnvironment.programming.BeDataType
import interactionPlugins.blockEnvironment.programming.blocks.BeBlockAtomar
import interactionPlugins.blockEnvironment.programming.rendering.*
import interactionPlugins.blockEnvironment.programming.rendering.shapes.BeShape

case class BeBlockDisplayMissingValue(acceptsType: Set[BeDataType]) extends BeBlockAtomar {

  override val displayShape: BeShape = BeDataType.getShape(acceptsType)

}
