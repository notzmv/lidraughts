package lidraughts.game

import draughts.Color
import java.security.SecureRandom
import ornicar.scalalib.Random

import lidraughts.db.dsl._

object IdGenerator {

  def uncheckedGame: Game.ID = Random nextString Game.gameIdSize

  def game: Fu[Game.ID] = {
    val id = uncheckedGame
    GameRepo.exists(id).flatMap {
      case true => game
      case false => fuccess(id)
    }
  }

  def games(nb: Int): Fu[List[Game.ID]] = {
    if (nb < 1) fuccess(List.empty)
    else if (nb == 1) game.dmap(List(_))
    else if (nb < 5) List.fill(nb)(game).sequenceFu
    else {
      val ids = List.fill(nb)(uncheckedGame)
      GameRepo.coll.distinct[Game.ID, List]("_id", $inIds(ids).some) flatMap { collisions =>
        games(collisions.size) dmap { _ ++ (ids diff collisions) }
      }
    }
  }

  private[this] val secureRandom = new SecureRandom()
  private[this] val whiteSuffixChars = ('0' to '4') ++ ('A' to 'Z') mkString
  private[this] val blackSuffixChars = ('5' to '9') ++ ('a' to 'z') mkString

  def player(color: Color): Player.ID = {
    // Trick to avoid collisions between player ids in the same game.
    val suffixChars = color.fold(whiteSuffixChars, blackSuffixChars)
    val suffix = suffixChars(secureRandom nextInt suffixChars.size)
    Random.secureString(Game.playerIdSize - 1) + suffix
  }
}
