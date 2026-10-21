package it.evadid.homepage.control.startup

import munit.FunSuite

class ClientRuntimeConfigSpec extends FunSuite {
  test("runtime settings preserve quoted values and ignore comments and invalid lines") {
    assertEquals(ClientRuntimeConfig.parseEnv("""# public client settings
      |EDUSQUIRREL_BACKEND_MODULE_WORKERS=2
      |EDUSQUIRREL_BACKEND_MODULE_WORKER_SCRIPT="worker.js?build=123"
      |LABEL='a=b'
      |INVALID
      |=missing-key
      |""".stripMargin), Map(
      "EDUSQUIRREL_BACKEND_MODULE_WORKERS" -> "2",
      "EDUSQUIRREL_BACKEND_MODULE_WORKER_SCRIPT" -> "worker.js?build=123", "LABEL" -> "a=b"))
  }
}
