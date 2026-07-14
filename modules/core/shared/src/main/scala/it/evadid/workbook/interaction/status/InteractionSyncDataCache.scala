package it.evadid.workbook.interaction.status

import it.evadid.util.logging.Logger
import it.evadid.core.datastructures.storage.RemoteSyncDataCache.{RemoteDataReader, SyncStatus}
import it.evadid.workbook.interaction.sync.SyncContext
import it.evadid.workbook.interaction.sync.SyncInformation.{SyncCache, SyncInformationWithContext}
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistorySerialized}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

