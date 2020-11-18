package lidraughts.swiss

import lidraughts.db.dsl._

private case class SwissSheet(outcomes: List[SwissSheet.Outcome]) {
  import SwissSheet._

  def points =
    Swiss.Points {
      outcomes.foldLeft(0) { case (acc, out) => acc + pointsFor(out) }
    }
}

private object SwissSheet {

  sealed trait Outcome
  case object Bye extends Outcome
  case object Late extends Outcome // missed the first round
  case object Absent extends Outcome
  case object Ongoing extends Outcome
  case object Win extends Outcome
  case object Loss extends Outcome
  case object Draw extends Outcome

  def pointsFor(outcome: Outcome) =
    outcome match {
      case Win | Bye => 2
      case Late | Draw => 1
      case _ => 0
    }

  def many(
    swiss: Swiss,
    players: List[SwissPlayer],
    pairingMap: SwissPairing.PairingMap
  ): List[SwissSheet] =
    players.map { player =>
      one(swiss, ~pairingMap.get(player.userId), player)
    }

  def one(
    swiss: Swiss,
    pairingMap: Map[SwissRound.Number, SwissPairing],
    player: SwissPlayer
  ): SwissSheet =
    SwissSheet {
      swiss.allRounds.map { round =>
        pairingMap get round match {
          case Some(pairing) =>
            pairing.status match {
              case Left(_) => Ongoing
              case Right(None) => Draw
              case Right(Some(color)) => if (pairing(color) == player.userId) Win else Loss
            }
          case None if player.byes(round) => Bye
          case None if round.value == 1 => Late
          case None => Absent
        }
      }
    }

}

final private class SwissSheetApi(
    playerColl: Coll,
    pairingColl: Coll
) {

  import org.joda.time.DateTime
  import reactivemongo.api.collections.bson.BSONBatchCommands.AggregationFramework.{ Descending, Match, PipelineOperator, Sort }
  import reactivemongo.api.ReadPreference
  import BsonHandlers._

  def source(
    swiss: Swiss,
    sort: Bdoc
  ): Fu[List[(SwissPlayer, Map[SwissRound.Number, SwissPairing], SwissSheet)]] = {
    val readPreference =
      if (swiss.finishedAt.exists(_ isBefore DateTime.now.minusSeconds(10)))
        ReadPreference.secondaryPreferred
      else ReadPreference.primary
    SwissPlayer
      .fields { f =>
        playerColl
          .find($doc(f.swissId -> swiss.id))
          .sort(sort)
      }
      .cursor[SwissPlayer](readPreference)
      .gather[List]() flatMap {
        _.map { player =>
          SwissPairing.fields { f =>
            pairingColl.list[SwissPairing](
              $doc(f.swissId -> swiss.id, f.players -> player.userId),
              readPreference
            ) dmap { player -> _ }
          }
            .map {
              case (player, pairings) =>
                val pairingMap = pairings.map { p =>
                  p.round -> p
                }.toMap
                (player, pairingMap, SwissSheet.one(swiss, pairingMap, player))
            }
        }.sequenceFu
      }

  }
}
