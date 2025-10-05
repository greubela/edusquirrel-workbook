package interactionPlugins.blockEnvironment.programming.connection

import contentmanagement.model.language.LanguageMap
import interactionPlugins.blockEnvironment.programming.*





trait BeConnection {
  def connectionRole: BeConnectionRole

  def connectionType: BeDataType

  def connectionCardinality: BeConnectionCardinality
}





