package interactionPlugins.blockEnvironment.programming.blocks.displayUtility

import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import interactionPlugins.blockEnvironment.programming.BeDataType
import interactionPlugins.blockEnvironment.programming.blocks.BeBlockAtomar
import interactionPlugins.blockEnvironment.programming.rendering.*
import interactionPlugins.blockEnvironment.programming.rendering.shapes.BeShape

case class BeBlockOptionalValue(acceptsType: Set[BeDataType]) extends BeBlockAtomar {

  override val displayShape: BeShape = BeDataType.getShape(acceptsType)

}

