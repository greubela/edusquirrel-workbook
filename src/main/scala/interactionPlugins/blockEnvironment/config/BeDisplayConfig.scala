package interactionPlugins.blockEnvironment.config

case class BeDisplayConfig(compactDefinitions: Boolean) {


}

object BeDisplayConfig {

  def default(): BeDisplayConfig = BeDisplayConfig(true)

}
