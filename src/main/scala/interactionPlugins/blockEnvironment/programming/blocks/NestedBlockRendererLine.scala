package interactionPlugins.blockEnvironment.programming.blocks

import contentmanagement.webElements.svg.shapes.BeShape

case class NestedBlockRendererLine(
                                    mainShape: BeShape,
                                    infoShapes: List[BeShape],
                                    navShapes: List[BeShape],
                                    sideEffectShapes: List[BeShape]
                                  ) {

}