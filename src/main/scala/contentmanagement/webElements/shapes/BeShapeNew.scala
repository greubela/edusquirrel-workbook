package contentmanagement.webElements.shapesNew

import contentmanagement.model.geometry.Dimension
import contentmanagement.webElements.shapes.meta.*
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.config.BeRenderingConfig


case class BeShapeNew[T: Fractional](
                                      shapeInfo: ShapeInfo[T],
                                      shapeLogic: ShapeLogic[T]
                                    ) {

  def renderToMinSize(renderingInfo: BeRenderingConfig): AppSvgElement = {
    shapeLogic.renderToMinSize(shapeInfo, renderingInfo)
  }

  def renderWithMaxSize(renderingInfo: BeRenderingConfig, maxSize: Dimension[T]): AppSvgElement = {
    shapeLogic.renderWithMaxSize(shapeInfo, renderingInfo, maxSize)
  }

  def addAugments(augments: AugmentInformation[T]): BeShapeNew[T] = {
    val newAugs: AugmentInformation[T] = shapeInfo.augInfo.combineWith(augments)
    val newShapeInfo: ShapeInfo[T] = shapeInfo.copy(augInfo = newAugs)
    this.copy(shapeInfo = newShapeInfo)
  }

  /*
  



    def


  }


  trait ShapePositionable {

  }

  trait ShapeResizable {
    def setSize(dim: Dimension[Double]):
  }

  trait ShapeDynamic {
    def preferredDim(): Dimension[Double]

    def render(bounds: Bounds[Double]): ShapeRendered
  }

  trait ShapeContainer extends ShapeDynamic {

  }

  trait ShapeFixedSize {
    def shapeDim(): Dimension[Double]

    def render(position: Point[Double]): ShapeRendered
  }

  trait ShapeRendered {
    def bounds: Bounds[Double]
  }


  Path:
    -can be repositioned
      - size cannot be changed

*/

}