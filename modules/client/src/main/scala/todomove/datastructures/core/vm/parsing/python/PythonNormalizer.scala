package todomove.datastructures.core.vm.parsing.python

import todomove.datastructures.core.vm.parsing.python.normalization.PythonNormalizationModel.{ParsedStatementTree, RawLine}
import todomove.datastructures.core.vm.parsing.python.normalization.{PythonLineNormalizationStage, PythonNormalizationPipeline, PythonNormalizationPipelineRunner, PythonNormalizationRenderer, PythonStatementTreeBuilder}

class PythonNormalizer(
                        config: PythonFrontendConfig = PythonFrontendConfig.default,
                        pipelineRunnerOverride: Option[PythonNormalizationPipelineRunner] = None
                      ) {
  private val pipelineRunner = pipelineRunnerOverride.getOrElse(PythonNormalizationPipeline.defaultRunner(config))

  def normalizeLineEndings(source: String): String =
    PythonLineNormalizationStage.normalizeLineEndingsAndDetab(source)

  def normalizePython(source: String): String = {
    runPipeline(source).renderedSource
  }

  def runPipeline(source: String): PythonNormalizationPipeline.PipelineState =
    pipelineRunner.run(source)

  def extractRawLines(normalizedAndDetabbed: String): List[RawLine] =
    PythonLineNormalizationStage.extractRawLines(normalizedAndDetabbed)

  def parseStatements(rawLines: List[RawLine]): ParsedStatementTree =
    PythonStatementTreeBuilder.parseStatements(rawLines, config.defaultIndent)

  def renderNormalizedOutput(statementTree: ParsedStatementTree): String =
    PythonNormalizationRenderer.renderNormalizedOutput(statementTree.statements, statementTree.indentStep)
}

object PythonNormalizer {
  def default: PythonNormalizer = {
    new PythonNormalizer(PythonFrontendConfig.default)
  }
}
