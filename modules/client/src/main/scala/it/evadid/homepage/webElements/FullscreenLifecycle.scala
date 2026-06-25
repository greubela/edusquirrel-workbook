package it.evadid.homepage.webElements

/** Optional hook for elements shown in the fullscreen overlay. */
trait FullscreenLifecycle {
  def onFullscreenClose(): Unit = ()
}
