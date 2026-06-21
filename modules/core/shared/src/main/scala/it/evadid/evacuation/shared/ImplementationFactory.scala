package it.evadid.evacuation.shared

import it.evadid.evacuation.core.io.traits.converter.Converter

trait ImplementationFactory {

  def getZipConverter(): Converter[Array[Byte]]

}
