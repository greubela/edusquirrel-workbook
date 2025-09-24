package interactionPlugins.turtleEnvironment

import scala.collection.mutable

object TurtleSegmentMatcher {

  case class Settings(
    angleToleranceDegrees: Double,
    distanceTolerance: Double,
    minOverlapLength: Double,
    minSegmentLength: Double,
    trimEpsilon: Double
  )

  val DefaultSettings: Settings =
    Settings(angleToleranceDegrees = 6.0, distanceTolerance = 6.0, minOverlapLength = 0.2, minSegmentLength = 0.05, trimEpsilon = 0.05)

  case class MatchResult(
    overlapSegments: List[TurtleLineSegment],
    expectedOnlySegments: List[TurtleLineSegment],
    actualOnlySegments: List[TurtleLineSegment]
  )

  def canonicalize(segments: List[TurtleLineSegment], settings: Settings = DefaultSettings): List[TurtleLineSegment] =
    segments.filter(_.length >= settings.minSegmentLength)

  def matchSegments(
    expectedSegments: List[TurtleLineSegment],
    actualSegments: List[TurtleLineSegment],
    settings: Settings = DefaultSettings
  ): MatchResult = {
    val filteredExpected = canonicalize(expectedSegments, settings)
    val filteredActual = canonicalize(actualSegments, settings)

    val candidates = buildCandidates(filteredExpected, filteredActual, settings)
    val matches = selectMatches(candidates)

    val overlapSegments = matches.flatMap(_.overlapSegments)
    val expectedRemainders = matches.flatMap(_.expectedRemainders)
    val actualRemainders = matches.flatMap(_.actualRemainders)

    val matchedExpectedIndices = matches.map(_.expectedIndex).toSet
    val matchedActualIndices = matches.map(_.actualIndex).toSet

    val unmatchedExpected = filteredExpected.zipWithIndex.collect { case (segment, index) if !matchedExpectedIndices.contains(index) => segment }
    val unmatchedActual = filteredActual.zipWithIndex.collect { case (segment, index) if !matchedActualIndices.contains(index) => segment }

    def trimSmall(segments: List[TurtleLineSegment]) = segments.filter(_.length >= settings.trimEpsilon)

    MatchResult(
      trimSmall(overlapSegments),
      trimSmall(expectedRemainders ++ unmatchedExpected),
      trimSmall(actualRemainders ++ unmatchedActual)
    )
  }

  private case class CandidateMatch(
    expectedIndex: Int,
    actualIndex: Int,
    cost: Double,
    overlapSegments: List[TurtleLineSegment],
    expectedRemainders: List[TurtleLineSegment],
    actualRemainders: List[TurtleLineSegment]
  )

  private case class OverlapPieces(
    overlapLength: Double,
    overlapSegments: List[TurtleLineSegment],
    expectedRemainders: List[TurtleLineSegment],
    actualRemainders: List[TurtleLineSegment]
  )

  private def buildCandidates(
    expected: List[TurtleLineSegment],
    actual: List[TurtleLineSegment],
    settings: Settings
  ): List[CandidateMatch] = {
    val buffer = mutable.ListBuffer.empty[CandidateMatch]
    expected.zipWithIndex.foreach { case (expectedSegment, eIndex) =>
      actual.zipWithIndex.foreach { case (actualSegment, aIndex) =>
        buildCandidate(expectedSegment, actualSegment, eIndex, aIndex, settings).foreach(buffer += _)
      }
    }
    buffer.toList.sortBy(_.cost)
  }

  private def buildCandidate(
    expected: TurtleLineSegment,
    actual: TurtleLineSegment,
    expectedIndex: Int,
    actualIndex: Int,
    settings: Settings
  ): Option[CandidateMatch] = {
    if (expected.length <= settings.trimEpsilon || actual.length <= settings.trimEpsilon) {
      None
    } else {
      val angleDifference = expected.angleTo(actual)
      if (angleDifference > settings.angleToleranceDegrees) {
        None
      } else {
        val distance = expected.distanceToSegment(actual)
        if (distance > settings.distanceTolerance) {
          None
        } else {
          computeOverlap(expected, actual, settings).map { pieces =>
            val cost = 1.0 - (pieces.overlapLength / math.max(expected.length, actual.length))
            CandidateMatch(expectedIndex, actualIndex, cost, pieces.overlapSegments, pieces.expectedRemainders, pieces.actualRemainders)
          }
        }
      }
    }
  }

  private def computeOverlap(
    expected: TurtleLineSegment,
    actual: TurtleLineSegment,
    settings: Settings
  ): Option[OverlapPieces] = {
    val expectedLength = expected.length
    val actualLength = actual.length
    if (expectedLength <= settings.trimEpsilon || actualLength <= settings.trimEpsilon) {
      return None
    }

    val expectedUnit = expected.normalizedDirection
    val actualUnit = actual.normalizedDirection
    val needsFlip = expectedUnit.dot(actualUnit) < 0.0
    val (alignedStart, alignedEnd) = if (needsFlip) (actual.end, actual.start) else (actual.start, actual.end)
    val alignedVector = alignedEnd - alignedStart
    val alignedLength = alignedVector.magnitude
    val alignedUnit = if (alignedLength == 0.0) actualUnit else alignedVector.normalized

    def projectOntoExpected(point: TurtlePoint): Double = (point - expected.start).dot(expectedUnit)
    val startProjection = projectOntoExpected(alignedStart)
    val endProjection = projectOntoExpected(alignedEnd)
    val projectionMin = math.min(startProjection, endProjection)
    val projectionMax = math.max(startProjection, endProjection)

    val overlapStart = clamp(math.max(0.0, projectionMin), 0.0, expectedLength)
    val overlapEnd = clamp(math.min(expectedLength, projectionMax), 0.0, expectedLength)
    val overlapLength = overlapEnd - overlapStart
    if (overlapLength <= settings.trimEpsilon) {
      return None
    }

    val expectedOverlapStart = expected.start + expectedUnit.scale(overlapStart)
    val expectedOverlapEnd = expected.start + expectedUnit.scale(overlapEnd)

    def projectOntoActual(point: TurtlePoint): Double = (point - alignedStart).dot(alignedUnit)
    val startParam = clamp(projectOntoActual(expectedOverlapStart), 0.0, alignedLength)
    val endParam = clamp(projectOntoActual(expectedOverlapEnd), 0.0, alignedLength)
    val (actualStartParam, actualEndParam) = if (startParam <= endParam) (startParam, endParam) else (endParam, startParam)
    val actualOverlapLength = actualEndParam - actualStartParam
    if (actualOverlapLength <= settings.trimEpsilon) {
      return None
    }

    val actualOverlapStart = alignedStart + alignedUnit.scale(actualStartParam)
    val actualOverlapEnd = alignedStart + alignedUnit.scale(actualEndParam)

    val overlapSegment = TurtleLineSegment(actualOverlapStart, actualOverlapEnd)

    val expectedRemainders = List(
      if (overlapStart > settings.trimEpsilon) Some(TurtleLineSegment(expected.start, expected.start + expectedUnit.scale(overlapStart))) else None,
      if (overlapEnd < expectedLength - settings.trimEpsilon) Some(TurtleLineSegment(expected.start + expectedUnit.scale(overlapEnd), expected.end)) else None
    ).flatten

    val actualRemainders = List(
      if (actualStartParam > settings.trimEpsilon) Some(TurtleLineSegment(alignedStart, alignedStart + alignedUnit.scale(actualStartParam))) else None,
      if (alignedLength - actualEndParam > settings.trimEpsilon) Some(TurtleLineSegment(alignedStart + alignedUnit.scale(actualEndParam), alignedEnd)) else None
    ).flatten

    Some(OverlapPieces(actualOverlapLength, List(overlapSegment), expectedRemainders, actualRemainders))
  }

  private def selectMatches(candidates: List[CandidateMatch]): List[CandidateMatch] = {
    val usedExpected = mutable.Set.empty[Int]
    val usedActual = mutable.Set.empty[Int]
    val matches = mutable.ListBuffer.empty[CandidateMatch]

    candidates.foreach { candidate =>
      if (!usedExpected.contains(candidate.expectedIndex) && !usedActual.contains(candidate.actualIndex)) {
        usedExpected += candidate.expectedIndex
        usedActual += candidate.actualIndex
        matches += candidate
      }
    }

    matches.toList
  }

  private def clamp(value: Double, min: Double, max: Double): Double = math.max(min, math.min(max, value))
}
