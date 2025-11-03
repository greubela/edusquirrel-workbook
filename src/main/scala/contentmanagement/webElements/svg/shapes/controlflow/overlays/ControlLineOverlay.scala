package contentmanagement.webElements.svg.shapes.controlflow.overlays

import contentmanagement.model.geometry.Bounds
import contentmanagement.webElements.svg.SvgPathBuilder
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

trait ControlLineOverlay {

  def getOverlayPath(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double]

}
