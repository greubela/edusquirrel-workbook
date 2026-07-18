package it.evadid.evacuation.eva2.configuration.ui

import it.evadid.core.datastructures.matrix.Direction

case class PersonDrawingInformation(directionToMove: Direction, doesMoveInCurrentStep: Boolean, toMoveInStep: Boolean)
