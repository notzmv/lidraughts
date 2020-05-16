package lidraughts.swiss

import com.github.blemale.scaffeine.Scaffeine
import reactivemongo.bson._
import scala.concurrent.duration._

import lidraughts.db.dsl._
import lidraughts.user.User
import scala.util.Success

final private class SwissRankingApi(
    playerColl: Coll,
    asyncCache: lidraughts.memo.AsyncCache.Builder
) {
  import BsonHandlers._

  def apply(swiss: Swiss): Fu[Ranking] =
    fuccess(scoreCache.getIfPresent(swiss.id)) getOrElse {
      dbCache get swiss.id
    }

  def update(res: SwissScoring.Result): Unit =
    scoreCache.put(
      res.swiss.id,
      res.leaderboard.zipWithIndex.map {
        case ((p, _), i) => p.userId -> (i + 1)
      }.toMap
    )

  private val scoreCache = Scaffeine()
    .expireAfterWrite(60 minutes)
    .build[Swiss.Id, Ranking]

  private val dbCache = asyncCache.multi[Swiss.Id, Ranking](
    name = "swiss.ranking",
    maxCapacity = 1024,
    f = computeRanking,
    expireAfter = _.ExpireAfterAccess(1 hour)
  )

  private def computeRanking(id: Swiss.Id): Fu[Ranking] =
    SwissPlayer.fields { f =>
      playerColl.primitive[User.ID]($doc(f.swissId -> id), $sort desc f.score, f.userId)
    } map {
      _.view.zipWithIndex.map {
        case (user, i) => (user, i + 1)
      }.toMap
    }
}
