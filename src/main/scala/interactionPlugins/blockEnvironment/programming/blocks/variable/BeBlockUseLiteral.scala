package interactionPlugins.blockEnvironment.programming.blocks.variable

import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.atomarElements.AppTextSvgElement
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.traits.*
import interactionPlugins.blockEnvironment.programming.connection.BeValueRole
import interactionPlugins.blockEnvironment.programming.rendering.*
import interactionPlugins.blockEnvironment.programming.{BeBlockContext, BeDataType}

case class BeBlockUseLiteral(
                              override val roleInParent: BeValueRole,
                              valueStr: String, dataType: BeDataType
                            ) extends BeBlockValue with BeBlockLogic {


  override val displayedText: Option[LanguageMap[HumanLanguage]] = Some(LanguageMap.universalMap(dataType.formatStringForDisplay(valueStr )))

  override val displayShape: BeShape = dataType.associatedShape

}
