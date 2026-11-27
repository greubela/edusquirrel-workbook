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

  test("the backend is production unless EDUSQUIRREL_BACKEND_URL names an http(s) origin") {
    import it.evadid.distribution.clients.RemoteExecutionConfig
    assertEquals(ClientRuntimeConfig.backendFrom(Map.empty), ClientRuntimeConfig.productionBackend)
    assertEquals(ClientRuntimeConfig.backendFrom(Map("EDUSQUIRREL_BACKEND_URL" -> " ")), ClientRuntimeConfig.productionBackend)
    assertEquals(ClientRuntimeConfig.backendFrom(Map("EDUSQUIRREL_BACKEND_URL" -> "http://localhost:9000")),
      RemoteExecutionConfig("http://localhost", 9000))
    assertEquals(RemoteExecutionConfig.fromUrl("https://backend.example.org"), RemoteExecutionConfig("https://backend.example.org", 443))
    assertEquals(RemoteExecutionConfig.fromUrl("http://[::1]:9000/"), RemoteExecutionConfig("http://[::1]", 9000))
    Seq("localhost:9000", "ftp://localhost", "http://localhost:9000/api", "http://localhost:9000?x=1", "http://user:pw@localhost", "not a url")
      .foreach(url => intercept[IllegalArgumentException](RemoteExecutionConfig.fromUrl(url)))
  }
}
