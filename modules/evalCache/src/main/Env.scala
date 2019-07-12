package lidraughts.evalCache

import com.typesafe.config.Config
import play.api.libs.json.JsValue

import lidraughts.hub.actorApi.socket.{ RemoteSocketTellSriIn, RemoteSocketTellSriOut }
import lidraughts.socket.Socket.Uid

final class Env(
    config: Config,
    settingStore: lidraughts.memo.SettingStore.Builder,
    db: lidraughts.db.Env,
    system: akka.actor.ActorSystem,
    asyncCache: lidraughts.memo.AsyncCache.Builder
) {

  private val CollectionEvalCache = config getString "collection.eval_cache"

  private lazy val truster = new EvalCacheTruster(asyncCache)

  private lazy val upgrade = new EvalCacheUpgrade

  lazy val api = new EvalCacheApi(
    coll = db(CollectionEvalCache),
    truster = truster,
    upgrade = upgrade,
    asyncCache = asyncCache
  )

  lazy val socketHandler = new EvalCacheSocketHandler(
    api = api,
    truster = truster,
    upgrade = upgrade
  )

  system.lidraughtsBus.subscribeFun('socketLeave) {
    case lidraughts.socket.actorApi.SocketLeave(uid, _) => upgrade unregister uid
  }

  // remote socket support
  system.lidraughtsBus.subscribeFun(Symbol("remoteSocketIn:evalGet")) {
    case RemoteSocketTellSriIn(sri, _, d) =>
      socketHandler.evalGet(Uid(sri), d, res => system.lidraughtsBus.publish(RemoteSocketTellSriOut(sri, res), 'remoteSocketOut))
  }
  system.lidraughtsBus.subscribeFun(Symbol("remoteSocketIn:evalPut")) {
    case RemoteSocketTellSriIn(sri, Some(userId), d) =>
      socketHandler.untrustedEvalPut(Uid(sri), userId, d)
  }
  // END remote socket support

  def cli = new lidraughts.common.Cli {
    def process = {
      case "eval-cache" :: "drop" :: fenParts =>
        api.drop(draughts.variant.Standard, draughts.format.FEN(fenParts mkString " ")) inject "done!"
    }
  }
}

object Env {

  lazy val current: Env = "evalCache" boot new Env(
    config = lidraughts.common.PlayApp loadConfig "evalCache",
    settingStore = lidraughts.memo.Env.current.settingStore,
    db = lidraughts.db.Env.current,
    system = lidraughts.common.PlayApp.system,
    asyncCache = lidraughts.memo.Env.current.asyncCache
  )
}
