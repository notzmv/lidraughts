package lidraughts.matches

private[matches] sealed abstract class MatchStatus(val id: Int) extends Ordered[MatchStatus] {

  def compare(other: MatchStatus) = id compare other.id

  def name = toString

  def is(s: MatchStatus): Boolean = this == s
  def is(f: MatchStatus.type => MatchStatus): Boolean = is(f(MatchStatus))
}

private[matches] object MatchStatus {

  case object Created extends MatchStatus(10)
  case object Started extends MatchStatus(20)
  case object Finished extends MatchStatus(30)

  val all = List(Created, Started, Finished)

  val byId = all map { v => (v.id, v) } toMap

  def apply(id: Int): Option[MatchStatus] = byId get id
}
