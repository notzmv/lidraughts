package lidraughts.matches

import akka.actor._
import akka.pattern.ask
import com.typesafe.config.Config
import scala.concurrent.duration._

import lidraughts.hub.actorApi.map.Ask
import lidraughts.hub.{ ActorMap, Sequencer }
import lidraughts.socket.actorApi.GetVersion
import lidraughts.socket.History
import makeTimeout.short

final class Env(
    config: Config,
    system: ActorSystem,
    scheduler: lidraughts.common.Scheduler,
    db: lidraughts.db.Env,
    hub: lidraughts.hub.Env,
    lightUser: lidraughts.common.LightUser.Getter,
    onGameStart: String => Unit,
    isOnline: String => Boolean,
    asyncCache: lidraughts.memo.AsyncCache.Builder
) {

  private val settings = new {
    val CollectionMatches = config getString "collection.matches"
    val SequencerTimeout = config duration "sequencer.timeout"
    val CreatedCacheTtl = config duration "created.cache.ttl"
    val HistoryMessageTtl = config duration "history.message.ttl"
    val UidTimeout = config duration "uid.timeout"
    val SocketTimeout = config duration "socket.timeout"
    val SocketName = config getString "socket.name"
    val ActorName = config getString "actor.name"
  }
  import settings._

  lazy val repo = new MatchRepo(
    matchColl = matchColl
  )

  lazy val api = new MatchApi(
    repo = repo,
    system = system,
    socketHub = socketHub,
    site = hub.socket.site,
    renderer = hub.actor.renderer,
    timeline = hub.actor.timeline,
    userRegister = hub.actor.userRegister,
    lobby = hub.socket.lobby,
    onGameStart = onGameStart,
    sequencers = sequencerMap,
    asyncCache = asyncCache
  )

  lazy val forms = new DataForm

  lazy val jsonView = new JsonView(lightUser)

  private val socketHub = system.actorOf(
    Props(new lidraughts.socket.SocketHubActor.Default[Socket] {
      def mkActor(matchId: String) = new Socket(
        matchId = matchId,
        history = new History(ttl = HistoryMessageTtl),
        getMatch = repo.find,
        jsonView = jsonView,
        uidTimeout = UidTimeout,
        socketTimeout = SocketTimeout,
        lightUser = lightUser
      )
    }), name = SocketName
  )

  val allCreated = asyncCache.single(
    name = "matches.allCreated",
    repo.allCreated,
    expireAfter = _.ExpireAfterWrite(CreatedCacheTtl)
  )

  def version(tourId: String): Fu[Int] =
    socketHub ? Ask(tourId, GetVersion) mapTo manifest[Int]

  private[matches] val matchColl = db(CollectionMatches)

  private val sequencerMap = system.actorOf(Props(ActorMap { id =>
    new Sequencer(SequencerTimeout.some, logger = logger)
  }))

}

object Env {

  lazy val current = "matches" boot new Env(
    config = lidraughts.common.PlayApp loadConfig "matches",
    system = lidraughts.common.PlayApp.system,
    scheduler = lidraughts.common.PlayApp.scheduler,
    db = lidraughts.db.Env.current,
    hub = lidraughts.hub.Env.current,
    lightUser = lidraughts.user.Env.current.lightUser,
    onGameStart = lidraughts.game.Env.current.onStart,
    isOnline = lidraughts.user.Env.current.isOnline,
    asyncCache = lidraughts.memo.Env.current.asyncCache
  )
}
