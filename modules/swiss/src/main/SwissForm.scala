package lidraughts.swiss

import draughts.Clock.{ Config => ClockConfig }
import draughts.variant.Variant
import org.joda.time.DateTime
import play.api.data._
import play.api.data.Forms._
import scala.concurrent.duration._

import lidraughts.common.Form._

final class SwissForm(isProd: Boolean) {

  import SwissForm._

  def form(minRounds: Int = 3) =
    Form(
      mapping(
        "name" -> optional(eventName(2, 30)),
        "clock" -> mapping(
          "limit" -> number.verifying(clockLimits.contains _),
          "increment" -> number(min = 0, max = 600)
        )(ClockConfig.apply)(ClockConfig.unapply)
          .verifying("Invalid clock", _.estimateTotalSeconds > 0),
        "startsAt" -> optional(inTheFuture(ISODateTimeOrTimestamp.isoDateTimeOrTimestamp)),
        "variant" -> optional(nonEmptyText.verifying(v => Variant(v).isDefined)),
        "rated" -> optional(boolean),
        "nbRounds" -> number(min = minRounds, max = 100),
        "description" -> optional(cleanNonEmptyText),
        "hasChat" -> optional(boolean),
        "roundInterval" -> optional(numberIn(roundIntervals))
      )(SwissData.apply)(SwissData.unapply)
    )

  def create =
    form() fill SwissData(
      name = none,
      clock = ClockConfig(180, 0),
      startsAt = Some(DateTime.now plusSeconds {
        if (isProd) 60 * 10 else 20
      }),
      variant = Variant.default.key.some,
      rated = true.some,
      nbRounds = 8,
      description = none,
      hasChat = true.some,
      roundInterval = Swiss.RoundInterval.auto.some
    )

  def edit(s: Swiss) =
    form(s.round.value) fill SwissData(
      name = s.name.some,
      clock = s.clock,
      startsAt = s.startsAt.some,
      variant = s.variant.key.some,
      rated = s.settings.rated.some,
      nbRounds = s.settings.nbRounds,
      description = s.settings.description,
      hasChat = s.settings.hasChat.some,
      roundInterval = s.settings.roundInterval.toSeconds.toInt.some
    )

  def nextRound =
    Form(
      single(
        "date" -> inTheFuture(ISODateTimeOrTimestamp.isoDateTimeOrTimestamp)
      )
    )
}

object SwissForm {

  val clockLimits: Seq[Int] = Seq(0, 15, 30, 45, 60, 90) ++ {
    (120 to 420 by 60) ++ (600 to 1800 by 300) ++ (2400 to 10800 by 600)
  }

  val clockLimitChoices = options(
    clockLimits,
    l => s"${draughts.Clock.Config(l, 0).limitString}${if (l <= 1) " minute" else " minutes"}"
  )

  val roundIntervals: Seq[Int] =
    Seq(
      Swiss.RoundInterval.auto,
      5,
      10,
      20,
      30,
      45,
      60,
      90,
      120,
      180,
      300,
      600,
      900,
      1200,
      1800,
      2700,
      3600,
      24 * 3600,
      2 * 24 * 3600,
      7 * 24 * 3600,
      Swiss.RoundInterval.manual
    )

  val roundIntervalChoices = options(
    roundIntervals,
    s =>
      if (s == Swiss.RoundInterval.auto) s"Automatic (recommended)"
      else if (s == Swiss.RoundInterval.manual) s"Manually schedule each round"
      else if (s < 60) s"$s seconds"
      else if (s < 3600) s"${s / 60} minute(s)"
      else if (s < 24 * 3600) s"${s / 3600} hour(s)"
      else s"${s / 24 / 3600} days(s)"
  )

  case class SwissData(
      name: Option[String],
      clock: ClockConfig,
      startsAt: Option[DateTime],
      variant: Option[String],
      rated: Option[Boolean],
      nbRounds: Int,
      description: Option[String],
      hasChat: Option[Boolean],
      roundInterval: Option[Int]
  ) {
    def realVariant = variant flatMap Variant.apply getOrElse Variant.default
    def realStartsAt = startsAt | DateTime.now.plusMinutes(10)
    def realRoundInterval = {
      (roundInterval | Swiss.RoundInterval.auto) match {
        case Swiss.RoundInterval.auto =>
          import draughts.Speed._
          draughts.Speed(clock) match {
            case UltraBullet => 5
            case Bullet => 10
            case Blitz if clock.estimateTotalSeconds < 300 => 20
            case Blitz => 30
            case Rapid => 60
            case _ => 300
          }
        case i => i
      }
    }.seconds
  }
}
