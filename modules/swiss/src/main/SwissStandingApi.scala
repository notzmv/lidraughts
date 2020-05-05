package lidraughts.swiss

import play.api.libs.json._
import scala.concurrent.duration._

import lidraughts.common.LightUser
import lidraughts.memo._
import lidraughts.db.dsl._

/*
 * Getting a standing page of a tournament can be very expensive
 * because it can iterate through thousands of mongodb documents.
 * Try to cache the stuff, and limit concurrent access to prevent
 * overloading mongodb.
 */
final class SwissStandingApi(
    swissColl: Coll,
    playerColl: Coll,
    pairingColl: Coll,
    asyncCache: lidraughts.memo.AsyncCache.Builder,
    lightUserApi: lidraughts.user.LightUserApi
) {

  import BsonHandlers._

  def apply(swiss: Swiss, page: Int): Fu[JsObject] =
    if (page == 1) first get swiss.id
    else if (page > 50) {
      if (swiss.isCreated) createdCache.get(swiss.id -> page)
      else compute(swiss.id, page)
    } else compute(swiss, page)

  private val first = asyncCache.clearable[Swiss.Id, JsObject](
    name = "swiss.page.first",
    f = compute(_, 1),
    expireAfter = _.ExpireAfterWrite(1 second)
  )

  private val createdCache = asyncCache.multi[(Swiss.Id, Int), JsObject](
    name = "swiss.page.createdCache",
    f = c => compute(c._1, c._2),
    expireAfter = _.ExpireAfterWrite(15 second)
  )

  def clearCache(swiss: Swiss): Unit = {
    first invalidate swiss.id
    // no need to invalidate createdCache, these are only cached when tour.isCreated
  }

  private def compute(id: Swiss.Id, page: Int): Fu[JsObject] =
    swissColl.byId[Swiss](id.value) flatten s"No such tournament: $id" flatMap { compute(_, page) }

  private def compute(swiss: Swiss, page: Int): Fu[JsObject] =
    for {
      rankedPlayers <- bestWithRankByPage(swiss.id, 10, page atLeast 1)
      pairings <- SwissPairing.fields { f =>
        pairingColl
          .find($doc(f.swissId -> swiss.id, f.players $in rankedPlayers.map(_.player.number)))
          .sort($sort asc f.round)
          .list[SwissPairing]()
          .map(SwissPairing.toMap)
      }
      users <- lightUserApi asyncMany rankedPlayers.map(_.player.userId)
    } yield Json.obj(
      "page" -> page,
      "players" -> rankedPlayers.zip(users).map {
        case (SwissPlayer.Ranked(rank, player), user) =>
          SwissJson.playerJson(
            swiss,
            SwissPlayer.View(
              player,
              rank,
              user | LightUser.fallback(player.userId),
              ~pairings.get(player.number)
            )
          )
      }
    )

  private[swiss] def bestWithRank(id: Swiss.Id, nb: Int, skip: Int = 0): Fu[List[SwissPlayer.Ranked]] =
    best(id, nb, skip).map { res =>
      res
        .foldRight(List.empty[SwissPlayer.Ranked] -> (res.size + skip)) {
          case (p, (res, rank)) => (SwissPlayer.Ranked(rank, p) :: res, rank - 1)
        }
        ._1
    }

  private[swiss] def bestWithRankByPage(id: Swiss.Id, nb: Int, page: Int): Fu[List[SwissPlayer.Ranked]] =
    bestWithRank(id, nb, (page - 1) * nb)

  private[swiss] def best(id: Swiss.Id, nb: Int, skip: Int = 0): Fu[List[SwissPlayer]] =
    SwissPlayer.fields { f =>
      playerColl.find($doc(f.swissId -> id)).sort($sort desc f.score).skip(skip).list[SwissPlayer](nb)
    }
}
