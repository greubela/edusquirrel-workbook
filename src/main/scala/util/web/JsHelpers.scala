package util.web

import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.typedarray.{ArrayBuffer, DataView}

object JsHelpers {


  def decodeArrayBuffer(buf: ArrayBuffer): Array[Byte] = {
    val data = new DataView(buf)
    Array.tabulate[Byte](data.byteLength)(index => data.getInt8(index))
  }

  def base64StringToByteArray(in: String): Array[Byte] = java.util.Base64.getDecoder.decode(in)

  def byteArrayToBase64String(in: Array[Byte]): String = java.util.Base64.getEncoder.encodeToString(in)


  def parseDouble(s: js.Any): Option[Double] = {
    try Some(s.asInstanceOf[Double])
    catch case _ => try Some(s.toString.toDouble)
    catch case _ => None
  }
  
  def promiseToFuture[A](p: js.Promise[A]): Future[A] = {
    val pr = Promise[A]()
    p.`then`[Unit](
      (value: A) => {
        pr.success(value)
        ()
      },
      (err: Any) => {
        pr.failure(js.JavaScriptException(err))
        ()
      }
    )
    pr.future
  }

  def asStringMap(value: js.Any): Map[String, String] =
    if value == null || js.isUndefined(value) then Map.empty
    else value.asInstanceOf[js.Dictionary[String]].toMap

  def asStringSeq(value: js.Any): Seq[String] =
    if value == null || js.isUndefined(value) then Seq.empty
    else value.asInstanceOf[js.Array[String]].toSeq

  def asStringOption(value: js.Any): Option[String] =
    if value == null || js.isUndefined(value) then None
    else if js.typeOf(value) == "string" then Some(value.asInstanceOf[String])
    else None

  def asBoolean(value: js.Any): Boolean =
    if value == null || js.isUndefined(value) then false
    else value.asInstanceOf[Boolean]

  def asInt(value: js.Any): Int =
    if value == null || js.isUndefined(value) then 0
    else value.asInstanceOf[Double].toInt

  def asDynamic(value: js.Any): js.Dynamic =
    value.asInstanceOf[js.Dynamic]

  def asArray(value: js.Any): js.Array[js.Any] =
    value.asInstanceOf[js.Array[js.Any]]

  def asDict(value: js.Any): js.Dictionary[js.Any] =
    value.asInstanceOf[js.Dictionary[js.Any]]

  def asString(value: js.Any): String =
    value.asInstanceOf[String]

  def obj(fields: (String, js.Any)*): js.Object =
    js.Dynamic.literal(fields *).asInstanceOf[js.Object]

  val emptyObj: js.Object =
    (new js.Object).asInstanceOf[js.Object]

  def readRequiredField(dyn: js.Dynamic, fieldName: String, displayFieldName: String = ""): js.Any = {
    val shownName = if displayFieldName.nonEmpty then displayFieldName else fieldName
    val raw = dyn.selectDynamic(fieldName).asInstanceOf[js.Any]
    if (js.isUndefined(raw) || raw == null)
      throw new IllegalArgumentException(s"Missing required field '$shownName'.")
    raw
  }

  def readStringField(dyn: js.Dynamic, fieldName: String): String = {
    val raw = readRequiredField(dyn, fieldName, fieldName)
    if (js.typeOf(raw) == "string") raw.asInstanceOf[String]
    else throw new IllegalArgumentException(s"Expected string field '$fieldName', but got '${js.typeOf(raw)}'.")
  }

  def readBooleanField(dyn: js.Dynamic, fieldName: String): Boolean = {
    val raw = readRequiredField(dyn, fieldName, fieldName)
    if (js.typeOf(raw) == "boolean") raw.asInstanceOf[Boolean]
    else throw new IllegalArgumentException(s"Expected boolean field '$fieldName', but got '${js.typeOf(raw)}'.")
  }

  def readArrayField(dyn: js.Dynamic, fieldName: String): js.Array[js.Any] = {
    val raw = readRequiredField(dyn, fieldName, fieldName)
    if (js.Array.isArray(raw.asInstanceOf[js.Any])) raw.asInstanceOf[js.Array[js.Any]]
    else throw new IllegalArgumentException(s"Expected array field '$fieldName', but got '${js.typeOf(raw)}'.")
  }

  def anyToSeq(raw: js.Any): Seq[js.Any] =
    if js.isUndefined(raw) || raw == null then Seq.empty
    else if js.Array.isArray(raw) then raw.asInstanceOf[js.Array[js.Any]].toSeq
    else Seq(raw)

  def javascriptErrorMessage(err: Throwable, fallback: String): String =
    err match {
      case jsErr: js.JavaScriptException =>
        Option(jsErr.exception).map(_.toString).getOrElse(fallback)
      case other =>
        Option(other.getMessage).getOrElse(fallback)
    }
}
