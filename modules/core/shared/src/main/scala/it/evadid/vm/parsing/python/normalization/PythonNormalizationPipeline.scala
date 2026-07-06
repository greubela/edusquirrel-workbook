package it.evadid.vm.parsing.python.normalization

import PythonNormalizationModel.{ParsedStatementTree, RawLine}
import it.evadid.vm.parsing.python.PythonFrontendConfig

trait NormalizationStage[-In, +Out] {
  def run(input: In): Out
}

trait PythonNormalizationPipelineRunner {
  def run(source: String): PythonNormalizationPipeline.PipelineState
}

object PythonNormalizationPipeline {

  final case class PipelineState(
                                  normalizedSource: String,
                                  rawLines: List[RawLine],
                                  statementTree: ParsedStatementTree,
                                  renderedSource: String
                                )

  private final case class PipelineSeed(normalizedSource: String, rawLines: List[RawLine])
  private final case class PipelineMid(normalizedSource: String, rawLines: List[RawLine], statementTree: ParsedStatementTree)

  private final class NormalizeSourceStage extends NormalizationStage[String, String] {
    override def run(input: String): String = PythonLineNormalizationStage.normalizeLineEndingsAndDetab(input)
  }

  private final class ExtractRawLinesStage extends NormalizationStage[String, PipelineSeed] {
    override def run(input: String): PipelineSeed = PipelineSeed(input, PythonLineNormalizationStage.extractRawLines(input))
  }

  private final class BuildStatementTreeStage(defaultIndent: Int) extends NormalizationStage[PipelineSeed, PipelineMid] {
    override def run(input: PipelineSeed): PipelineMid = {
      val tree =
        if (input.rawLines.isEmpty) ParsedStatementTree(Nil, defaultIndent)
        else PythonStatementTreeBuilder.parseStatements(input.rawLines, defaultIndent)
      PipelineMid(input.normalizedSource, input.rawLines, tree)
    }
  }

  private final class RenderOutputStage extends NormalizationStage[PipelineMid, PipelineState] {
    override def run(input: PipelineMid): PipelineState = {
      val rendered =
        if (input.rawLines.isEmpty) ""
        else PythonNormalizationRenderer.renderNormalizedOutput(input.statementTree.statements, input.statementTree.indentStep)
      PipelineState(input.normalizedSource, input.rawLines, input.statementTree, rendered)
    }
  }

  final class DefaultRunner(config: PythonFrontendConfig) extends PythonNormalizationPipelineRunner {
    private val normalizeSource = new NormalizeSourceStage()
    private val extractRawLines = new ExtractRawLinesStage()
    private val buildStatementTree = new BuildStatementTreeStage(config.defaultIndent)
    private val renderOutput = new RenderOutputStage()

    override def run(source: String): PipelineState = {
      val normalized = normalizeSource.run(source)
      val raw = extractRawLines.run(normalized)
      val tree = buildStatementTree.run(raw)
      renderOutput.run(tree)
    }
  }

  def defaultRunner(config: PythonFrontendConfig): PythonNormalizationPipelineRunner =
    new DefaultRunner(config)
}
