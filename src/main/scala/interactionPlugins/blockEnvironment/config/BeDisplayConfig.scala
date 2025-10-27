package interactionPlugins.blockEnvironment.config

case class BeDisplayConfig(useCompactBlocks: Boolean) {

}

object BeDisplayConfig {

  def default(): BeDisplayConfig = BeDisplayConfig(true)

}
