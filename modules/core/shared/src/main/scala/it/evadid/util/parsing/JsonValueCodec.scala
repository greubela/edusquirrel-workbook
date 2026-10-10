package it.evadid.util.parsing

import upickle.default.*

/** Value classes have no product Mirror; persist their values through ordinary products. */
private[parsing] object JsonValueCodec {
  private sealed trait StoredValue derives ReadWriter
  private case class StoredString(value: String) extends StoredValue derives ReadWriter
  private case class StoredObject(value: List[(String, StoredValue)]) extends StoredValue derives ReadWriter
  private case class StoredArray(value: List[StoredValue]) extends StoredValue derives ReadWriter
  private case class StoredNumber(value: Double) extends StoredValue derives ReadWriter
  private case object StoredFalse extends StoredValue
  private case object StoredTrue extends StoredValue
  private case object StoredNull extends StoredValue

  private def store(value: Js.Val): StoredValue = value match {
    case Js.Str(value) => StoredString(value)
    case value: Js.Obj => StoredObject(value.value.toList.map((key, child) => key -> store(child)))
    case value: Js.Arr => StoredArray(value.value.toList.map(store))
    case Js.Num(value) => StoredNumber(value)
    case Js.False => StoredFalse
    case Js.True => StoredTrue
    case Js.Null => StoredNull
  }

  private def restore(value: StoredValue): Js.Val = value match {
    case StoredString(value) => Js.Str(value)
    case StoredObject(value) => Js.Obj(value.map((key, child) => key -> restore(child))*)
    case StoredArray(value) => Js.Arr(value.map(restore)*)
    case StoredNumber(value) => Js.Num(value)
    case StoredFalse => Js.False
    case StoredTrue => Js.True
    case StoredNull => Js.Null
  }

  def valueCodec: ReadWriter[Js.Val] = readwriter[StoredValue].bimap(store, restore)

  def concreteCodec[T <: Js.Val](accept: Js.Val => Option[T]): ReadWriter[T] =
    valueCodec.bimap(value => value, value => accept(value)
      .getOrElse(throw new IllegalArgumentException("Unexpected JSON node type")))
}
