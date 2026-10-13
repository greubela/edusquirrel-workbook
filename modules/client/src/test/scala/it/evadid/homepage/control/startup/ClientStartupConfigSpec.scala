package it.evadid.homepage.control.startup

import munit.FunSuite
import scala.scalajs.js

class ClientStartupConfigSpec extends FunSuite {
  test("deployment settings retain quoted values and ignore comments") {
    val config = ClientStartupConfig.parse("# ignored\nA='value=with=equals'\nB=\"2\"\ninvalid\n")
    assertEquals(config.selectDynamic("A").asInstanceOf[String], "value=with=equals")
    assertEquals(config.selectDynamic("B").asInstanceOf[String], "2")
    assert(js.isUndefined(config.selectDynamic("invalid")))
  }
  test("background worker counts allow disabling warmup and reject invalid values") {
    assertEquals(ClientStartupConfig.workerCount(js.Dynamic.literal()), 1)
    assertEquals(ClientStartupConfig.workerCount(ClientStartupConfig.parse("EDUSQUIRREL_BACKEND_MODULE_WORKERS=0")), 0)
    List("-1", "garbage").foreach(value =>
      assertEquals(ClientStartupConfig.workerCount(ClientStartupConfig.parse(s"EDUSQUIRREL_BACKEND_MODULE_WORKERS=$value")), 1))
  }
}
