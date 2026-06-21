package it.evadid.evacuation.eva1.algorithm.routing

import it.evadid.evacuation.core.algorithm.routing.model.RoutingOption
import it.evadid.evacuation.core.datastructures.maps.MultiHashMapList
import it.evadid.evacuation.eva1.algorithm.events.eventtypes.{PersonFinishedEvent, PersonInsertedEvent, PersonReceivedEvent, PersonSentEvent}
import it.evadid.evacuation.eva1.algorithm.events.traits.PersonEvent
import it.evadid.evacuation.eva1.algorithm.routing.FlowRoutingMap.FlowRoutingMap
import it.evadid.evacuation.eva1.model.evagraph.{Person, Router}

import scala.collection.mutable

case class EvacuationState(curPositionsInState: PositionStateMap, persons: Set[Person], routingMap: FlowRoutingMap, currenTimestamp: Long, handledEvents: List[PersonEvent], remainingEvents: Set[PersonEvent]) {

  def lastEventMap(): Map[Person, PersonEvent] = {
    val lastActivityMap = mutable.HashMap[Person, PersonEvent]()
    persons.foreach(
      curPerson => handledEvents.filter(_.person == curPerson)
        .maxByOption(_.timestampInMs)
        .foreach(lastActivityMap.put(curPerson, _)
        ))

    lastActivityMap.toMap
  }

  def getSafePersons: MultiHashMapList[Router, Person] = {
    val res = new MultiHashMapList[Router, Person]()
    handledEvents.filter(_.isInstanceOf[PersonFinishedEvent]).map(_.asInstanceOf[PersonFinishedEvent]).foreach(pfe => res.addElement((pfe.router, pfe.person)))
    res
  }

  def calculateNextState(evacuationStrategy: FlowStrategy): Option[EvacuationState] = {
    var tryToSend: Option[(Person, RoutingOption[Router])] = curPositionsInState.tryToSendPerson(routingMap, evacuationStrategy)
    if (tryToSend.isDefined) {
      Some(handleRoutingOption(tryToSend.get._1, tryToSend.get._2))
    } else if (remainingEvents.nonEmpty) {
      Some(handleEvent(remainingEvents.minBy(_.eventStartTimestamp)))
    } else {
      None
    }
  }

  private def handleRoutingOption(person: Person, routingOption: RoutingOption[Router]): EvacuationState = {
    val edge = curPositionsInState.graph.dirEdgesBetween(routingOption.curPos, routingOption.nextStep.get).head
    val sentEvent = PersonSentEvent(person, edge, curPositionsInState.graph, currenTimestamp, -1)
    handleEvent(sentEvent)
  }

  private def handleEvent(event: PersonEvent): EvacuationState = {
    val newState = curPositionsInState.handleEventMovement(event)
    val followingEvents = calculateFollowingEvents(event)
    val newRemainingEvents = (remainingEvents - event) ++ followingEvents
    //  println("handle Event: " + event + ", remaining Events: " + newRemainingEvents.size + " (head: " + newRemainingEvents.headOption + ")")
    EvacuationState(newState, persons, routingMap, event.eventStartTimestamp, handledEvents ++ Some(event), newRemainingEvents)
  }

  private def calculateFollowingEvents(event: PersonEvent): List[PersonEvent] = {
    val res = new mutable.ListBuffer[PersonEvent]()
    event match {
      case PersonInsertedEvent(person, router, graph, eventTimestamp, simulationStartedTimestampInMs) => {}
      case PersonFinishedEvent(person, router, graph, eventTimestamp, simulationStartedTimestampInMs) => {}
      case PersonReceivedEvent(person, edge, graph, eventTimestamp, simulationStartedTimestampInMs) => {
        if (edge.dest.isExit) {
          res += PersonFinishedEvent(person, edge.dest, graph, eventTimestamp, simulationStartedTimestampInMs)
        }
      }
      case PersonSentEvent(person, edge, graph, eventTimestamp, simulationStartedTimestampInMs) => {
        res += PersonReceivedEvent(person, edge, graph, eventTimestamp + edge.content.delayInMs, simulationStartedTimestampInMs)
      }
    }
    res.toList
  }


}
