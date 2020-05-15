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

  private def computeRanking(id: Swiss.Id): Fu[Ranking] = SwissPlayer.fields { f =>
    playerColl
      .aggregateWith[Bdoc]() { framework =>
        import framework._
        Match($doc(f.swissId -> id)) -> List(
          Sort(Descending(f.score)),
          Group(BSONNull)("players" -> PushField(f.userId))
        )
      }
      .headOption map {
        _ ?? {
          _ get "players" match {
            case Some(BSONArray(players)) =>
              // mutable optimized implementation
              val b = Map.newBuilder[User.ID, Int]
              var r = 0
              for (u <- players) {
                u match {
                  case Success(v) =>
                    b += (v.asInstanceOf[BSONString].value -> r)
                    r = r + 1
                  case _ =>
                }
              }
              b.result
            case _ => Map.empty
          }
        }
      }
  }
}
