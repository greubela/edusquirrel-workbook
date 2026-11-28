package it.evadid.server

/** Server configuration from explicit settings only, never from the test machine's environment. */
object TestServerConfig {
  def apply(settings: (String, String)*): ServerConfig =
    ServerConfig.from((Map("SERVER_SECRET" -> "unit-test-secret") ++ settings).get)
}
