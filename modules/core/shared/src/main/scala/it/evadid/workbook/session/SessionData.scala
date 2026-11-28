package it.evadid.workbook.session

import it.evadid.core.datastructures.user.{User, UserConfig}
import it.evadid.core.util.InfoUtil
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.sync.SyncContext
import it.evadid.workbook.sync.model.InteractionHistoryJson
import upickle.default.*

import java.time.Instant

/** A learner's saved session: identity, public configuration, workbook definition and format-coded histories. */
case class SessionData(
                        timestampStorage: Instant,
                        knownUsageData: Map[SyncContext, InteractionHistoryJson],
                        userInfo: User,
                        userConfig: Option[UserConfig],
                        workbookInfo: AllWorkbookInfo) derives ReadWriter {
  /** The workbook to continue: the open one when it is this session's workbook, else the copy stored in the file. */
  def workbookToContinue(open: Option[AllWorkbookInfo]): AllWorkbookInfo =
    open.filter(_.loadedWorkbook.workbookId == workbookInfo.loadedWorkbook.workbookId).getOrElse(workbookInfo)

  /** Sessions leave the browser only without destination credentials. */
  def withoutCredentials: SessionData = copy(userConfig = userConfig.map(_.withoutCredentials))

  /** Download name; workbook ids may contain path separators. */
  def fileName: String =
    InfoUtil.downloadName(s"eva ${userInfo.id} ${workbookInfo.loadedWorkbook.elementId}", timestampStorage) + ".eva"
}
