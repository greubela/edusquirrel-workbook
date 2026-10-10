package it.evadid.evacuation.eva1.algorithm.events.traits

import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.EvaGraph
import it.evadid.evacuation.eva1.model.evagraph.{ObservableEvaGraphModel, EvaPerson}

trait PersonEvent extends Event {
  def person: EvaPerson
  def eventStartTimestamp: Long
  def simulationStartedTimestampInMs: Long
  def graph: EvaGraph
}

object PersonEvent {
  import it.evadid.evacuation.eva1.algorithm.events.eventtypes.*
  private case class StoredEvent(kind: String, payload: ujson.Value) derives upickle.default.ReadWriter

  given upickle.default.ReadWriter[PersonEvent] = upickle.default.readwriter[StoredEvent].bimap(
    event => event match {
      case value: PersonInsertedEvent => StoredEvent("inserted", upickle.default.writeJs(value))
      case value: PersonSentEvent => StoredEvent("sent", upickle.default.writeJs(value))
      case value: PersonReceivedEvent => StoredEvent("received", upickle.default.writeJs(value))
      case value: PersonFinishedEvent => StoredEvent("finished", upickle.default.writeJs(value))
      case other => throw new IllegalArgumentException(s"Unsupported person event: ${other.getClass.getName}")
    }, stored => stored.kind match {
      case "inserted" => upickle.default.read[PersonInsertedEvent](stored.payload)
      case "sent" => upickle.default.read[PersonSentEvent](stored.payload)
      case "received" => upickle.default.read[PersonReceivedEvent](stored.payload)
      case "finished" => upickle.default.read[PersonFinishedEvent](stored.payload)
      case other => throw new IllegalArgumentException(s"Unknown person event: $other")
    })
}



