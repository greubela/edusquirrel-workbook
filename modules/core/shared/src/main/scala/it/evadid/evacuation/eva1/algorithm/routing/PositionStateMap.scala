package it.evadid.evacuation.eva1.algorithm.routing

import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes._
import it.evadid.evacuation.core.algorithm.routing.model.RoutingOption
import it.evadid.core.datastructures.graph.Edge
import it.evadid.evacuation.core.datastructures.maps.MultiHashMapList
import it.evadid.evacuation.eva1.algorithm.events.eventtypes.{PersonFinishedEvent, PersonInsertedEvent, PersonReceivedEvent, PersonSentEvent}
import it.evadid.evacuation.eva1.algorithm.events.traits.PersonEvent
import it.evadid.evacuation.eva1.algorithm.routing.FlowRoutingMap.FlowRoutingMap
import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.{EvaEdge, EvaGraph, RouterOrEdge}
import it.evadid.evacuation.eva1.model.evagraph.{ConnectionInfo, EvaGraphTypes, Person, Router}

import scala.collection.mutable

case class PositionStateMap(graph: EvaGraph, positionStateMap: MultiHashMapList[RouterOrEdge, Person]) {

  def edgesMap(): MultiHashMapList[EvaEdge, Person] = positionStateMap.getCopyWithFilteredKeys(_.getEither().isRight).getCopyWithMappedKeys(_.getEither().getOrElse(throw new NoSuchElementException("Expected edge position")))

  def capacityMap(): Map[EvaEdge, CapacityInformation] = {
    val res = mutable.HashMap[EvaEdge, CapacityInformation]()
    graph.edges.map(_.asInstanceOf[EvaEdge]).foreach((edge: EvaEdge) => {
      res.put(edge, capacityInformationForEdge(edge.start, edge.dest))
    })
    res.toMap
  }

  def capacityMapDirected(): Map[EvaEdge, CapacityInformation] = {
    val res = mutable.HashMap[EvaEdge, CapacityInformation]()
    graph.edges.map(_.asInstanceOf[EvaEdge]).foreach((edge: EvaEdge) => {
      res.put(edge, capacityInformationForEdgeDirected(edge.start, edge.dest))
    })
    res.toMap
  }

  def routerMap(): MultiHashMapList[Router, Person] = positionStateMap.getCopyWithFilteredKeys(_.getEither().isLeft).getCopyWithMappedKeys(_.getEither().swap.getOrElse(throw new NoSuchElementException("Expected router position")))

  def getPersonAtPositions(pos: RouterOrEdge): Seq[Person] = {
    positionStateMap.get(pos).get.toSeq
  }

  private def capacityInformationForEdge(router1: Router, router2: Router): CapacityInformation = {
    val personsAtEdges: mutable.ListBuffer[Person] = new mutable.ListBuffer[Person]

    val edges: List[EvaEdge] = graph.allEdgesBetween(router1, router2).toList
    edges.flatMap(edge => positionStateMap.get(EvaGraphTypes.edgeToEither(edge))).foreach(personsAtEdges.addAll(_))

    val minParallelism = edges.minBy(_.content.maxParallelism).content.maxParallelism
    CapacityInformation(personsAtEdges.toList, minParallelism)
  }

  private def capacityInformationForEdgeDirected(router1: Router, router2: Router): CapacityInformation = {
    val personsAtEdges: mutable.ListBuffer[Person] = new mutable.ListBuffer[Person]

    val edges: List[EvaEdge] = graph.dirEdgesBetween(router1, router2).toList
    edges.flatMap(edge => positionStateMap.get(EvaGraphTypes.edgeToEither(edge))).foreach(personsAtEdges.addAll(_))

    val minParallelism = edges.minBy(_.content.maxParallelism).content.maxParallelism
    CapacityInformation(personsAtEdges.toList, minParallelism)
  }


  def tryToSendPerson(routingMap: FlowRoutingMap, evacuationStrategy: FlowStrategy): Option[(Person, RoutingOption[Router])] = {
    val entitiesAtRouter: Set[(Router, Person)] = positionStateMap.getAllEntries.filter(_._1.getEither().isLeft).map(tup => (tup._1.getEither().swap.getOrElse(throw new NoSuchElementException("Expected router position")), tup._2)).filterNot(_._1.isExit)

    val entities: mutable.HashSet[(Router, Person)] = new mutable.HashSet[(Router, Person)]()
    entities.addAll(entitiesAtRouter)

    var nextStep: Option[(Person, RoutingOption[Router])] = None
    while (nextStep.isEmpty && entities.nonEmpty) {
      val tryToRouteEntry = entities.head
      entities -= tryToRouteEntry

      nextStep = tryToRoute(tryToRouteEntry._2, tryToRouteEntry._1, routingMap, evacuationStrategy)
    }
    nextStep
  }

  private def tryToRoute(person: Person, currentPositionOfPerson: Router, routingMap: FlowRoutingMap, evacuationStrategy: FlowStrategy): Option[(Person, RoutingOption[Router])] = {

    val allRoutingOptions: mutable.Seq[RoutingOption[Router]] = routingMap.getMap(currentPositionOfPerson).filter(_.nextStep.isDefined)

    val freeRoutingOptions = allRoutingOptions.filter(option => {
      val capacityInformation = capacityInformationForEdge(currentPositionOfPerson, option.nextStep.get)
      capacityInformation.onPosition.size < capacityInformation.maxCapacity
    })

    if (freeRoutingOptions.nonEmpty) {
      evacuationStrategy.decideRouting(allRoutingOptions.toSeq, freeRoutingOptions.toSeq).map((person, _))
    } else {
      None
    }
  }


  def handleEventMovement(event: PersonEvent): PositionStateMap = {
    val res = positionStateMap.getCopy

    event match {
      case PersonInsertedEvent(person, router, graph, eventTimestamp, simulationStartedTimestampInMs) => {
        res.addElement(EvaGraphTypes.routerToEither(router), person)
      }
      case PersonReceivedEvent(person, edge, graph, eventTimestamp, simulationStartedTimestampInMs) => {
        res.removeElement(EvaGraphTypes.edgeToEither(edge), person)
        res.addElement(EvaGraphTypes.routerToEither(edge.dest), person)
      }
      case PersonSentEvent(person, edge, graph, eventTimestamp, simulationStartedTimestampInMs) => {
        res.removeElement(EvaGraphTypes.routerToEither(edge.start), person)
        res.addElement(EvaGraphTypes.edgeToEither(edge), person)
      }
      case PersonFinishedEvent(person, router, graph, eventTimestamp, simulationStartedTimestampInMs) => {
        res.removeElement(EvaGraphTypes.routerToEither(router), person)
      }
      case _: PersonEvent => ???
    }

    PositionStateMap(graph, res)
  }


}

object PositionStateMap {


  def getEmpty(graph: EvaGraph): PositionStateMap = PositionStateMap(graph, new MultiHashMapList[RouterOrEdge, Person])

}