package lidraughts.matches

import play.api.data._
import play.api.data.Forms._

import lidraughts.common.Form._

final class DataForm {

  val clockTimes = (5 to 15 by 5) ++ (20 to 90 by 10) ++ (120 to 180 by 20)
  val clockTimeDefault = 20
  val clockTimeChoices = options(clockTimes, "%d minute{s}")

  val clockIncrements = (0 to 2 by 1) ++ (3 to 7) ++ (10 to 30 by 5) ++ (40 to 60 by 10) ++ (90 to 180 by 30)
  val clockIncrementDefault = 60
  val clockIncrementChoices = options(clockIncrements, "%d second{s}")

  def create = Form(mapping(
    "clockTime" -> numberIn(clockTimeChoices),
    "clockIncrement" -> numberIn(clockIncrementChoices),
    "variants" -> list {
      number.verifying(Set(draughts.variant.Standard.id, draughts.variant.Frisian.id, draughts.variant.Frysk.id, draughts.variant.Antidraughts.id, draughts.variant.Breakthrough.id) contains _)
    }.verifying("At least one variant", _.nonEmpty)
  )(MatchSetup.apply)(MatchSetup.unapply)) fill MatchSetup(
    clockTime = clockTimeDefault,
    clockIncrement = clockIncrementDefault,
    variants = List(draughts.variant.Standard.id)
  )
}

case class MatchSetup(
    clockTime: Int,
    clockIncrement: Int,
    variants: List[Int]
)
