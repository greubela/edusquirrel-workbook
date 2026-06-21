package it.evadid.evacuation.config.property.discrete

import it.evadid.evacuation.config.property.ObservableConfigProperty
import it.evadid.evacuation.config.value.ConfigValue

trait ObservableDiscreteConfigProperty[T] extends ObservableConfigProperty[T] {

  def possibleValues(): List[ConfigValue[T]]

}
