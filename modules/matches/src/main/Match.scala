package lidraughts.matches

import draughts.variant.Variant
import lidraughts.user.User
import org.joda.time.DateTime
import ornicar.scalalib.Random

case class Match(
    _id: Match.ID,
    name: String,
    status: MatchStatus,
    clock: MatchClock,
    applicants: List[MatchApplicant],
    pairings: List[Int],
    variants: List[Variant],
    createdAt: DateTime,
    hostId: String,
    startedAt: Option[DateTime],
    finishedAt: Option[DateTime],
    hostSeenAt: Option[DateTime]
) {

  def id = _id

  def fullName = s"$name match"

  def isCreated = !isStarted

  def isStarted = startedAt.isDefined

  def isFinished = status == MatchStatus.Finished

  def isRunning = status == MatchStatus.Started

  def hasApplicant(userId: String) = applicants.exists(_ is userId)

  def addApplicant(applicant: MatchApplicant) = Created {
    if (!hasApplicant(applicant.player.user) && variants.has(applicant.player.variant))
      copy(applicants = applicants :+ applicant)
    else this
  }

  def removeApplicant(userId: String) = Created {
    copy(applicants = applicants filterNot (_ is userId))
  }

  def accept(userId: String, v: Boolean) = Created {
    copy(applicants = applicants map {
      case a if a is userId => a.copy(accepted = v)
      case a => a
    })
  }

  def nbAccepted = applicants.count(_.accepted)

  def perfTypes: List[lidraughts.rating.PerfType] = variants.flatMap { variant =>
    lidraughts.game.PerfPicker.perfType(
      speed = draughts.Speed(clock.config.some),
      variant = variant,
      daysPerTurn = none
    )
  }

  def variantRich = variants.size > 3

  def isHost(userOption: Option[User]): Boolean = userOption ?? isHost
  def isHost(user: User): Boolean = user.id == hostId

  def isNotBrandNew = createdAt isBefore DateTime.now.minusSeconds(10)

  private def Created(s: => Match): Match = if (isCreated) s else this

}

object Match {

  type ID = String

  case class OnStart(mtch: Match)

  def make(
    host: User,
    clock: MatchClock,
    variants: List[Variant]
  ): Match = Match(
    _id = Random nextString 8,
    name = RandomName(),
    status = MatchStatus.Created,
    clock = clock,
    hostId = host.id,
    createdAt = DateTime.now,
    variants = variants,
    applicants = Nil,
    pairings = Nil,
    startedAt = none,
    finishedAt = none,
    hostSeenAt = DateTime.now.some
  )

}
