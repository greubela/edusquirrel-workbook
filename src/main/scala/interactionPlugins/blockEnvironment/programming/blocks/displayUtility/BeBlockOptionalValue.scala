package interactionPlugins.blockEnvironment.programming.blocks.displayUtility

import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import interactionPlugins.blockEnvironment.programming.BeDataType
import interactionPlugins.blockEnvironment.programming.blocks.BeBlockAtomar
import interactionPlugins.blockEnvironment.programming.rendering.BeShape

case class BeBlockOptionalValue(acceptsType: Set[BeDataType]) extends BeBlockAtomar {

  override val displayShape: BeShape = BeShape(acceptsType)

  override val displayedText: Option[LanguageMap[HumanLanguage]] = None

}

