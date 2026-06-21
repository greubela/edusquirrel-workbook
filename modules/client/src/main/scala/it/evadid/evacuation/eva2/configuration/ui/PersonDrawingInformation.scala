package it.evadid.evacuation.eva2.configuration.ui

import it.evadid.evacuation.core.datastructures.Direction

case class PersonDrawingInformation(directionToMove: Direction, doesMoveInCurrentStep: Boolean, toMoveInStep: Boolean)
