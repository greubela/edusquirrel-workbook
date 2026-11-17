package it.evadid.homepage.workbook.htmlRenderer

import com.raquo.laminar.api.L.*

sealed trait DomElementCollection {

  lazy val allElementsSignal: Signal[Seq[Element]]

}

object DomElementCollection {

  implicit class DomElementSingle(element: Element) extends DomElementCollection {
    override lazy val allElementsSignal: Signal[Seq[Element]] = Signal.fromValue(List(element))
  }

  implicit class DomElementList(elements: Seq[Element]) extends DomElementCollection {
    override lazy val allElementsSignal: Signal[Seq[Element]] = Signal.fromValue(elements)
  }

  implicit class DomElementSignal(elementSignal: Signal[Element]) extends DomElementCollection {
    override lazy val allElementsSignal: Signal[Seq[Element]] = elementSignal.map(element => List(element))
  }

  implicit class DomElementSignalList(elementsSignal: Signal[Seq[Element]]) extends DomElementCollection {
    override lazy val allElementsSignal: Signal[Seq[Element]] = elementsSignal
  }

  implicit class DomEventStreamSingle(eventStream: EventStream[Element]) extends DomElementCollection {
    override lazy val allElementsSignal: Signal[Seq[Element]] = eventStream.startWith(div()).map(cur => List(cur))
  }

  implicit class DomEventStreamList(eventStream: EventStream[Seq[Element]]) extends DomElementCollection {
    override lazy val allElementsSignal: Signal[Seq[Element]] = eventStream.startWith(List())
  }


}

