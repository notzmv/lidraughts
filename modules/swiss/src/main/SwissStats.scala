package lidraughts.swiss

import reactivemongo.api.collections.bson.BSONBatchCommands.AggregationFramework.{ Match, PipelineOperator }
import reactivemongo.api.ReadPreference
import reactivemongo.bson._
import scala.concurrent.duration._

import lidraughts.db.dsl._

case class SwissStats(
    games: Int = 0,
    whiteWins: Int = 0,
    blackWins: Int = 0,
    draws: Int = 0,
    byes: Int = 0,
    absences: Int = 0,
    averageRating: Int = 0
)

final class SwissStatsApi(
    swissColl: Coll,
    playerColl: Coll,
    pairingColl: Coll,
    mongoCache: lidraughts.memo.MongoCache.Builder
) {

  import BsonHandlers._

  def apply(swiss: Swiss): Fu[Option[SwissStats]] =
    swiss.isFinished ?? cache(swiss.id).dmap(some).dmap(_.filter(_.games > 0))

  implicit private val statsBSONHandler = Macros.handler[SwissStats]

  private val cache = mongoCache[Swiss.Id, SwissStats](
    prefix = "swiss:stats",
    keyToString = _.value,
    f = fetch,
    timeToLive = 10 minutes,
    timeToLiveMongo = 60.days.some
  )

  private def fetch(id: Swiss.Id): Fu[SwissStats] =
    swissColl.byId[Swiss](id.value) flatMap {
      _.filter(_.nbPlayers > 0).fold(fuccess(SwissStats())) { swiss =>
        SwissPlayer.fields { f =>
          playerColl
            .aggregateList(
              Match($doc(f.swissId -> id)),
              List(
                PipelineOperator(
                  $doc(
                    "$lookup" -> $doc(
                      "from" -> pairingColl.name,
                      "let" -> $doc("n" -> "$n"),
                      "pipeline" -> $arr(
                        $doc(
                          "$match" -> $doc(
                            "$expr" -> $doc(
                              "$and" -> $arr(
                                $doc("$eq" -> $arr("$s", id)),
                                $doc("$in" -> $arr("$$n", "$p"))
                              )
                            )
                          )
                        )
                      ),
                      "as" -> "pairings"
                    )
                  )
                )
              ),
              maxDocs = Int.MaxValue,
              readPreference = ReadPreference.secondaryPreferred
            )
            .map {
              _.flatMap { doc =>
                val result = for {
                  player <- playerHandler.readOpt(doc)
                  pairings <- doc.getAs[List[SwissPairing]]("pairings")
                  pairingMap = pairings.map { p =>
                    p.round -> p
                  }.toMap
                } yield (player, pairings, SwissSheet.one(swiss, pairingMap, player))
                result.toList
              }.foldLeft(SwissStats()) {
                case (stats, (player, pairings, sheet)) =>
                  pairings.foldLeft((0, 0, 0, 0)) {
                    case ((games, whiteWins, blackWins, draws), pairing) =>
                      val counts = pairing.white == player.userId
                      (
                        games + counts.??(1),
                        whiteWins + (counts && pairing.whiteWins).??(1),
                        blackWins + (counts && pairing.blackWins).??(1),
                        draws + (counts && pairing.isDraw).??(1)
                      )
                  } match {
                    case (games, whiteWins, blackWins, draws) =>
                      sheet.outcomes.foldLeft((0, 0)) {
                        case ((byes, absences), outcome) =>
                          (
                            byes + (outcome == SwissSheet.Bye).??(1),
                            absences + (outcome == SwissSheet.Absent).??(1)
                          )
                      } match {
                        case (byes, absences) =>
                          stats.copy(
                            games = stats.games + games,
                            whiteWins = stats.whiteWins + whiteWins,
                            blackWins = stats.blackWins + blackWins,
                            draws = stats.draws + draws,
                            byes = stats.byes + byes,
                            absences = stats.absences + absences,
                            averageRating = stats.averageRating + player.rating
                          )
                      }
                  }
              }
            }
            .dmap(s => s.copy(averageRating = s.averageRating / swiss.nbPlayers))
        }
      }
    }
}
