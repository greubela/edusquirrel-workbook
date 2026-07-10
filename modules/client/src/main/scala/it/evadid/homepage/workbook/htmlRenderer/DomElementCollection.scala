package it.evadid.homepage.workbook.htmlRenderer

import com.raquo.laminar.api.L.*

sealed trait DomElementCollection {

  lazy val allElementsSignal: Signal[List[Element]]

}

object DomElementCollection {

  implicit class DomElementSingle(element: Element) extends DomElementCollection {
    override lazy val allElementsSignal: Signal[List[Element]] = Signal.fromValue(List(element))
  }

  implicit class DomElementList(elements: List[Element]) extends DomElementCollection {
    override lazy val allElementsSignal: Signal[List[Element]] = Signal.fromValue(elements)
  }

  implicit class DomElementSignal(elementSignal: Signal[Element]) extends DomElementCollection {
    override lazy val allElementsSignal: Signal[List[Element]] = elementSignal.map(element => List(element))
  }

  implicit class DomElementSignalList(elementsSignal: Signal[List[Element]]) extends DomElementCollection {
    override lazy val allElementsSignal: Signal[List[Element]] = elementsSignal
  }


}

