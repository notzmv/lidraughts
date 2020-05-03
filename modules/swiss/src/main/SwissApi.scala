package lidraughts.swiss

import org.joda.time.DateTime
import reactivemongo.api._
import reactivemongo.api.collections.bson.BSONBatchCommands.AggregationFramework._

import lidraughts.db.dsl._
import lidraughts.common.GreatPlayer
import lidraughts.hub.lightTeam.TeamId
import lidraughts.user.User

final class SwissApi(
    swissColl: Coll,
    playerColl: Coll,
    pairingColl: Coll
) {

  import BsonHandlers._

  def byId(id: Swiss.Id) = swissColl.byId[Swiss](id.value)

  def create(data: SwissForm.SwissData, me: User, teamId: TeamId): Fu[Swiss] = {
    val swiss = Swiss(
      _id = Swiss.makeId,
      name = data.name | GreatPlayer.randomName,
      clock = data.clock,
      variant = data.realVariant,
      rated = data.rated | true,
      round = SwissRound.Number(0),
      nbRounds = data.nbRounds,
      nbPlayers = 0,
      createdAt = DateTime.now,
      createdBy = me.id,
      teamId = teamId,
      startsAt = data.startsAt,
      finishedAt = none,
      winnerId = none,
      description = data.description,
      hasChat = data.hasChat | true
    )
    swissColl.insert(swiss) inject swiss
  }

  // def leaderboard(swiss: Swiss, page: Int): Fu[List[LeaderboardPlayer]] =
  //   playerColl
  //     .aggregateList(
  //       Match($doc("s" -> swiss.id)), List(
  //         Sort(Descending("t")),
  //         Skip((page - 1) * 10),
  //         Limit(10),
  //         PipelineOperator(
  //           $doc(
  //             "$lookup" -> $doc(
  //               "from" -> pairingColl.name,
  //               "let" -> $doc("n" -> "$n"),
  //               "pipeline" -> $arr(
  //                 $doc(
  //                   "$match" -> $doc(
  //                     "$expr" -> $doc(
  //                       "$and" -> $arr(
  //                         $doc("s" -> swiss.id),
  //                         $doc("u" -> "$$n")
  //                       )
  //                     )
  //                   )
  //                 )
  //               ),
  //               "as" -> "pairings"
  //             )
  //           )
  //         )
  //       ),
  //       maxDocs = 10,
  //       readPreference = ReadPreference.secondaryPreferred
  //     ).map {
  //         _ map { doc =>
  //           LeaderboardPlayer(
  //             playerHandler.read(doc),
  //             (~doc.getAs[List[SwissPairing]]("pairings")).map { p =>
  //               p.round -> p
  //             }.toMap
  //           )
  //         }
  //       }

  def pairingsOf(swiss: Swiss) =
    pairingColl.find($doc("s" -> swiss.id)).sort($sort asc "r").list[SwissPairing]()

  def featuredInTeam(teamId: TeamId): Fu[List[Swiss]] =
    swissColl.find($doc("teamId" -> teamId)).sort($sort desc "startsAt").list[Swiss](5)

  private def insertPairing(pairing: SwissPairing) =
    pairingColl.insert {
      pairingHandler.write(pairing) ++ $doc("d" -> DateTime.now)
    }.void
}
