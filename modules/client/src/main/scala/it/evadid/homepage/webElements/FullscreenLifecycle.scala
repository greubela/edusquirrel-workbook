package it.evadid.homepage.webElements

trait FullscreenLifecycle {
  def onFullscreenOpen(): Unit = ()
  def onFullscreenClose(): Unit
}
