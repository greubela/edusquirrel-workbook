package it.evadid.workbook.reports

/** Confirmation requires the latest answer and all associated feedback events at the destination. */
case class InteractionSyncReport[RW](allSyncDestinations: Set[RW], destinationsWithSufficientWrites: Set[RW])
