package it.evadid.evacuation.config.property.discrete

import it.evadid.evacuation.config.value.{ConfigValue, SimpleConfigValue}

case class BasicDiscreteConfigProperty[T](name: String, possibleValues: List[ConfigValue[T]], defaultValue: ConfigValue[T], description: Option[String]) extends DiscreteConfigProperty[T] {

}

object BasicDiscreteConfigProperty {

  def apply[T](name: String, possibleValues: List[ConfigValue[T]], defaultValue: T, description: String): BasicDiscreteConfigProperty[T] = {
    val defaultValueObj = possibleValues.find(_.value == defaultValue).get
    new BasicDiscreteConfigProperty(name, possibleValues, defaultValueObj, Some(description))
  }

  def apply[T](name: String, possibleValues: List[ConfigValue[T]], defaultValue: T): BasicDiscreteConfigProperty[T] = {
    val defaultValueObj = possibleValues.find(_.value == defaultValue).get
    new BasicDiscreteConfigProperty(name, possibleValues, defaultValueObj, None)
  }

  def createBooleanProperty(name: String, description: String): BasicDiscreteConfigProperty[Boolean] = {
    val trueVal = new SimpleConfigValue[Boolean](true)
    val falseVal = new SimpleConfigValue[Boolean](false)
    new BasicDiscreteConfigProperty[Boolean](name, List(trueVal, falseVal), falseVal, Some(description))
  }


}
