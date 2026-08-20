package it.evadid.homepage.webElements

trait FullscreenLifecycle {
  def onFullscreenOpen(): Unit = ()
  def onFullscreenClose(): Unit

  /** When false, backdrop / outside clicks do not dismiss fullscreen (X / Close still work). */
  def dismissOnOutsideClick: Boolean = true
}
