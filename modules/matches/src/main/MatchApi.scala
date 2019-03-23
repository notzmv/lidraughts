package lidraughts.matches

import akka.actor._
import akka.pattern.{ ask, pipe }
import play.api.libs.json.Json
import scala.concurrent.duration._

import draughts.variant.Variant
import lidraughts.common.Debouncer
import lidraughts.game.{ Game, GameRepo, PerfPicker }
import lidraughts.hub.actorApi.lobby.ReloadMatches
import lidraughts.hub.actorApi.map.Tell
import lidraughts.hub.actorApi.timeline.{ Propagate, MatchCreate, MatchJoin }
import lidraughts.socket.actorApi.SendToFlag
import lidraughts.user.{ User, UserRepo }
import makeTimeout.short

final class MatchApi(
    system: ActorSystem,
    sequencers: ActorRef,
    onGameStart: Game.ID => Unit,
    socketHub: ActorRef,
    site: ActorSelection,
    renderer: ActorSelection,
    timeline: ActorSelection,
    userRegister: ActorSelection,
    lobby: ActorSelection,
    repo: MatchRepo,
    asyncCache: lidraughts.memo.AsyncCache.Builder
) {

  def currentHostIds: Fu[Set[String]] = currentHostIdsCache.get

  private val currentHostIdsCache = asyncCache.single[Set[String]](
    name = "matches.currentHostIds",
    f = repo.allStarted dmap (_.map(_.hostId)(scala.collection.breakOut)),
    expireAfter = _.ExpireAfterAccess(10 minutes)
  )

  def byIds = repo.byIds _

  def create(setup: MatchSetup, me: User): Fu[Match] = {
    val mtch = Match.make(
      clock = MatchClock(
        config = draughts.Clock.Config(setup.clockTime * 60, setup.clockIncrement)
      ),
      variants = setup.variants.flatMap { draughts.variant.Variant(_) },
      host = me
    )
    (repo create mtch) >>- publish() >>- {
      timeline ! (Propagate(MatchCreate(me.id, mtch.id, mtch.fullName)) toFollowersOf me.id)
    } inject mtch
  }

  def addApplicant(matchId: Match.ID, user: User, variantKey: String): Unit = {
    WithMatch(repo.findCreated, matchId) { mtch =>
      if (mtch.nbAccepted >= Game.maxPlayingRealtime) mtch
      else {
        timeline ! (Propagate(MatchJoin(user.id, mtch.id, mtch.fullName)) toFollowersOf user.id)
        Variant(variantKey).filter(mtch.variants.contains).fold(mtch) { variant =>
          mtch addApplicant MatchApplicant.make(
            MatchPlayer.make(
              user,
              variant,
              PerfPicker.mainOrDefault(
                speed = draughts.Speed(mtch.clock.config.some),
                variant = variant,
                daysPerTurn = none
              )(user.perfs)
            )
          )
        }
      }
    }
  }

  def removeApplicant(matchId: Match.ID, user: User): Unit = {
    WithMatch(repo.findCreated, matchId) { _ removeApplicant user.id }
  }

  def accept(matchId: Match.ID, userId: String, v: Boolean): Unit = {
    UserRepo byId userId foreach {
      _ foreach { user =>
        WithMatch(repo.findCreated, matchId) { _.accept(user.id, v) }
      }
    }
  }

  def abort(matchId: Match.ID): Unit = {
    Sequence(matchId) {
      repo.findCreated(matchId) flatMap {
        _ ?? { mtch =>
          (repo remove mtch) >>- sendTo(mtch.id, actorApi.Aborted) >>- publish()
        }
      }
    }
  }

  private def onComplete(mtch: Match): Unit = {
    currentHostIdsCache.refresh
    userRegister ! lidraughts.hub.actorApi.SendTo(
      mtch.hostId,
      lidraughts.socket.Socket.makeMessage("matchEnd", Json.obj(
        "id" -> mtch.id,
        "name" -> mtch.name
      ))
    )
  }

  def idToName(id: Match.ID): Fu[Option[String]] =
    repo find id map2 { (mtch: Match) => mtch.fullName }

  private def update(mtch: Match) =
    repo.update(mtch) >>- socketReload(mtch.id) >>- publish()

  private def WithMatch(
    finding: Match.ID => Fu[Option[Match]],
    matchId: Match.ID
  )(updating: Match => Match): Unit = {
    Sequence(matchId) {
      finding(matchId) flatMap {
        _ ?? { mtch => update(updating(mtch)) }
      }
    }
  }

  private def Sequence(matchId: Match.ID)(work: => Funit): Unit = {
    sequencers ! Tell(matchId, lidraughts.hub.Sequencer work work)
  }

  private object publish {
    private val siteMessage = SendToFlag("matches", Json.obj("t" -> "reload"))
    private val debouncer = system.actorOf(Props(new Debouncer(5 seconds, {
      (_: Debouncer.Nothing) =>
        site ! siteMessage
        repo.allCreated foreach { matches =>
          renderer ? actorApi.MatchTable(matches) map {
            case view: play.twirl.api.Html => ReloadMatches(view.body)
          } pipeToSelection lobby
        }
    })))
    def apply(): Unit = { debouncer ! Debouncer.Nothing }
  }

  private def sendTo(matchId: Match.ID, msg: Any): Unit = {
    socketHub ! Tell(matchId, msg)
  }

  private def socketReload(matchId: Match.ID): Unit = {
    sendTo(matchId, actorApi.Reload)
  }
}
