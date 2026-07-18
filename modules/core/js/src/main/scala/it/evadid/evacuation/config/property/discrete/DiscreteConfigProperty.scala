package it.evadid.evacuation.config.property.discrete

import it.evadid.evacuation.config.property.ConfigProperty
import it.evadid.evacuation.config.value.ConfigValue

trait DiscreteConfigProperty[T] extends ConfigProperty[T] {

  def possibleValues: List[ConfigValue[T]]

  assert(possibleValues.contains(defaultValue))
  assert(possibleValues.map(_.name).distinct.size == possibleValues.size, "names of possible values must be unique!")
  assert(possibleValues.map(_.value).distinct.size == possibleValues.size, "values of possible values must be unique!")

}
