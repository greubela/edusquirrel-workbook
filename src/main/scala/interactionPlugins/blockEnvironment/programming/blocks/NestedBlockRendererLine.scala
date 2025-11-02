package interactionPlugins.blockEnvironment.programming.blocks

import interactionPlugins.blockEnvironment.programming.shapes.BeShape

case class NestedBlockRendererLine(
                                    mainShape: BeShape,
                                    infoShapes: List[BeShape],
                                    navShapes: List[BeShape],
                                    sideEffectShapes: List[BeShape]
                                  ) {

}