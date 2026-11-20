package it.evadid.util

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.typedarray.{Int8Array, Uint8Array}

class JsHelpersSpec extends JsDomSuite {
  test("numeric JavaScript values convert to doubles, unparsable values fall back") {
    assertEquals(JsHelpers.doubleHelper.fromJsToScala(2.5), Some(2.5))
    assertEquals(JsHelpers.doubleHelper.fromJsToScala("-12.25"), Some(-12.25))
    assertEquals(JsHelpers.doubleHelper.fromJsToScala("ahead"), None)
    assertEquals(JsHelpers.doubleHelper.fromJsOrDefault("ahead", 7.0), 7.0)
    assertEquals(JsHelpers.doubleHelper.unsafeFromJsToScala("3"), 3.0)
    assertEquals(JsHelpers.doubleHelper.fromScalaToJs(1.5), 1.5: js.Any)
  }

  test("array buffers decode to signed bytes and survive a base64 round trip") {
    val bytes = new Uint8Array(js.Array[Short](0, 1, 127, 128, 255))
    val decoded = JsHelpers.decodeArrayBuffer(bytes.buffer)
    assertEquals(decoded.toList, List[Byte](0, 1, 127, -128, -1))
    val encoded = JsHelpers.byteArrayToBase64String(decoded)
    assertEquals(encoded, "AAF/gP8=")
    assertEquals(JsHelpers.base64StringToByteArray(encoded).toList, decoded.toList)
    assertEquals(JsHelpers.decodeArrayBuffer(new Int8Array(0).buffer).length, 0)
  }

  test("JavaScript promises complete Scala futures with their value or rejection") {
    val resolved = JsHelpers.promiseToFuture(js.Promise.resolve[Int](42))
    val rejected = JsHelpers.promiseToFuture(js.Promise.reject("offline").asInstanceOf[js.Promise[Int]])
    resolved.zip(rejected.failed).map { (value, error) =>
      assertEquals(value, 42)
      error match {
        case js.JavaScriptException(reason) => assertEquals(reason, "offline": Any)
        case other => fail(s"unexpected failure $other")
      }
    }
  }

  test("blobs convert to data URLs through the browser file reader") {
    val blob = js.Dynamic.newInstance(window.Blob)(js.Array("hi"), js.Dynamic.literal(`type` = "text/plain"))
    JsHelpers.blobToDataUrl(blob.asInstanceOf[org.scalajs.dom.Blob]).map(url =>
      assertEquals(url, "data:text/plain;base64,aGk="))
  }

  test("loose JavaScript values read as safe Scala defaults") {
    val missing: js.Any = js.undefined
    assertEquals(JsHelpers.readStringMap(js.Dictionary("a" -> "1")), Map("a" -> "1"))
    assertEquals(JsHelpers.readStringMap(missing), Map.empty[String, String])
    assertEquals(JsHelpers.asStringMap(null), Map.empty[String, String])
    assertEquals(JsHelpers.asStringMap(js.Dictionary("b" -> "2")), Map("b" -> "2"))
    assertEquals(JsHelpers.stringMapHelper.fromJsToScala(js.Dictionary("c" -> "3")), Some(Map("c" -> "3")))
    assertEquals(JsHelpers.stringMapHelper.fromScalaToJs(Map("d" -> "4")).asInstanceOf[js.Dictionary[String]].toMap, Map("d" -> "4"))
    assertEquals(JsHelpers.parseOrElse(js.undefined.asInstanceOf[js.Dynamic], 5), 5)
    assertEquals(JsHelpers.parseOrElse((6: js.Any).asInstanceOf[js.Dynamic], 5), 6)
    assertEquals(JsHelpers.parseOrEmpty[String](js.undefined.asInstanceOf[js.Dynamic]), None)
    assertEquals(JsHelpers.parseOrEmpty[String](("x": js.Any).asInstanceOf[js.Dynamic]), Some("x"))
    assertEquals(JsHelpers.asStringSeq(js.Array("a", "b")), Seq("a", "b"))
    assertEquals(JsHelpers.asStringSeq(missing), Seq.empty)
    assertEquals(JsHelpers.asStringOption("text"), Some("text"))
    assertEquals(JsHelpers.asStringOption(3), None)
    assertEquals(JsHelpers.asStringOption(null), None)
    assertEquals(JsHelpers.asBoolean(true), true)
    assertEquals(JsHelpers.asBoolean(missing), false)
    assertEquals(JsHelpers.asInt(4.9), 4)
    assertEquals(JsHelpers.asInt(null), 0)
    assertEquals(JsHelpers.anyToSeq(js.Array[js.Any](1, 2)), Seq[js.Any](1, 2))
    assertEquals(JsHelpers.anyToSeq("one"), Seq[js.Any]("one"))
    assertEquals(JsHelpers.anyToSeq(missing), Seq.empty)
    val record = JsHelpers.obj("name" -> "Eva", "ok" -> true, "items" -> js.Array(1))
    assertEquals(JsHelpers.asString(JsHelpers.asDynamic(record).name), "Eva")
    assertEquals(JsHelpers.asDict(record).keys.toList, List("name", "ok", "items"))
    assertEquals(JsHelpers.asArray(JsHelpers.asDynamic(record).items).length, 1)
    assertEquals(js.Object.keys(JsHelpers.emptyObj).length, 0)
  }

  test("required field readers name missing or mistyped fields") {
    val record = js.Dynamic.literal(name = "Eva", ok = true, items = js.Array(1), count = 3)
    assertEquals(JsHelpers.readStringField(record, "name"), "Eva")
    assertEquals(JsHelpers.readBooleanField(record, "ok"), true)
    assertEquals(JsHelpers.readArrayField(record, "items").length, 1)
    assert(intercept[IllegalArgumentException](JsHelpers.readRequiredField(record, "absent", "Shown"))
      .getMessage.contains("'Shown'"))
    assert(intercept[IllegalArgumentException](JsHelpers.readRequiredField(record, "absent")).getMessage.contains("'absent'"))
    assert(intercept[IllegalArgumentException](JsHelpers.readStringField(record, "count")).getMessage.contains("'number'"))
    assert(intercept[IllegalArgumentException](JsHelpers.readBooleanField(record, "name")).getMessage.contains("'string'"))
    assert(intercept[IllegalArgumentException](JsHelpers.readArrayField(record, "ok")).getMessage.contains("'boolean'"))
  }

  test("JavaScript error messages prefer the thrown value and fall back when absent") {
    assertEquals(JsHelpers.javascriptErrorMessage(js.JavaScriptException("boom"), "fallback"), "boom")
    assertEquals(JsHelpers.javascriptErrorMessage(js.JavaScriptException(null), "fallback"), "fallback")
    assertEquals(JsHelpers.javascriptErrorMessage(RuntimeException("scala"), "fallback"), "scala")
    assertEquals(JsHelpers.javascriptErrorMessage(RuntimeException(), "fallback"), "fallback")
  }
}
