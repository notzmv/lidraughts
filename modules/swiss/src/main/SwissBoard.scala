package lidraughts.swiss

import scala.concurrent.duration._

import lidraughts.common.LightUser
import lidraughts.game.Game
import lidraughts.db.dsl._

case class SwissBoard(
    gameId: Game.ID,
    p1: SwissBoard.Player,
    p2: SwissBoard.Player
)

object SwissBoard {
  case class Player(player: SwissPlayer, user: LightUser, rank: Int)
}

final class SwissBoardApi(
    swissColl: Coll,
    asyncCache: lidraughts.memo.AsyncCache.Builder,
    lightUserApi: lidraughts.user.LightUserApi
) {

  private val cache = asyncCache.multi[Swiss.Id, List[SwissBoard]](
    name = "swiss.boards",
    f = compute,
    expireAfter = _.ExpireAfterWrite(15 second)
  )

  def get(swiss: Swiss) = cache.get(swiss.id)

  private def compute(id: Swiss.Id): Fu[List[SwissBoard]] = ???
}
