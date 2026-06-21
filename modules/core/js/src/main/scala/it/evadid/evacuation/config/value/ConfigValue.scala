package it.evadid.evacuation.config.value

case class ConfigValue[T](value: T, name: String, description: Option[String]) {

  def toPrint: String = if (description.isDefined) description.get else name

}
