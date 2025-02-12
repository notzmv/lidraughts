package lidraughts.analyse

import draughts.{ Centis, Stats }
import lidraughts.common.Maths

object Statistics {

  // Coefficient of Variance
  def coefVariation(a: List[Int]): Option[Float] = {
    val s = Stats(a)
    s.stdDev.map { _ / s.mean }
  }

  // ups all values by 0.1s
  // as to avoid very high variation on bullet games
  // where all move times are low (https://lichess.org/@/AlisaP?mod)
  // and drops the first move because it's always 0
  def moveTimeCoefVariation(a: List[Centis]): Option[Float] =
    coefVariation(a.drop(1).map(_.centis + 10))

  def moveTimeCoefVariationNoDrop(a: List[Centis]): Option[Float] =
    coefVariation(a.map(_.centis + 10))

  def moveTimeCoefVariation(pov: lidraughts.game.Pov): Option[Float] =
    for {
      mt <- moveTimes(pov)
      coef <- moveTimeCoefVariation(mt)
    } yield coef

  def moveTimes(pov: lidraughts.game.Pov): Option[List[Centis]] =
    pov.game.moveTimes(pov.color)

  def cvIndicatesHighlyFlatTimes(c: Float) =
    c < 0.25

  def cvIndicatesHighlyFlatTimesForStreaks(c: Float) =
    c < 0.13

  def cvIndicatesModeratelyFlatTimes(c: Float) =
    c < 0.4

  private val instantaneous = Centis(0)

  def slidingMoveTimesCvs(pov: lidraughts.game.Pov): Option[Iterator[Float]] =
    moveTimes(pov) ?? { mt =>
      mt.iterator.sliding(15)
        .filter(_.count(instantaneous ==) < 4)
        .flatMap(a => moveTimeCoefVariationNoDrop(a.toList))
        .some
    }

  def highlyConsistentMoveTimes(pov: lidraughts.game.Pov): Boolean =
    if (pov.game.clock.forall(_.estimateTotalSeconds > 60))
      moveTimeCoefVariation(pov) ?? { cvIndicatesHighlyFlatTimes(_) }
    else false

  def highlyConsistentMoveTimeStreaks(pov: lidraughts.game.Pov): Boolean =
    if (pov.game.clock.forall(_.estimateTotalSeconds > 60))
      slidingMoveTimesCvs(pov) ?? {
        _ exists cvIndicatesHighlyFlatTimesForStreaks
      }
    else false

  def moderatelyConsistentMoveTimes(pov: lidraughts.game.Pov): Boolean =
    moveTimeCoefVariation(pov) ?? { cvIndicatesModeratelyFlatTimes(_) }

  private val fastMove = Centis(50)
  def noFastMoves(pov: lidraughts.game.Pov): Boolean = {
    val moveTimes = ~pov.game.moveTimes(pov.color)
    moveTimes.count(fastMove >) <= (moveTimes.size / 20) + 2
  }

  def listAverage[T: Numeric](x: List[T]) = ~Maths.mean(x)

  def listDeviation[T: Numeric](x: List[T]) = ~Stats(x).stdDev
}
