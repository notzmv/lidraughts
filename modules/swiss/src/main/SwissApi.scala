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
import lidraughts.common.{ Bus, GreatPlayer, LightUser }
import lidraughts.db.dsl._
import lidraughts.game.Game
import lidraughts.hub.lightTeam.TeamId
import lidraughts.hub.{ Duct, DuctMap }
import lidraughts.round.actorApi.round.QuietFlag
import lidraughts.user.{ User, UserRepo }

final class SwissApi(
    swissColl: Coll,
    playerColl: Coll,
    pairingColl: Coll,
    cache: SwissCache,
    system: ActorSystem,
    sequencers: DuctMap[_],
    socketMap: SocketMap,
    director: SwissDirector,
    scoring: SwissScoring,
    chatApi: ChatApi,
    lightUserApi: lidraughts.user.LightUserApi,
    proxyGames: List[Game.ID] => Fu[List[Game]],
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
      round = SwissRound.Number(0),
      nbPlayers = 0,
      nbOngoing = 0,
      createdAt = DateTime.now,
      createdBy = me.id,
      teamId = teamId,
      nextRoundAt = data.realStartsAt.some,
      startsAt = data.realStartsAt,
      finishedAt = none,
      winnerId = none,
      settings = Swiss.Settings(
        nbRounds = data.nbRounds,
        rated = data.rated | true,
        description = data.description,
        hasChat = data.hasChat | true,
        roundInterval = data.realRoundInterval
      )
    )
    swissColl.insert(swiss) inject swiss
  }

  def update(old: Swiss, data: SwissForm.SwissData): Funit = {
    val swiss = old.copy(
      name = data.name | old.name,
      clock = data.clock,
      variant = data.realVariant,
      startsAt = data.startsAt.ifTrue(old.isCreated) | old.startsAt,
      nextRoundAt = if (old.isCreated) Some(data.startsAt | old.startsAt) else old.nextRoundAt,
      settings = old.settings.copy(
        nbRounds = data.nbRounds,
        rated = data.rated | old.settings.rated,
        description = data.description,
        hasChat = data.hasChat | old.settings.hasChat,
        roundInterval = data.roundInterval.fold(old.settings.roundInterval)(_.seconds)
      )
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
      playerColl // try a rejoin first
        .updateField($id(SwissPlayer.makeId(swiss.id, me.id)), SwissPlayer.Fields.absent, false)
        .flatMap { rejoin =>
          fuccess(rejoin.nModified == 1) >>| { // if it failed, try a join
            (swiss.isEnterable && isInTeam(swiss.teamId)) ?? {
              val number = SwissPlayer.Number(swiss.nbPlayers + 1)
              playerColl.insert(SwissPlayer.make(swiss.id, number, me, swiss.perfLens)) zip
                swissColl.updateField($id(swiss.id), "nbPlayers", number) inject true
            }
          } flatMap { res =>
            scoring.recompute(swiss) >>- socketReload(swiss.id) inject res
          }
        } addEffect { _ => }
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

  def withdraw(id: Swiss.Id, me: User): Unit =
    Sequencing(id)(notFinishedById) { swiss =>
      playerColl
        .updateField($id(SwissPlayer.makeId(swiss.id, me.id)), SwissPlayer.Fields.absent, true)
        .void >>-
        socketReload(swiss.id)
    }

  def pairingsOf(swiss: Swiss) = SwissPairing.fields { f =>
    pairingColl
      .find($doc(f.swissId -> swiss.id))
      .sort($sort asc f.round)
      .list[SwissPairing]()
  }

  def featuredInTeam(teamId: TeamId): Fu[List[Swiss]] =
    cache.featuredInTeamCache.get(teamId) flatMap { ids =>
      swissColl.byOrderedIds[Swiss, Swiss.Id](ids)(_.id)
    }

  def visibleInTeam(teamId: TeamId, nb: Int): Fu[List[Swiss]] =
    swissColl.find($doc("teamId" -> teamId)).sort($sort desc "startsAt").list[Swiss](nb)

  def playerInfo(swiss: Swiss, userId: User.ID): Fu[Option[SwissPlayer.ViewExt]] =
    UserRepo named userId flatMap {
      _ ?? { user =>
        playerColl.byId[SwissPlayer](SwissPlayer.makeId(swiss.id, user.id).value) flatMap {
          _ ?? { player =>
            SwissPairing.fields { f =>
              pairingColl
                .find($doc(f.swissId -> swiss.id, f.players -> player.number))
                .sort($sort asc f.round)
                .list[SwissPairing]()
            } flatMap {
              pairingViews(_, player)
            } flatMap { pairings =>
              SwissPlayer.fields { f =>
                playerColl.countSel($doc(f.swissId -> swiss.id, f.score $gt player.score)).dmap(1.+)
              } map { rank =>
                val pairingMap = pairings.view.map { p =>
                  p.pairing.round -> p
                }.toMap
                SwissPlayer
                  .ViewExt(
                    player,
                    rank,
                    user.light,
                    pairingMap,
                    SwissSheet.one(swiss, pairingMap.view.map { case (r, p) => (r, p.pairing) }.toMap, player)
                  )
                  .some
              }
            }
          }
        }
      }
    }

  def pairingViews(pairings: Seq[SwissPairing], player: SwissPlayer): Fu[Seq[SwissPairing.View]] =
    pairings.headOption ?? { first =>
      SwissPlayer.fields { f =>
        playerColl
          .find($doc(f.swissId -> first.swissId, f.number $in pairings.map(_ opponentOf player.number)))
          .list[SwissPlayer]()
      } flatMap { opponents =>
        lightUserApi asyncMany opponents.map(_.userId) map { users =>
          opponents.zip(users) map {
            case (o, u) => SwissPlayer.WithUser(o, u | LightUser.fallback(o.userId))
          }
        } map { opponents =>
          pairings flatMap { pairing =>
            opponents.find(_.player.number == pairing.opponentOf(player.number)) map {
              SwissPairing.View(pairing, _)
            }
          }
        }
      }
    }

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
            scoring.recompute(swiss) >>
            game.playerWhoDidNotMove.flatMap(_.userId).?? { absent =>
              SwissPlayer.fields { f =>
                playerColl.updateField($doc(f.swissId -> swiss.id, f.userId -> absent), f.absent, true).void
              }
            } >> {
              if (swiss.round.value == swiss.settings.nbRounds) doFinish(swiss)
              else if (swiss.nbOngoing == 1)
                swissColl
                  .updateField($id(swiss.id), "nextRoundAt", DateTime.now.plusSeconds(swiss.settings.roundInterval.toSeconds.toInt))
                  .void >>-
                  systemChat(swiss.id, s"Round ${swiss.round.value + 1} will start soon.")
              else funit
            } >>- socketReload(swiss.id)
        }
      }
    }
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
        playerColl.find($doc(f.swissId -> swiss.id)).sort($sort desc f.score).uno[SwissPlayer]
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
                        s"All possible pairings were played. The tournament is complete."
                      )
                  ) {
                      case s if s.nextRoundAt.isEmpty =>
                        scoring.recompute(s) >>-
                          systemChat(swiss.id, s"Round ${swiss.round.value + 1} started.")
                      case s =>
                        swissColl
                          .update($id(swiss.id), $set("nextRoundAt" -> DateTime.now.plusSeconds(61)))
                          .void >>-
                          systemChat(swiss.id, s"Round ${swiss.round.value + 1} failed.", true)
                    }
                }
              else {
                if (swiss.startsAt isBefore DateTime.now.minusMinutes(60)) destroy(swiss)
                else {
                  systemChat(swiss.id, "Not enough players for first round; delaying start.", true)
                  swissColl
                    .update($id(swiss.id), $set("nextRoundAt" -> DateTime.now.plusSeconds(121)))
                    .void
                }
              }
            val fuCompleted = fu >>- socketReloadImmediately(swiss.id) inject true
            fuCompleted map promise.success
          }
          promise.future.withTimeoutDefault(15.seconds, false)(system).void
        }
      }

  private[swiss] def checkOngoingGames: Funit =
    SwissPairing.fields { f =>
      pairingColl.primitive[Game.ID]($doc(f.status -> SwissPairing.ongoing), f.id)
    } flatMap proxyGames flatMap { games =>
      val (finished, ongoing) = games.partition(_.finishedOrAborted)
      val flagged = ongoing.filter(_ outoftime true)
      if (flagged.nonEmpty)
        bus.publish(lidraughts.hub.actorApi.map.TellMany(flagged.map(_.id), QuietFlag), 'roundSocket)
      finished.foreach(finishGame)
      funit
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

  private def socketReloadImmediately(swissId: Swiss.Id): Unit =
    socketMap.tell(swissId.value, NotifyReload)
}
