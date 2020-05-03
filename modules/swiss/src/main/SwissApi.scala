package lidraughts.swiss

import akka.actor.ActorSystem
import org.joda.time.DateTime
import ornicar.scalalib.Zero
import reactivemongo.api._
import reactivemongo.api.collections.bson.BSONBatchCommands.AggregationFramework._
import scala.concurrent.duration._
import scala.concurrent.Promise

import actorApi._
import lidraughts.db.dsl._
import lidraughts.common.GreatPlayer
import lidraughts.hub.lightTeam.TeamId
import lidraughts.hub.{ Duct, DuctMap }
import lidraughts.user.User

final class SwissApi(
    swissColl: Coll,
    playerColl: Coll,
    pairingColl: Coll,
    system: ActorSystem,
    sequencers: DuctMap[_],
    socketMap: SocketMap
) {

  import BsonHandlers._

  def byId(id: Swiss.Id) = swissColl.byId[Swiss](id.value)
  def enterableById(id: Swiss.Id) = swissColl.byId[Swiss](id.value).dmap(_.filter(_.isEnterable))

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

  def join(
    id: Swiss.Id,
    me: User,
    isInTeam: TeamId => Boolean,
    promise: Option[Promise[Boolean]]
  ): Unit = Sequencing(id)(enterableById) { swiss =>
    val fuJoined =
      isInTeam(swiss.teamId) ?? {
        val number = SwissPlayer.Number(swiss.nbPlayers + 1)
        playerColl.insert(SwissPlayer.make(swiss.id, number, me, swiss.perfLens)) >>
          updateNbPlayers(swiss.id) >>-
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

  def pairingsOf(swiss: Swiss) =
    pairingColl.find($doc("s" -> swiss.id)).sort($sort asc "r").list[SwissPairing]()

  def featuredInTeam(teamId: TeamId): Fu[List[Swiss]] =
    swissColl.find($doc("teamId" -> teamId)).sort($sort desc "startsAt").list[Swiss](5)

  private def updateNbPlayers(swissId: Swiss.Id): Funit =
    playerColl.countSel($doc("s" -> swissId)) flatMap {
      swissColl.updateField($id(swissId), "nbPlayers", _).void
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

  private def socketReload(swissId: Swiss.Id): Unit = socketMap.tell(swissId.value, Reload)

  private def insertPairing(pairing: SwissPairing) =
    pairingColl.insert {
      pairingHandler.write(pairing) ++ $doc("d" -> DateTime.now)
    }.void

}
