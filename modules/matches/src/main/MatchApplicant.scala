package lidraughts.matches

case class MatchApplicant(
    player: MatchPlayer,
    accepted: Boolean
) {

  def is(userId: String): Boolean = player is userId
  def is(other: MatchPlayer): Boolean = player is other
}

private[matches] object MatchApplicant {

  def make(player: MatchPlayer): MatchApplicant = new MatchApplicant(
    player = player,
    accepted = false
  )
}
