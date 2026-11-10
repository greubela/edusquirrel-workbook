package it.evadid.homepage.control.change

import it.evadid.core.datastructures.language.AppLanguage.English
import it.evadid.core.datastructures.language.control.LanguageMapStorage
import it.evadid.homepage.control.model.{AllDisplayInfo, FullInfo, HomepageInfo}
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.interaction.variable.InteractionVariable
import it.evadid.workbook.meta.InteractionVariableSyncReport
import munit.FunSuite
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class RemoteInteractionCacheControlSpec extends FunSuite {
  private def emptyInfo: FullInfo = FullInfo(HomepageInfo(English, None, None,
    AllDisplayInfo(false, None), LanguageMapStorage.empty))

  test("batch loading propagates a failure from an asynchronous per-variable load") {
    val failure = IllegalStateException("history report failed")
    val control = new RemoteInteractionCacheControl(emptyInfo) {
      override def createCurrentReport[T, GA, SA](variable: InteractionVariable[T, GA, SA]): InteractionVariableSyncReport[T, GA, SA] =
        throw failure
    }
    val result: Future[Unit] = control.load(List(InteractionVariable(TextInteraction("answer"))))
    result.failed.map(error => assert(error eq failure))
  }

  test("an empty batch completes without accessing remote caches") {
    val control = RemoteInteractionCacheControl(emptyInfo)
    val result: Future[Unit] = control.load(Nil)
    result.map(value => assertEquals(value, ()))
  }
}
