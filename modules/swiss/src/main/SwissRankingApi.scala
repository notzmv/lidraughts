package lidraughts.swiss

import scala.concurrent.duration._
import reactivemongo.bson._

import lidraughts.db.dsl._

final private class SwissRankingApi(
    playerColl: Coll,
    asyncCache: lidraughts.memo.AsyncCache.Builder
) {
  import BsonHandlers._

  def apply(swiss: Swiss): Fu[Ranking] =
    if (swiss.isFinished) finishedRanking get swiss.id
    else ongoingRanking get swiss.id

  // only applies to ongoing tournaments
  private val ongoingRanking = asyncCache.multi[Swiss.Id, Ranking](
    name = "swiss.ongoingRanking",
    f = computeRanking,
    expireAfter = _.ExpireAfterWrite(3 seconds)
  )

  // only applies to finished tournaments
  private val finishedRanking = asyncCache.multi[Swiss.Id, Ranking](
    name = "swiss.finishedRanking",
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
          Group(BSONNull)("players" -> PushField(f.number))
        )
      }
      .headOption map {
        _ ?? {
          _ get "players" match {
            case Some(BSONArray(players)) =>
              // mutable optimized implementation
              val b = Map.newBuilder[SwissPlayer.Number, Int]
              var r = 0
              for (u <- players) {
                b += (SwissPlayer.Number(u.get.asInstanceOf[BSONInteger].value) -> r)
                r = r + 1
              }
              b.result
            case _ => Map.empty
          }
        }
      }
  }
}
