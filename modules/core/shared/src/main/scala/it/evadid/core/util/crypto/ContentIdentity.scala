package it.evadid.core.util.crypto

/** Canonical parameter encoding: maps/sets are unordered; sequences retain their order. */
object ContentIdentity {
  private def canonical(value: ujson.Value): ujson.Value = value match {
    case value: ujson.Obj => ujson.Obj.from(value.obj.toList.sortBy(_._1).map((name, item) => name -> canonical(item)))
    case value: ujson.Arr => ujson.Arr.from(value.arr.map(canonical))
    case value => value
  }
  def json(value: Any): ujson.Value = value match {
    case null => ujson.Null
    case value: ujson.Value => canonical(value)
    case value: String => ujson.Str(value)
    case value: Boolean => ujson.Bool(value)
    case value: Char => ujson.Obj("char" -> value.toString)
    case value: BigDecimal => ujson.Obj("decimal" -> value.bigDecimal.stripTrailingZeros.toPlainString)
    case value: BigInt => ujson.Obj("integer" -> value.toString)
    case value: Long => ujson.Obj("long" -> value.toString)
    case value: java.lang.Number =>
      val number = value.doubleValue()
      if number.isNaN || number.isInfinity then ujson.Obj("number" -> number.toString)
      else ujson.Num(if number == 0 then 0 else number)
    case value: collection.Map[?, ?] =>
      val pairs = value.iterator.map((key, item) => ujson.Arr(json(key), json(item))).toList.sortBy(ujson.write(_))
      ujson.Obj("map" -> ujson.Arr.from(pairs))
    case value: collection.Set[?] => ujson.Obj("set" -> ujson.Arr.from(value.iterator.map(json).toList.sortBy(ujson.write(_))))
    case value: Seq[?] => ujson.Obj("sequence" -> ujson.Arr.from(value.map(json)))
    case value: Product =>
      ujson.Obj("type" -> value.getClass.getName, "constructor" -> value.productPrefix,
        "parameters" -> ujson.Obj.from(value.productElementNames.zip(value.productIterator).map((name, item) => name -> json(item))))
    case value => throw IllegalArgumentException(s"No canonical identity encoding for ${value.getClass.getName}; provide identityContent explicitly")
  }

  def key(value: ujson.Value): String = "sha256:" + Sha256.text(ujson.write(canonical(value)))
}
