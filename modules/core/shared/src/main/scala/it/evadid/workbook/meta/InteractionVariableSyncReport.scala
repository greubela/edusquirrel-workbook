package it.evadid.workbook.meta

/** Confirmation reflects the destination's configured event strategy. */
case class InteractionVariableSyncReport[RW](allSyncDestinations: Set[RW], destinationsWithSufficientWrites: Set[RW])
