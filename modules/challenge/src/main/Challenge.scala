package lidraughts.challenge

import draughts.format.FEN
import draughts.variant.{ Variant, FromPosition }
import draughts.{ Mode, Speed }
import org.joda.time.DateTime

import lidraughts.game.PerfPicker
import lidraughts.rating.PerfType
import lidraughts.user.User
import draughts.format.Forsyth

case class Challenge(
    _id: String,
    status: Challenge.Status,
    variant: Variant,
    initialFen: Option[FEN],
    timeControl: Challenge.TimeControl,
    mode: Mode,
    colorChoice: Challenge.ColorChoice,
    finalColor: draughts.Color,
    challenger: EitherChallenger,
    destUser: Option[Challenge.Registered],
    rematchOf: Option[String],
    createdAt: DateTime,
    seenAt: DateTime,
    expiresAt: DateTime
) {

  import Challenge._

  def id = _id

  def challengerUser = challenger.right.toOption
  def challengerUserId = challengerUser.map(_.id)
  def challengerIsAnon = challenger.isLeft
  def destUserId = destUser.map(_.id)

  def userIds = List(challengerUserId, destUserId).flatten

  def daysPerTurn = timeControl match {
    case TimeControl.Correspondence(d) => d.some
    case _ => none
  }
  def unlimited = timeControl == TimeControl.Unlimited

  def clock = timeControl match {
    case c: TimeControl.Clock => c.some
    case _ => none
  }

  def hasClock = clock.isDefined

  def openDest = destUser.isEmpty
  def online = status == Status.Created
  def active = online || status == Status.Offline
  def declined = status == Status.Declined
  def accepted = status == Status.Accepted

  def setDestUser(u: User) = copy(
    destUser = toRegistered(variant, timeControl)(u).some
  )

  def speed = speedOf(timeControl)

  lazy val perfType = perfTypeOf(variant, timeControl)
}

object Challenge {

  type ID = String

  sealed abstract class Status(val id: Int) {
    val name = toString.toLowerCase
  }
  object Status {
    case object Created extends Status(10)
    case object Offline extends Status(15)
    case object Canceled extends Status(20)
    case object Declined extends Status(30)
    case object Accepted extends Status(40)
    val all = List(Created, Offline, Canceled, Declined, Accepted)
    def apply(id: Int): Option[Status] = all.find(_.id == id)
  }

  case class Rating(int: Int, provisional: Boolean) {
    def show = s"$int${if (provisional) "?" else ""}"
  }
  object Rating {
    def apply(p: lidraughts.rating.Perf): Rating = Rating(p.intRating, p.provisional)
  }

  case class Registered(id: User.ID, rating: Rating)
  case class Anonymous(secret: String)

  sealed trait TimeControl
  object TimeControl {
    case object Unlimited extends TimeControl
    case class Correspondence(days: Int) extends TimeControl
    case class Clock(config: draughts.Clock.Config) extends TimeControl {
      // All durations are expressed in seconds
      def limit = config.limit
      def increment = config.increment
      def show = config.show
    }
  }

  sealed trait ColorChoice
  object ColorChoice {
    case object Random extends ColorChoice
    case object White extends ColorChoice
    case object Black extends ColorChoice
  }

  private def speedOf(timeControl: TimeControl) = timeControl match {
    case TimeControl.Clock(config) => Speed(config)
    case _ => Speed.Correspondence
  }

  private def perfTypeOf(variant: Variant, timeControl: TimeControl): PerfType =
    PerfPicker.perfType(speedOf(timeControl), variant, timeControl match {
      case TimeControl.Correspondence(d) => d.some
      case _ => none
    }).orElse {
      (variant == FromPosition) option perfTypeOf(draughts.variant.Standard, timeControl)
    }.|(PerfType.Correspondence)

  private val idSize = 8

  private def randomId = ornicar.scalalib.Random nextString idSize

  private def toRegistered(variant: Variant, timeControl: TimeControl)(u: User) =
    Registered(u.id, Rating(u.perfs(perfTypeOf(variant, timeControl))))

  def make(
    variant: Variant,
    initialFen: Option[FEN],
    timeControl: TimeControl,
    mode: Mode,
    color: String,
    challenger: Either[String, User],
    destUser: Option[User],
    rematchOf: Option[String]
  ): Challenge = {
    val (colorChoice, finalColor) = color match {
      case "white" => ColorChoice.White -> draughts.White
      case "black" => ColorChoice.Black -> draughts.Black
      case _ => ColorChoice.Random -> draughts.Color(scala.util.Random.nextBoolean)
    }
    val finalMode = timeControl match {
      case TimeControl.Clock(clock) if !lidraughts.game.Game.allowRated(variant, clock) => Mode.Casual
      case _ => mode
    }
    new Challenge(
      _id = randomId,
      status = Status.Created,
      variant = variant,
      initialFen =
        if (variant == FromPosition) initialFen.flatMap(fen => Forsyth << fen.value).map(sit => FEN(Forsyth >> sit.withoutGhosts))
        else !variant.standardInitialPosition option FEN(variant.initialFen),
      timeControl = timeControl,
      mode = finalMode,
      colorChoice = colorChoice,
      finalColor = finalColor,
      challenger = challenger.fold[EitherChallenger](
        sid => Left(Anonymous(sid)),
        u => Right(toRegistered(variant, timeControl)(u))
      ),
      destUser = destUser map toRegistered(variant, timeControl),
      rematchOf = rematchOf,
      createdAt = DateTime.now,
      seenAt = DateTime.now,
      expiresAt = inTwoWeeks
    )
  }
}
