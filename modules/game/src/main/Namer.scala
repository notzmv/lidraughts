package lidraughts.game

import lidraughts.common.LightUser
import lidraughts.user.User

object Namer {

  def playerText(player: Player, withRating: Boolean = false)(implicit lightUser: LightUser.GetterSync): String =
    player.aiLevel.fold(
      player.userId.flatMap(lightUser).fold(player.name | "Anon.") { u =>
        player.rating.ifTrue(withRating).fold(u.titleName) { r => s"${u.titleName} ($r)" }
      }
    ) { level => s"A.I. level $level" }

  def gameVsText(game: Game, withRatings: Boolean = false)(implicit lightUser: LightUser.GetterSync): String =
    s"${playerText(game.whitePlayer, withRatings)} - ${playerText(game.blackPlayer, withRatings)}"

  def ratingString(p: Player) = p.rating match {
    case Some(rating) => s"$rating${if (p.provisional) "?" else ""}"
    case _ => "?"
  }

  def ratingStringIfUser(p: Player) =
    (p.aiLevel.isEmpty && p.userId.isDefined) option ratingString(p)
}
