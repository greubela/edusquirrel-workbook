package it.evadid.evacuation.config.property

import it.evadid.evacuation.config.property.ObservableConfigProperty.PropertyListener
import it.evadid.evacuation.config.value.ConfigValue
import it.evadid.evacuation.core.utility.GeneralUtility

import scala.collection.mutable


trait ObservableConfigProperty[T] {
  def property: ConfigProperty[T]

  def getValue: ConfigValue[T]

  def setValue(newValue: T): Unit

  val listener: mutable.ListBuffer[PropertyListener[T]]
}


object ObservableConfigProperty {

  trait PropertyListener[T] {

    def onPropertyChange(property: ConfigProperty[T], oldValue: ConfigValue[T], newValue: ConfigValue[T]): Unit

  }

  def apply[T](property: ConfigProperty[T], valueFactory: String => Either[T, Exception]): BasicObservableConfigProperty[T] = new BasicObservableConfigProperty[T](property, valueFactory)

  def apply(property: ConfigProperty[Integer]): BasicObservableConfigProperty[Integer] = new BasicObservableConfigProperty[Integer](property, GeneralUtility.factoryToEitherFactory(str => Integer.parseInt(str)))

  def fromIntProperty(property: ConfigProperty[Int]):  BasicObservableConfigProperty[Int] = new BasicObservableConfigProperty[Int](property, GeneralUtility.factoryToEitherFactory(str => str.toInt))

  def fromLongProperty(property: ConfigProperty[Long]): BasicObservableConfigProperty[Long] = new BasicObservableConfigProperty[Long](property, GeneralUtility.factoryToEitherFactory(str => str.toLong))

  def createWithoutStringFactory[T](property: ConfigProperty[T]): BasicObservableConfigProperty[T] = new BasicObservableConfigProperty[T](property, null)


}
