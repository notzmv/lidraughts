package lidraughts.tournament

import akka.actor._
import akka.pattern.pipe
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants._
import scala.concurrent.duration._

import actorApi._
import draughts.StartingPosition
import Schedule.offsetCET

private final class TournamentScheduler private (api: TournamentApi) extends Actor {

  import Schedule.Freq._
  import Schedule.Speed._
  import Schedule.Plan
  import draughts.variant._

  /* Month plan:
   * First week: Monthly variant tournaments
   * Second week: Yearly & Shield variant tournaments
   * Third week: Shield standard tournaments
   * Last week: Monthly standard tournaments
   */

  // def marathonDates = List(
  // Spring -> Saturday of the weekend after Orthodox Easter Sunday
  // Summer -> first Saturday of August
  // Autumn -> Saturday of weekend before the weekend Halloween falls on (c.f. half-term holidays)
  // Winter -> 28 December, convenient day in the space between Boxing Day and New Year's Day
  // )

  def receive = {

    case ScheduleNow =>
      TournamentRepo.scheduledUnfinished.map(_.flatMap(_.schedule)) map
        ScheduleNowWith.apply pipeTo self

    case ScheduleNowWith(dbScheds) => try {

      val rightNow = DateTime.now
      val today = rightNow.withTimeAtStartOfDay
      val tomorrow = rightNow plusDays 1
      val startOfYear = today.dayOfYear.withMinimumValue

      class OfMonth(fromNow: Int) {
        val firstDay = today.plusMonths(fromNow).dayOfMonth.withMinimumValue
        val lastDay = firstDay.dayOfMonth.withMaximumValue

        val firstWeek = firstDay.plusDays(7 - (firstDay.getDayOfWeek - 1) % 7)
        val secondWeek = firstWeek plusDays 7
        val thirdWeek = secondWeek plusDays 7
        val lastWeek = lastDay.minusDays((lastDay.getDayOfWeek - 1) % 7)
      }
      val thisMonth = new OfMonth(0)
      val nextMonth = new OfMonth(1)

      def nextDayOfWeek(number: Int) = today.plusDays((number + 7 - today.getDayOfWeek) % 7)
      val nextMonday = nextDayOfWeek(1)
      val nextTuesday = nextDayOfWeek(2)
      val nextWednesday = nextDayOfWeek(3)
      val nextThursday = nextDayOfWeek(4)
      val nextFriday = nextDayOfWeek(5)
      val nextSaturday = nextDayOfWeek(6)
      val nextSunday = nextDayOfWeek(7)

      def secondWeekOf(month: Int) = {
        val start = orNextYear(startOfYear.withMonthOfYear(month))
        start.plusDays(15 - start.getDayOfWeek)
      }

      def orTomorrow(date: DateTime) = if (date isBefore rightNow) date plusDays 1 else date
      def orNextWeek(date: DateTime) = if (date isBefore rightNow) date plusWeeks 1 else date
      def orNextYear(date: DateTime) = if (date isBefore rightNow) date plusYears 1 else date

      val isHalloween = today.getDayOfMonth == 31 && today.getMonthOfYear == OCTOBER

      val std = draughts.variant.Standard.startingPosition
      def opening(offset: Int) = {
        val positions = Nil //StartingPosition.featurable
        positions((today.getDayOfYear + offset) % positions.size)
      }

      val farFuture = today plusMonths 7

      val birthday = new DateTime(2018, 8, 13, 12, 0, 0)
      val birthdayThisYear = birthday.withYear(today.getYear)

      // schedule daily and longer CET, others UTC
      val todayCET = offsetCET(today)

      val nextPlans: List[Schedule.Plan] = List(

        List( // legendary tournaments!
          at(birthdayThisYear, 14 - offsetCET(birthdayThisYear)) map orNextYear map { date =>
            val yo = date.getYear - 2018
            Schedule(Unique, Blitz, Standard, std, date) plan {
              _.copy(
                name = s"${date.getYear} Lidraughts Anniversary",
                minutes = 12 * 60,
                spotlight = Spotlight(
                  headline = s"${if (yo == 1) "One year" else s"$yo years"} of free draughts!",
                  description = s"""
We've had ${if (yo == 1) "our first great draughts year" else s"$yo great draughts years"} together!

Thank you all, you rock!"""
                ).some
              )
            }
          }
        ).flatten,

        List( // yearly tournaments!
          secondWeekOf(JANUARY).withDayOfWeek(SUNDAY) -> Classical,
          secondWeekOf(FEBRUARY).withDayOfWeek(TUESDAY) -> HyperBullet,
          secondWeekOf(MARCH).withDayOfWeek(WEDNESDAY) -> Bullet,
          secondWeekOf(APRIL).withDayOfWeek(THURSDAY) -> SuperBlitz,
          secondWeekOf(MAY).withDayOfWeek(FRIDAY) -> Blitz,
          secondWeekOf(JUNE).withDayOfWeek(SATURDAY) -> Rapid,

          secondWeekOf(JULY).withDayOfWeek(SUNDAY) -> Classical,
          secondWeekOf(AUGUST).withDayOfWeek(TUESDAY) -> HyperBullet,
          secondWeekOf(SEPTEMBER).withDayOfWeek(WEDNESDAY) -> Bullet,
          secondWeekOf(OCTOBER).withDayOfWeek(THURSDAY) -> SuperBlitz,
          secondWeekOf(NOVEMBER).withDayOfWeek(FRIDAY) -> Blitz,
          secondWeekOf(DECEMBER).withDayOfWeek(SATURDAY) -> Rapid
        ).flatMap {
            case (day, speed) =>
              at(day, 17 - offsetCET(day)) filter farFuture.isAfter map { date =>
                Schedule(Yearly, speed, Standard, std, date).plan
              }
          },

        List( // yearly variant tournaments!
          secondWeekOf(JANUARY).withDayOfWeek(MONDAY) -> Breakthrough,
          secondWeekOf(JUNE).withDayOfWeek(WEDNESDAY) -> Brazilian,
          secondWeekOf(MAY).withDayOfWeek(THURSDAY) -> Russian,
          secondWeekOf(FEBRUARY).withDayOfWeek(FRIDAY) -> Frysk,
          secondWeekOf(MARCH).withDayOfWeek(SATURDAY) -> Antidraughts,
          secondWeekOf(APRIL).withDayOfWeek(SUNDAY) -> Frisian,

          secondWeekOf(JULY).withDayOfWeek(MONDAY) -> Breakthrough,
          secondWeekOf(DECEMBER).withDayOfWeek(WEDNESDAY) -> Brazilian,
          secondWeekOf(NOVEMBER).withDayOfWeek(THURSDAY) -> Russian,
          secondWeekOf(AUGUST).withDayOfWeek(FRIDAY) -> Frysk,
          secondWeekOf(SEPTEMBER).withDayOfWeek(SATURDAY) -> Antidraughts,
          secondWeekOf(OCTOBER).withDayOfWeek(SUNDAY) -> Frisian
        ).flatMap {
            case (day, variant) =>
              at(day, 18 - offsetCET(day)) filter farFuture.isAfter map { date =>
                Schedule(Yearly, Blitz, variant, std, date).plan
              }
          },

        List(thisMonth, nextMonth).flatMap { month =>
          List(
            List( // monthly standard tournaments!
              month.lastWeek.withDayOfWeek(MONDAY) -> UltraBullet,
              month.lastWeek.withDayOfWeek(TUESDAY) -> HyperBullet,
              month.lastWeek.withDayOfWeek(WEDNESDAY) -> Bullet,
              month.lastWeek.withDayOfWeek(THURSDAY) -> SuperBlitz,
              month.lastWeek.withDayOfWeek(FRIDAY) -> Blitz,
              month.lastWeek.withDayOfWeek(SATURDAY) -> Rapid
            ).flatMap {
                case (day, speed) => at(day, 17 - offsetCET(day)) map { date =>
                  Schedule(Monthly, speed, Standard, std, date).plan
                }
              },

            List( // monthly variant tournaments!
              month.firstWeek.withDayOfWeek(MONDAY) -> Breakthrough,
              month.firstWeek.withDayOfWeek(WEDNESDAY) -> Brazilian,
              month.firstWeek.withDayOfWeek(THURSDAY) -> Russian,
              month.firstWeek.withDayOfWeek(FRIDAY) -> Frysk,
              month.firstWeek.withDayOfWeek(SATURDAY) -> Antidraughts,
              month.firstWeek.withDayOfWeek(SUNDAY) -> Frisian
            ).flatMap {
                case (day, variant) => at(day, (if (variant.russian || variant.brazilian) 17 else 18) - offsetCET(day)) map { date =>
                  Schedule(Monthly, Blitz, variant, std, date).plan
                }
              },

            List( // shield tournaments!
              month.thirdWeek.withDayOfWeek(TUESDAY) -> HyperBullet,
              month.thirdWeek.withDayOfWeek(WEDNESDAY) -> Bullet,
              month.thirdWeek.withDayOfWeek(THURSDAY) -> SuperBlitz,
              month.thirdWeek.withDayOfWeek(FRIDAY) -> Blitz,
              month.thirdWeek.withDayOfWeek(SATURDAY) -> Rapid
            ).flatMap {
                case (day, speed) => at(day, 16 - offsetCET(day)) map { date =>
                  Schedule(Shield, speed, Standard, std, date) plan {
                    _.copy(
                      name = s"${if (speed == Bullet) "Bullet Inc" else speed.toString} Shield",
                      spotlight = Some(TournamentShield spotlight speed.toString)
                    )
                  }
                }
              },

            List( // shield variant tournaments! (different days to not overlap with yearlies)
              month.secondWeek.withDayOfWeek(TUESDAY) -> Brazilian,
              month.secondWeek.withDayOfWeek(WEDNESDAY) -> Breakthrough,
              month.secondWeek.withDayOfWeek(THURSDAY) -> Frysk,
              month.secondWeek.withDayOfWeek(FRIDAY) -> Antidraughts,
              month.secondWeek.withDayOfWeek(SATURDAY) -> Frisian,
              month.secondWeek.withDayOfWeek(SUNDAY) -> Russian
            ).flatMap {
                case (day, variant) => at(day, 16 - offsetCET(day)) map { date =>
                  Schedule(Shield, Blitz, variant, std, date) plan {
                    _.copy(
                      name = s"${variant.name} Shield",
                      spotlight = Some(TournamentShield spotlight variant.name)
                    )
                  }
                }
              }

          ).flatten
        },

        List( // weekly standard tournaments!
          nextMonday -> UltraBullet,
          nextTuesday -> HyperBullet,
          nextWednesday -> Bullet,
          nextThursday -> SuperBlitz,
          nextFriday -> Blitz,
          nextSaturday -> Rapid
        ).flatMap {
            case (day, speed) => at(day, 17 - offsetCET(day)) map { date =>
              Schedule(Weekly, speed, Standard, std, date |> orNextWeek).plan
            }
          },

        List( // weekly variant tournaments!
          nextMonday -> Breakthrough,
          nextWednesday -> Brazilian,
          nextThursday -> Russian,
          nextFriday -> Frysk,
          nextSaturday -> Antidraughts,
          nextSunday -> Frisian
        ).flatMap {
            case (day, variant) => at(day, (if (variant.russian || variant.brazilian) 18 else 19) - offsetCET(day)) map { date =>
              Schedule(Weekly, Blitz, variant, std, date |> orNextWeek).plan
            }
          },

        List( // daily tournaments!
          at(today, 17 - todayCET) map { date => Schedule(Daily, Bullet, Standard, std, date |> orTomorrow).plan },
          at(today, 18 - todayCET) map { date => Schedule(Daily, SuperBlitz, Standard, std, date |> orTomorrow).plan }
        ).flatten,

        List( // daily variant tournaments!
          at(today, 15 - todayCET, 30) map { date => Schedule(Daily, SuperBlitz, Frysk, std, date |> orTomorrow).plan },
          at(today, 17 - todayCET) map { date => Schedule(Daily, SuperBlitz, Russian, std, date |> orTomorrow).plan },
          at(today, 18 - todayCET, 30) map { date => Schedule(Daily, SuperBlitz, Brazilian, std, date |> orTomorrow).plan },
          at(today, 20 - todayCET) map { date => Schedule(Daily, Blitz, Frisian, std, date |> orTomorrow).plan },
          at(today, 21 - todayCET, 30) map { date => Schedule(Daily, SuperBlitz, Antidraughts, std, date |> orTomorrow).plan }
        ).flatten,

        List( // eastern tournaments!
          at(today, 5 - todayCET) map { date => Schedule(Eastern, Bullet, Standard, std, date |> orTomorrow).plan },
          at(today, 6 - todayCET) map { date => Schedule(Eastern, SuperBlitz, Standard, std, date |> orTomorrow).plan }
        ).flatten,

        // hourly standard tournaments!
        (0 to 6).toList.flatMap { hourDelta =>
          val date = rightNow plusHours hourDelta
          val hour = date.getHourOfDay
          val bulletType = if (hour % 3 == 2) HippoBullet else Bullet
          val blitzType = if (hour % 3 == 2) Blitz else SuperBlitz
          List(
            at(date, hour) map { date => Schedule(Hourly, bulletType, Standard, std, date).plan },
            at(date, hour, 30) collect { case date if bulletType != HippoBullet => Schedule(Hourly, if (hour % 3 == 1) HyperBullet else Bullet, Standard, std, date).plan },
            at(date, hour) map { date => Schedule(Hourly, blitzType, Standard, std, date).plan }
          ).flatten
        },

        // hourly variant tournaments
        (0 to 6).toList.flatMap { hourDelta =>
          val date = rightNow plusHours hourDelta
          val hour = date.getHourOfDay
          val hourCET = hour + todayCET
          if (hourCET == 20 || hourCET == 21 || hourCET == 22) {
            // no tournaments during daily frisian / antidraughts
            Nil
          } else if (hour % 3 == 0) {
            // alternating frysk / breakthrough every third hour
            val variant = if (hour % 6 == 0) Frysk else Breakthrough
            List(
              at(date, hour) map { date => Schedule(Hourly, SuperBlitz, variant, std, date).plan }
            ).flatten
          } else {
            // alternating blitz / superblitz
            val variant = if (hour % 3 == 1) Frisian else Antidraughts
            val blitzType = if (hour % 6 == 1 || hour % 6 == 2) SuperBlitz else Blitz
            List(
              at(date, hour) map { date => Schedule(Hourly, blitzType, variant, std, date).plan }
            ).flatten
          }
        },

        // hourly 64 tournaments (russian / brazilian)
        (0 to 6).toList.flatMap { hourDelta =>
          val date = rightNow plusHours hourDelta
          val hour = date.getHourOfDay
          val hourCET = hour + todayCET
          if (hour % 3 == 2) {
            // bullet every third hour
            List(
              at(date, hour) map { date => Schedule(Hourly, Bullet, Brazilian, std, date).plan },
              at(date, hour, 30) map { date => Schedule(Hourly, Bullet, Russian, std, date).plan }
            ).flatten
          } else {
            // alternating blitz / superblitz
            val variant = if (hour % 3 == 0) Brazilian else Russian
            val blitzType = if (hour % 6 == 0 || hour % 6 == 1) SuperBlitz else Blitz
            List(
              at(date, hour) map { date => Schedule(Hourly, blitzType, variant, std, date).plan }
            ).flatten
          }
        }

      ).flatten

      nextPlans.map { plan =>
        plan.copy(schedule = Schedule addCondition plan.schedule)
      }.foldLeft(List[Plan]()) {
        case (plans, p) if p.schedule.at.isBeforeNow => plans
        case (plans, p) if overlaps(p.schedule, dbScheds) => plans
        case (plans, p) if overlaps(p.schedule, plans.map(_.schedule)) => plans
        case (plans, p) => p :: plans
      } foreach api.createFromPlan
    } catch {
      case e: org.joda.time.IllegalInstantException =>
        logger.error(s"failed to schedule all: ${e.getMessage}")
    }
  }

  private case class ScheduleNowWith(dbScheds: List[Schedule])

  private def endsAt(s: Schedule) = s.at plus (Schedule.durationFor(s).toLong * 60 * 1000)
  private def interval(s: Schedule) = new org.joda.time.Interval(s.at, endsAt(s))
  private def overlaps(s: Schedule, ss: Seq[Schedule]) = ss exists {
    // unique tournaments never overlap
    case s2 if s.freq.isUnique != s2.freq.isUnique => false
    // prevent daily && weekly on the same day
    case s2 if s.freq.isDailyOrBetter && s2.freq.isDailyOrBetter && s.sameVariantAndSpeed(s2) => s sameDay s2
    // overlapping same variant, or similar 64 variant
    case s2 if s.variant.exotic && (s.sameVariant(s2) || s.similar64Variant(s2)) => interval(s) overlaps interval(s2)
    // overlapping same rating limit
    case s2 if s2.hasMaxRating && s.sameMaxRating(s2) => interval(s) overlaps interval(s2)
    // overlapping similar
    case s2 if s.similarSpeed(s2) && s.sameVariant(s2) && s.sameMaxRating(s2) => interval(s) overlaps interval(s2)
    case _ => false
  }

  private def at(day: DateTime, hour: Int, minute: Int = 0): Option[DateTime] = try {
    Some(day.withTimeAtStartOfDay plusHours hour plusMinutes minute)
  } catch {
    case e: Exception =>
      logger.error(s"failed to schedule one: ${e.getMessage}")
      None
  }
}

private object TournamentScheduler {

  def start(system: ActorSystem, api: TournamentApi) = {
    val ref = system.actorOf(Props(new TournamentScheduler(api)))
    system.scheduler.schedule(1 minute, 5 minutes, ref, ScheduleNow)
  }
}
