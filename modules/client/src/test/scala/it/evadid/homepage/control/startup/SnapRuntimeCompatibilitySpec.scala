package it.evadid.homepage.control.startup

import com.raquo.airstream.state.Var
import munit.FunSuite
import scala.scalajs.js

class SnapRuntimeCompatibilitySpec extends FunSuite {
  private class Fixture(targetApp: String = "TurtleStitch 3.0") {
    val calls = Var(0)
    val serializer = js.Dynamic.literal(app = targetApp)
    val ide = js.Dynamic.literal(name = "test IDE")
    val remix = js.Dynamic.literal(id = 123)
    val result = js.Dynamic.literal(ok = true)
    val original: js.ThisFunction3[js.Dynamic, js.Dynamic, js.Any, js.Any, js.Any] =
      (self: js.Dynamic, _: js.Dynamic, actualIde: js.Any, actualRemix: js.Any) => {
        assert(self == serializer)
        assert(actualIde == ide)
        assert(actualRemix == remix)
        calls.update(_ + 1)
        result
      }
    val prototype = js.Dynamic.literal(loadProjectModel = original)
    def invoke(xml: js.Dynamic): js.Dynamic =
      prototype.loadProjectModel.call(serializer, xml, ide, remix)
  }

  test("foreign application headers use the active serializer while preserving receiver, arguments and result") {
    val fixture = new Fixture
    val xml = js.Dynamic.literal(attributes = js.Dynamic.literal(app = "Snap! 9.0", name = "my program"))
    SnapRuntimeCompatibility.patchSerializer(fixture.prototype)
    assert(fixture.invoke(xml) == fixture.result)
    assertEquals(xml.attributes.app.asInstanceOf[String], "TurtleStitch 3.0")
    assertEquals(xml.attributes.name.asInstanceOf[String], "my program")
    assertEquals(fixture.calls.now(), 1)
  }

  test("matching application names retain the source version") {
    val fixture = new Fixture
    val xml = js.Dynamic.literal(attributes = js.Dynamic.literal(app = "TurtleStitch 1.0"))
    SnapRuntimeCompatibility.patchSerializer(fixture.prototype)
    fixture.invoke(xml)
    assertEquals(xml.attributes.app.asInstanceOf[String], "TurtleStitch 1.0")
  }

  test("missing headers and missing XML are delegated unchanged") {
    val fixture = new Fixture
    SnapRuntimeCompatibility.patchSerializer(fixture.prototype)
    List(js.Dynamic.literal(), js.Dynamic.literal(attributes = js.Dynamic.literal()),
      null.asInstanceOf[js.Dynamic], js.undefined.asInstanceOf[js.Dynamic]).foreach { xml =>
      assert(fixture.invoke(xml) == fixture.result)
    }
    assertEquals(fixture.calls.now(), 4)
  }

  test("empty source application names are not rewritten") {
    val fixture = new Fixture
    SnapRuntimeCompatibility.patchSerializer(fixture.prototype)
    List("", " 9.0").foreach { app =>
      val xml = js.Dynamic.literal(attributes = js.Dynamic.literal(app = app))
      fixture.invoke(xml)
      assertEquals(xml.attributes.app.asInstanceOf[String], app)
    }
  }

  test("an empty target application does not rewrite source metadata") {
    val fixture = new Fixture("")
    val xml = js.Dynamic.literal(attributes = js.Dynamic.literal(app = "Snap! 9.0"))
    SnapRuntimeCompatibility.patchSerializer(fixture.prototype)
    fixture.invoke(xml)
    assertEquals(xml.attributes.app.asInstanceOf[String], "Snap! 9.0")
  }

  test("repeated installation reuses the same wrapper and a fresh prototype is independently patched") {
    val fixture = new Fixture
    SnapRuntimeCompatibility.patchSerializer(fixture.prototype)
    val wrapped = fixture.prototype.loadProjectModel
    SnapRuntimeCompatibility.patchSerializer(fixture.prototype)
    assert(wrapped == fixture.prototype.loadProjectModel)
    fixture.invoke(js.Dynamic.literal())
    assertEquals(fixture.calls.now(), 1)
    val fresh = new Fixture
    SnapRuntimeCompatibility.patchSerializer(fresh.prototype)
    fresh.invoke(js.Dynamic.literal())
    assertEquals(fresh.calls.now(), 1)
  }
}
