package it.evadid.server.commandHandler.sql

import it.evadid.util.Logger
import it.evadid.workbook.model.interaction.sync.SyncFormatter.RichInteractionVariableFormatter
import it.evadid.workbook.model.interaction.sync.UsageContext

import java.sql.Connection

case class UpsertToDatabase(
                              connection: Connection,
                              context: UsageContext,
                              logger: Logger,
                              formatter: RichInteractionVariableFormatter
                            ) {



}
