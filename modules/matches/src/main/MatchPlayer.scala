package lidraughts.matches

import draughts.variant.Variant
import lidraughts.rating.Perf
import lidraughts.user.User

private[matches] case class MatchPlayer(
    user: User.ID,
    variant: Variant,
    rating: Int,
    provisional: Option[Boolean]
) {

  def is(userId: User.ID): Boolean = user == userId
  def is(other: MatchPlayer): Boolean = is(other.user)
}

private[matches] object MatchPlayer {

  private[matches] def make(user: User, variant: Variant, perf: Perf): MatchPlayer = {
    new MatchPlayer(
      user = user.id,
      variant = variant,
      rating = perf.intRating,
      provisional = perf.provisional.some
    )
  }
}
