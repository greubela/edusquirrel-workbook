package it.evadid.homepage.workbook.legacy.model.abstractions

import com.raquo.laminar.api.L.Element
import it.evadid.homepage.workbook.legacy.model.info.FullInfo
import it.evadid.workbook.model.abstractions.WorkbookElement
import it.evadid.workbook.model.interaction.WorkbookInteraction

import scala.concurrent.{ExecutionContext, Future}

/*
case class BasicHtmlWorkbookElement[T <: WorkbookElement](workbookElement: WorkbookElement, fullInfo: FullInfo, domElement: Element) extends HtmlWorkbookElement {

  /*def loadScaffoldingInformation(languageMapIdExerciseText: String, languageMapIdAdditionalHints: String): Future[ScaffoldingInformation[T]] = {
    workbookElement match {
      case int: WorkbookInteraction[T] =>
        val fut1 = fullInfo.technical.languageMapStorage.loadAsFuture(languageMapIdExerciseText)(using ExecutionContext.global)
        val fut2 = fullInfo.technical.languageMapStorage.loadAsFuture(languageMapIdAdditionalHints)(using ExecutionContext.global)
        fut1.zip(fut2).map { case (res1, res2) => ScaffoldingInformation[T](int, res1, res2) }(using ExecutionContext.global)
      case _ =>
        Future.failed(new IllegalArgumentException("This is not an interaction"))
    }
  }*/


}*/