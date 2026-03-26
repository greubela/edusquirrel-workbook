package util.web

import scala.concurrent.{Future, Promise}
import scala.scalajs.js

object JsHelpers {

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
}
