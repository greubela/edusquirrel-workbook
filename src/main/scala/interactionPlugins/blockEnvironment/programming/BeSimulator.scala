package interactionPlugins.blockEnvironment.programming


trait BeSimulatorConfig{}
trait BeSimulatorState{}
trait BeSimulatorErros{}
trait BeSimulatorWarnings{}

case class BeSimulator(config: BeSimulatorConfig) {

    def getSimulatorStates(): List[BeSimulatorState] = ???
    def getSimulatorErrors(): List[BeSimulatorErros] = ???
    def beSimulatorWarnings(): List[BeSimulatorWarnings] = ???
  
}
