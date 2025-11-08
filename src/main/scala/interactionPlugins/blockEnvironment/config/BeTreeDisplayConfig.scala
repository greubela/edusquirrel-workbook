package interactionPlugins.blockEnvironment.config

case class BeTreeDisplayConfig(displayPlaceholders: Boolean, displayNavigation: Boolean, displayControlFlow: Boolean, compactDefinitions: Boolean, compactFunctionCalls: Boolean) {

}

object BeTreeDisplayConfig {

  def default(): BeTreeDisplayConfig = BeTreeDisplayConfig(false, true, true, true, false)

}
