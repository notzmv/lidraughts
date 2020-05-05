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
import lidraughts.chat.{ Chat, ChatApi }
import lidraughts.common.{ Bus, GreatPlayer }
import lidraughts.db.dsl._
import lidraughts.game.Game
import lidraughts.hub.lightTeam.TeamId
import lidraughts.hub.{ Duct, DuctMap }
import lidraughts.round.actorApi.round.QuietFlag
import lidraughts.user.User

final class SwissApi(
    swissColl: Coll,
    playerColl: Coll,
    pairingColl: Coll,
    system: ActorSystem,
    sequencers: DuctMap[_],
    socketMap: SocketMap,
    director: SwissDirector,
    scoring: SwissScoring,
    chatApi: ChatApi,
    bus: Bus
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
      nbOngoing = 0,
      createdAt = DateTime.now,
      createdBy = me.id,
      teamId = teamId,
      nextRoundAt = data.realStartsAt.some,
      startsAt = data.realStartsAt,
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
      startsAt = data.startsAt.ifTrue(old.isCreated) | old.startsAt,
      nextRoundAt = if (old.isCreated) Some(data.startsAt | old.startsAt) else old.nextRoundAt,
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
      (swiss.isEnterable && isInTeam(swiss.teamId)) ?? {
        val number = SwissPlayer.Number(swiss.nbPlayers + 1)
        playerColl.insert(SwissPlayer.make(swiss.id, number, me, swiss.perfLens)) zip
          swissColl.updateField($id(swiss.id), "nbPlayers", number) >>
          scoring.recompute(swiss) >>-
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
      pairingColl.byId[SwissPairing](game.id).dmap(_.filter(_.isOngoing)) flatMap {
        _ ?? { pairing =>
          val winner = game.winnerColor
            .map(_.fold(pairing.white, pairing.black))
            .flatMap(playerNumberHandler.writeOpt)
          winner.fold(pairingColl.updateField($id(game.id), SwissPairing.Fields.status, BSONNull))(
            pairingColl.updateField($id(game.id), SwissPairing.Fields.status, _)
          ).void >>
            swissColl.update($id(swiss.id), $inc("nbOngoing" -> -1)) >>
            scoring.recompute(swiss) >> {
              if (swiss.round.value == swiss.nbRounds) doFinish(swiss)
              else if (swiss.nbOngoing == 1) {
                val minutes = 1
                swissColl
                  .updateField($id(swiss.id), "nextRoundAt", DateTime.now.plusMinutes(minutes))
                  .void >>-
                  systemChat(
                    swiss.id,
                    s"Round ${swiss.round.value + 1} will start soon."
                  )
              } else funit
            } >>- socketReload(swiss.id)
        }
      }
    }
  }

  // private def isCurrentRoundFinished(swiss: Swiss) =
  //   SwissPairing
  //     .fields { f =>
  //       !pairingColl.exists(
  //         $doc(f.swissId -> swiss.id, f.round -> swiss.round, f.status -> SwissPairing.ongoing)
  //       )
  //     }

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
      _ <- swissColl
        .update(
          $id(swiss.id),
          $unset("nextRoundAt") ++ $set(
            "nbRounds" -> swiss.round,
            "finishedAt" -> DateTime.now
          )
        )
        .void
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

  private[swiss] def startPendingRounds: Funit =
    swissColl
      .find($doc("nextRoundAt" $lt DateTime.now), $id(true))
      .list[Bdoc](10)
      .map(_.flatMap(_.getAs[Swiss.Id]("_id")))
      .flatMap { ids =>
        lidraughts.common.Future.applySequentially(ids) { id =>
          val promise = Promise[Boolean]
          Sequencing(id)(notFinishedById) { swiss =>
            val fu =
              if (swiss.nbPlayers >= 4)
                director.startRound(swiss).flatMap {
                  _.fold(
                    doFinish(swiss) >>-
                      systemChat(
                        swiss.id,
                        s"Not enough players for round ${swiss.round.value + 1}; terminating tournament."
                      )
                  ) { s =>
                      scoring.recompute(s) >>-
                        systemChat(
                          swiss.id,
                          s"Round ${swiss.round.value + 1} started."
                        )
                    }
                }
              else {
                if (swiss.startsAt isBefore DateTime.now.minusMinutes(60)) destroy(swiss)
                else {
                  systemChat(swiss.id, "Not enough players for first round; delaying start.", true)
                  swissColl
                    .update($id(swiss.id), $set("nextRoundAt" -> DateTime.now.plusSeconds(21)))
                    .void
                }
              }
            val fuCompleted = fu >>- socketReload(swiss.id) inject true
            fuCompleted map promise.success
          }
          promise.future.withTimeoutDefault(15.seconds, false)(system).void
        }
      }

  private[swiss] def checkOngoingGames: Funit =
    SwissPairing.fields { f =>
      pairingColl.primitive[Game.ID]($doc(f.status -> SwissPairing.ongoing), f.id)
    } map { gameIds =>
      bus.publish(lidraughts.hub.actorApi.map.TellMany(gameIds, QuietFlag), 'roundSocket)
    }

  private def systemChat(id: Swiss.Id, text: String, volatile: Boolean = false): Unit =
    chatApi.userChat.service(
      Chat.Id(id.value),
      text,
      volatile
    )

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
