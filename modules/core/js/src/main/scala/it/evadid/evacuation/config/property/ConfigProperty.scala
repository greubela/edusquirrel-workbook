package it.evadid.evacuation.config.property

import it.evadid.evacuation.config.value.ConfigValue

trait ConfigProperty[T] {
  def name: String

  def defaultValue: ConfigValue[T]

  def description: Option[String]

}
