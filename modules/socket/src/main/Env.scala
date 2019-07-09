package lidraughts.socket

import akka.actor._
import com.typesafe.config.Config
import io.lettuce.core._
import scala.concurrent.duration._

import actorApi._

final class Env(
    system: ActorSystem,
    config: Config,
    lifecycle: play.api.inject.ApplicationLifecycle,
    hub: lidraughts.hub.Env,
    settingStore: lidraughts.memo.SettingStore.Builder
) {

  private val RedisUri = config getString "redis.uri"

  private val population = new Population(system)

  private val moveBroadcast = new MoveBroadcast(system)

  private val userRegister = new UserRegister(system)

  private val remoteSocket = new RemoteSocket(
    redisClient = RedisClient create RedisURI.create(RedisUri),
    chanIn = "site-in",
    chanOut = "site-out",
    lifecycle = lifecycle,
    notificationActor = hub.notification,
    setNb = nb => population ! actorApi.RemoteNbMembers(nb),
    bus = system.lidraughtsBus
  )

  system.scheduler.schedule(5 seconds, 1 seconds) { population ! PopulationTell }

  val socketDebugSetting = settingStore[Boolean](
    "socketDebug",
    default = false,
    text = "Send extra debugging to websockets.".some
  )

  import lidraughts.memo.SettingStore.Regex._
  import lidraughts.memo.SettingStore.Formable.regexFormable
  val socketRemoteUsersSetting = settingStore[scala.util.matching.Regex](
    "socketRemoteUsers",
    default = "".r,
    text = "Regex selecting user IDs using remote socket".some
  )
}

object Env {

  lazy val current = "socket" boot new Env(
    system = lidraughts.common.PlayApp.system,
    config = lidraughts.common.PlayApp loadConfig "socket",
    lifecycle = lidraughts.common.PlayApp.lifecycle,
    hub = lidraughts.hub.Env.current,
    settingStore = lidraughts.memo.Env.current.settingStore
  )
}
