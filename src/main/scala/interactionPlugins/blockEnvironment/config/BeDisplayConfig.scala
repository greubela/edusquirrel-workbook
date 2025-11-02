package interactionPlugins.blockEnvironment.config

case class BeDisplayConfig(displayNavigation: Boolean, displayControlFlow: Boolean, compactDefinitions: Boolean) {

}

object BeDisplayConfig {

  def default(): BeDisplayConfig = BeDisplayConfig(true, true, true)

}
