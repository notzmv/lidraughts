package lidraughts.user

import org.joda.time.DateTime

case class SimplifiedTrophy(
    _id: String,
    user: String,
    kind: String,
    date: DateTime
)

object SimplifiedTrophy {
  def make(userId: String, kindKey: String): SimplifiedTrophy = SimplifiedTrophy(
    _id = ornicar.scalalib.Random nextString 8,
    user = userId,
    kind = kindKey,
    date = DateTime.now
  )
}

case class Trophy(
    _id: String, // random
    user: String,
    kind: TrophyKind,
    date: DateTime
) extends Ordered[Trophy] {

  def timestamp = date.getMillis

  def compare(other: Trophy) =
    if (kind.order == other.kind.order) date compareTo other.date
    else Integer.compare(kind.order, other.kind.order)
}

case class TrophyKind(
    _id: String,
    name: String,
    icon: Option[String],
    url: Option[String],
    klass: Option[String],
    order: Int,
    withCustomImage: Boolean
)

object TrophyKind {
  val marathonWinner = "marathonWinner"
  val marathonTopTen = "marathonTopTen"
  val marathonTopFifty = "marathonTopFifty"
  val marathonTopHundred = "marathonTopHundred"
  val moderator = "moderator"
  val developer = "developer"
  val verified = "verified"
}

