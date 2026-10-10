package it.evadid.evacuation.eva1.model.evagraph

import upickle.default.ReadWriter


case class EvaPerson(seed: Int, possibleDestinations: Seq[Router]) derives ReadWriter

object EvaPerson{



}

