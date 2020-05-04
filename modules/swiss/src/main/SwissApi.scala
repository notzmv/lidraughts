package lidraughts.swiss

import akka.actor.ActorSystem
import org.joda.time.DateTime
import ornicar.scalalib.Zero
import reactivemongo.api._
import reactivemongo.api.collections.bson.BSONBatchCommands.AggregationFramework._
import reactivemongo.bson._
import scala.concurrent.duration._
import scala.concurrent.Promise

import actorApi._
import lidraughts.db.dsl._
import lidraughts.common.GreatPlayer
import lidraughts.hub.lightTeam.TeamId
import lidraughts.hub.{ Duct, DuctMap }
import lidraughts.game.Game
import lidraughts.user.User

final class SwissApi(
    swissColl: Coll,
    playerColl: Coll,
    pairingColl: Coll,
    system: ActorSystem,
    sequencers: DuctMap[_],
    socketMap: SocketMap,
    director: SwissDirector,
    scoring: SwissScoring
) {

  import BsonHandlers._

  def byId(id: Swiss.Id) = swissColl.byId[Swiss](id.value)
  def notFinishedById(id: Swiss.Id) = byId(id).dmap(_.filter(_.isNotFinished))
  def createdById(id: Swiss.Id) = byId(id).dmap(_.filter(_.isCreated))
  def startedById(id: Swiss.Id) = byId(id).dmap(_.filter(_.isStarted))

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
      nextRoundAt = data.startsAt.some,
      startsAt = data.startsAt,
      finishedAt = none,
      winnerId = none,
      description = data.description,
      hasChat = data.hasChat | true
    )
    swissColl.insert(swiss) inject swiss
  }

  def update(old: Swiss, data: SwissForm.SwissData): Funit = {
    val swiss = old.copy(
      name = data.name | old.name,
      clock = data.clock,
      variant = data.realVariant,
      rated = data.rated | old.rated,
      nbRounds = data.nbRounds,
      startsAt = data.startsAt,
      description = data.description,
      hasChat = data.hasChat | old.hasChat
    )
    swissColl.update($id(swiss.id), swiss).void
  }

  def join(
    id: Swiss.Id,
    me: User,
    isInTeam: TeamId => Boolean,
    promise: Option[Promise[Boolean]]
  ): Unit = Sequencing(id)(notFinishedById) { swiss =>
    val fuJoined =
      isInTeam(swiss.teamId) ?? {
        val number = SwissPlayer.Number(swiss.nbPlayers + 1).pp
        playerColl.insert(SwissPlayer.make(swiss.id, number, me, swiss.perfLens)) zip
          swissColl.updateField($id(swiss.id), "nbPlayers", number) >>-
          socketReload(swiss.id) inject true
      }
    fuJoined map {
      joined => promise.foreach(_ success joined)
    }
  }

  def joinWithResult(
    id: Swiss.Id,
    me: User,
    isInTeam: TeamId => Boolean
  ): Fu[Boolean] = {
    val promise = Promise[Boolean]
    join(id, me, isInTeam, promise.some)
    promise.future.withTimeoutDefault(5.seconds, false)(system)
  }

  def pairingsOf(swiss: Swiss) = SwissPairing.fields { f =>
    pairingColl
      .find($doc(f.swissId -> swiss.id))
      .sort($sort asc f.round)
      .list[SwissPairing]()
  }

  def featuredInTeam(teamId: TeamId): Fu[List[Swiss]] =
    swissColl.find($doc("teamId" -> teamId)).sort($sort desc "startsAt").list[Swiss](5)

  private[swiss] def finishGame(game: Game): Unit = game.swissId foreach { swissId =>
    Sequencing(Swiss.Id(swissId))(startedById) { swiss =>
      pairingColl.byId[SwissPairing](game.id) flatMap {
        _ ?? { pairing =>
          val winner = game.winnerColor
            .map(_.fold(pairing.white, pairing.black))
            .flatMap(playerNumberHandler.writeOpt)
          winner.fold(pairingColl.updateField($id(game.id), SwissPairing.Fields.status, BSONNull))(
            pairingColl.updateField($id(game.id), SwissPairing.Fields.status, _)
          ).void >>
            scoring.recompute(swiss) >> {
              if (swiss.round.value == swiss.nbRounds) doFinish(swiss)
              else
                isCurrentRoundFinished(swiss).flatMap {
                  _ ?? swissColl.updateField($id(swiss.id), "nextRoundAt", DateTime.now.plusSeconds(10)).void
                }
            } >>- socketReload(swiss.id)
        }
      }
    }
  }

  private def isCurrentRoundFinished(swiss: Swiss) =
    SwissPairing
      .fields { f =>
        !pairingColl.exists(
          $doc(f.swissId -> swiss.id, f.round -> swiss.round, f.status -> SwissPairing.ongoing)
        )
      }

  private[swiss] def destroy(swiss: Swiss): Funit =
    swissColl.remove($id(swiss.id)) >>
      pairingColl.remove($doc(SwissPairing.Fields.swissId -> swiss.id)) >>
      playerColl.remove($doc(SwissPairing.Fields.swissId -> swiss.id)).void >>-
      socketReload(swiss.id)

  private[swiss] def finish(oldSwiss: Swiss): Unit =
    Sequencing(oldSwiss.id)(startedById) { swiss =>
      pairingColl.countSel($doc(SwissPairing.Fields.swissId -> swiss.id)) flatMap {
        case 0 => destroy(swiss)
        case _ => doFinish(swiss: Swiss)
      }
    }
  private def doFinish(swiss: Swiss): Funit =
    for {
      _ <- swissColl.updateField($id(swiss.id), "finishedAt", DateTime.now).void
      winner <- SwissPlayer.fields { f =>
        playerColl.find($doc(f.swissId -> swiss.id)).sort($sort desc f.score).one[SwissPlayer]
      }
      _ <- winner.?? { p =>
        swissColl.updateField($id(swiss.id), "winnerId", p.userId).void
      }
    } yield socketReload(swiss.id)

  def kill(swiss: Swiss): Unit = {
    if (swiss.isStarted) finish(swiss)
    else if (swiss.isCreated) destroy(swiss)
  }

  private[swiss] def tick: Funit =
    swissColl
      .find($doc("nextRoundAt" $lt DateTime.now), $id(true))
      .list[Bdoc](10)
      .map(_.flatMap(_.getAs[Swiss.Id]("_id")))
      .flatMap { ids =>
        lidraughts.common.Future.applySequentially(ids) { id =>
          val promise = Promise[Boolean]
          Sequencing(id)(notFinishedById) { swiss =>
            val fuRound = director.startRound(swiss).flatMap(scoring.recompute) >>-
              socketReload(swiss.id) inject true
            fuRound map promise.success
          }
          promise.future.withTimeoutDefault(15.seconds, false)(system).void
        }
      }

  private def Sequencing(swissId: Swiss.Id)(fetch: Swiss.Id => Fu[Option[Swiss]])(run: Swiss => Funit): Unit =
    doSequence(swissId) {
      fetch(swissId) flatMap {
        case Some(t) => run(t)
        case None => fufail(s"Can't run sequenced operation on missing swiss $swissId")
      }
    }

  private def doSequence(swissId: Swiss.Id)(fu: => Funit): Unit =
    sequencers.tell(swissId.value, Duct.extra.LazyFu(() => fu))

  private def socketReload(swissId: Swiss.Id): Unit =
    socketMap.tell(swissId.value, Reload)
}
