package it.evadid.evacuation.config.value

class SimpleConfigValue[T](value: T) extends ConfigValue[T](value, String.valueOf(value), Some(String.valueOf(value))) {

  override def equals(obj: Any): Boolean = {
    obj match {
      case casted: SimpleConfigValue[?] => casted.value == value
      case _ => false
    }
  }

}
