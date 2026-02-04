package interactionPlugins.blockEnvironment.feedback.ml

import scala.scalajs.js

/**
 * Multiclass linear model: logits = W x + b, probs = softmax(logits)
 *
 * Intended to be trained offline and shipped as JSON.
 */
final case class SoftmaxModel(
  labels: Vector[String],
  featureIndex: Map[String, Int],
  weights: Array[Array[Double]],
  bias: Array[Double],
  mean: Option[Array[Double]] = None,
  std: Option[Array[Double]] = None
) {

  def numClasses: Int = labels.length

  def numFeatures: Int =
    if weights.nonEmpty then weights(0).length
    else featureIndex.valuesIterator.maxOption.map(_ + 1).getOrElse(0)

  def predictProbs(features: Map[String, Double]): Array[Double] = {
    val x = Array.fill[Double](numFeatures)(0.0)
    features.foreach { case (name, value) =>
      featureIndex.get(name).foreach { idx =>
        if idx >= 0 && idx < x.length then x(idx) = value
      }
    }

    (mean, std) match {
      case (Some(m), Some(s)) if m.length == x.length && s.length == x.length =>
        var i = 0
        while i < x.length do
          val denom = if s(i) == 0.0 then 1.0 else s(i)
          x(i) = (x(i) - m(i)) / denom
          i += 1
      case _ =>
    }

    val logits = Array.fill[Double](numClasses)(0.0)
    var c = 0
    while c < numClasses do
      var z = bias(c)
      val w = weights(c)
      var j = 0
      while j < x.length do
        z += w(j) * x(j)
        j += 1
      logits(c) = z
      c += 1

    // numerically stable softmax
    var max = Double.NegativeInfinity
    c = 0
    while c < numClasses do
      if logits(c) > max then max = logits(c)
      c += 1

    var sum = 0.0
    c = 0
    while c < numClasses do
      val e = math.exp(logits(c) - max)
      logits(c) = e
      sum += e
      c += 1

    c = 0
    while c < numClasses do
      logits(c) = if sum == 0.0 then 0.0 else logits(c) / sum
      c += 1

    logits
  }

  def predictLabel(features: Map[String, Double]): (String, Double) = {
    val p = predictProbs(features)
    var bestIdx = 0
    var best = Double.NegativeInfinity
    var i = 0
    while i < p.length do
      if p(i) > best then
        best = p(i)
        bestIdx = i
      i += 1
    (labels(bestIdx), best)
  }

  def topContributors(
    features: Map[String, Double],
    classLabel: String,
    k: Int = 5
  ): Seq[(String, Double)] = {
    val classIdx = labels.indexOf(classLabel)
    if classIdx < 0 || classIdx >= weights.length then return Nil

    val x0 = Array.fill[Double](numFeatures)(0.0)
    features.foreach { case (name, value) =>
      featureIndex.get(name).foreach { idx =>
        if idx >= 0 && idx < x0.length then x0(idx) = value
      }
    }

    val x = x0.clone()
    (mean, std) match {
      case (Some(m), Some(s)) if m.length == x.length && s.length == x.length =>
        var i = 0
        while i < x.length do
          val denom = if s(i) == 0.0 then 1.0 else s(i)
          x(i) = (x(i) - m(i)) / denom
          i += 1
      case _ =>
    }

    val w = weights(classIdx)
    val contrib = featureIndex.iterator.map { case (name, idx) =>
      val v = if idx >= 0 && idx < x.length then x(idx) else 0.0
      (name, w(idx) * v)
    }.toSeq

    contrib.sortBy { case (_, score) => -math.abs(score) }.take(math.max(0, k))
  }
}

object SoftmaxModel {

  /** Parses the JSON format emitted by tools/dev/train_mini_ml.py. */
  def fromJson(jsonText: String): SoftmaxModel = {
    val root = js.JSON.parse(jsonText).asInstanceOf[js.Dynamic]

    def anyToDouble(a: js.Any): Double =
      js.typeOf(a) match
        case "number" => a.asInstanceOf[Double]
        case "string" => a.asInstanceOf[String].toDouble
        case _         => a.toString.toDouble

    def anyToInt(a: js.Any): Int =
      js.typeOf(a) match
        case "number" => a.asInstanceOf[Double].toInt
        case "string" => a.asInstanceOf[String].toInt
        case _         => a.toString.toInt

    def dynArrToStringVec(d: js.Dynamic): Vector[String] =
      d.asInstanceOf[js.Array[js.Any]].map(_.toString).toVector

    def dynArrToDoubleArray(d: js.Dynamic): Array[Double] =
      d.asInstanceOf[js.Array[js.Any]].map { x =>
        anyToDouble(x)
      }.toArray

    val labels = dynArrToStringVec(root.labels)

    val featureIndexObj = root.feature_index.asInstanceOf[js.Dictionary[js.Any]]
    val featureIndex = featureIndexObj.iterator.map { case (k, v) =>
      val i = anyToInt(v)
      (k, i)
    }.toMap

    val weightsDyn = root.weights.asInstanceOf[js.Array[js.Any]]
    val weights = weightsDyn.map { row =>
      dynArrToDoubleArray(row.asInstanceOf[js.Dynamic])
    }.toArray

    val bias = dynArrToDoubleArray(root.bias)

    val standardize =
      try root.standardize
      catch case _: Throwable => null

    val meanOpt =
      if standardize == null || js.isUndefined(standardize) then None
      else
        val m = standardize.mean
        if m == null || js.isUndefined(m) then None else Some(dynArrToDoubleArray(m))

    val stdOpt =
      if standardize == null || js.isUndefined(standardize) then None
      else
        val s = standardize.std
        if s == null || js.isUndefined(s) then None else Some(dynArrToDoubleArray(s))

    SoftmaxModel(
      labels = labels,
      featureIndex = featureIndex,
      weights = weights,
      bias = bias,
      mean = meanOpt,
      std = stdOpt
    )
  }
}
