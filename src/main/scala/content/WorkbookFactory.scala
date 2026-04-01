package content

import com.raquo.laminar.api.L
import workbook.model.Workbook
import workbook.model.info.{AllWorkbookInfo, WorkbookInfo}

trait WorkbookFactory {

  private var id = 0

  protected def nextId(prefix: String = "auto-id"): String = {
    id = id + 1
    prefix + "-" + id
  }

  def workbookInfo: AllWorkbookInfo
  def workbookInfoVar: L.Var[WorkbookInfo] = workbookInfo.workbookInfoVar

  def createWorkbook: Workbook


}
