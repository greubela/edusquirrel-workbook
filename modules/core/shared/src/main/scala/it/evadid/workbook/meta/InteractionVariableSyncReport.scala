package it.evadid.workbook.meta

/** Confirmation requires the latest answer and all associated feedback events at the destination. */
case class InteractionVariableSyncReport[RW](allSyncDestinations: Set[RW], destinationsWithSufficientWrites: Set[RW])
