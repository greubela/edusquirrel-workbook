package it.evadid.evacuation.config.property.discrete

import it.evadid.evacuation.config.property.ObservableConfigProperty
import it.evadid.evacuation.config.property.ObservableConfigProperty.PropertyListener
import it.evadid.evacuation.config.value.ConfigValue

import scala.collection.mutable

class BasicDiscreteObservableConfigProperty[T](val property: DiscreteConfigProperty[T]) extends ObservableConfigProperty[T] with ObservableDiscreteConfigProperty[T] {
  private var currentValue: ConfigValue[T] = property.defaultValue

  override val listener: mutable.ListBuffer[PropertyListener[T]] = mutable.ListBuffer(
    new PropertyListener[T] {
      override def onPropertyChange(property: it.evadid.evacuation.config.property.ConfigProperty[T], oldValue: ConfigValue[T], newValue: ConfigValue[T]): Unit =
        println(property.name + " changed: " + oldValue.name + " -> " + newValue.name)
    }
  )

  override def getValue: ConfigValue[T] = currentValue

  override def setValue(newValue: T): Unit = {
    assert(property.possibleValues.map(_.value).contains(newValue), "Value '" + newValue + "' is not allowed for " + property.name)

    val oldValue = currentValue
    currentValue = property.possibleValues.find(_.value == newValue).get

    if (oldValue != currentValue) {
      listener.foreach(_.onPropertyChange(property, oldValue, currentValue))
    }
  }

  def setValue(newValueName: String): Option[Exception] = {

    val newValueOp = property.possibleValues.find(_.name == newValueName)

    newValueOp match {
      case Some(newValue) => {
        val oldValue = currentValue
        currentValue = newValue
        if (oldValue != currentValue) {
          listener.foreach(_.onPropertyChange(property, oldValue, currentValue))
        }
        None
      }
      case None => {
        Some(new IllegalArgumentException("Value <" + newValueName + "> is not allowed for discrete property" + property.name))
      }
    }


  }

  override def possibleValues(): List[ConfigValue[T]] = property.possibleValues.toList
}

object BasicDiscreteObservableConfigProperty {

  def apply[T](property: BasicDiscreteConfigProperty[T]): ObservableDiscreteConfigProperty[T] = new BasicDiscreteObservableConfigProperty[T](property)

}
