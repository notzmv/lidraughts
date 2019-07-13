package lidraughts.site

import akka.actor.ActorSystem
import com.typesafe.config.Config
import play.api.libs.concurrent.Akka.system

import lidraughts.socket.actorApi.SendToFlag

final class Env(
    config: Config,
    remoteSocketApi: lila.socket.RemoteSocket,
    population: lila.socket.SocketPopulation,
    hub: lidraughts.hub.Env,
    system: ActorSystem
) {

  private val SocketSriTtl = config duration "socket.sri.ttl"

  private val socket = new Socket(system, SocketSriTtl)

  val remoteSocket = new SiteRemoteSocket(
    remoteSocketApi = remoteSocketApi
  )

  lazy val socketHandler = new SocketHandler(socket, hub)
}

object Env {

  lazy val current = "site" boot new Env(
    config = lidraughts.common.PlayApp loadConfig "site",
    remoteSocketApi = lila.socket.Env.current.remoteSocket,
    population = lila.socket.Env.current.population,
    hub = lidraughts.hub.Env.current,
    system = lidraughts.common.PlayApp.system
  )
}
