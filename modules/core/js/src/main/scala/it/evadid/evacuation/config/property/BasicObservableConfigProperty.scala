package it.evadid.evacuation.config.property

import it.evadid.evacuation.config.property.ObservableConfigProperty.PropertyListener
import it.evadid.evacuation.config.value.{ConfigValue, SimpleConfigValue}

import scala.collection.mutable

class BasicObservableConfigProperty[T](prop: ConfigProperty[T], valueFactory: String => Either[T, Exception]) extends ObservableConfigProperty[T] {

  private var currentValue: ConfigValue[T] = property.defaultValue

  override val listener: mutable.ListBuffer[PropertyListener[T]] = mutable.ListBuffer(
    //(a, b, c) => println(property.name + " changed: " + b.name + " -> " + c.name)
  )

  override def getValue: ConfigValue[T] = currentValue

  override def setValue(newValue: T): Unit = {

    val oldValue = currentValue
    currentValue = new SimpleConfigValue(newValue)

    if (oldValue != currentValue) {
      listener.foreach(_.onPropertyChange(property, oldValue, currentValue))
    }
  }

  def setValue(newValue: String): Option[Exception] = try {

    val newCalculatedValue = valueFactory.apply(newValue)

    newCalculatedValue match {
      case Left(calculatedValue) => {
        setValue(calculatedValue)
        None
      }
      case Right(exception) => {
        println("[WARNING] new value <" + newValue + "> ignored for config " + prop.name + ": " + exception.getMessage)
        Some(exception)
      }
    }

  } catch {
    case ex: Exception => Some(ex)
  }

  override def property: ConfigProperty[T] = prop
}
