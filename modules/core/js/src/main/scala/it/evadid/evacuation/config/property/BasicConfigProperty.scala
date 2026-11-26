package it.evadid.evacuation.config.property

import it.evadid.evacuation.config.value.{ConfigValue, SimpleConfigValue}

case class BasicConfigProperty[T](name: String, defaultValue: ConfigValue[T], description: Option[String]) extends ConfigProperty[T] {


}

object BasicConfigProperty {

  def apply[T](name: String, startValue: T, description: String) = new BasicConfigProperty[T](name, new SimpleConfigValue[T](startValue), Some(description))

}
