package lidraughts.swiss

import akka.actor._
import com.typesafe.config.Config
import scala.concurrent.duration._

import lidraughts.socket.History
import lidraughts.socket.Socket.{ GetVersion, SocketVersion }
import lidraughts.user.LightUserApi

final class Env(
    config: Config,
    system: ActorSystem,
    db: lidraughts.db.Env,
    lightUserApi: lidraughts.user.LightUserApi
) {

  private val settings = new {
    val CollectionSwiss = config getString "collection.swiss"
    val CollectionPlayer = config getString "collection.player"
    val CollectionPairing = config getString "collection.pairing"
    val HistoryMessageTtl = config duration "history.message.ttl"
    val UidTimeout = config duration "uid.timeout"
    val SocketTimeout = config duration "socket.timeout"
    val SocketName = config getString "socket.name"
  }
  import settings._

  lazy val api = new SwissApi(
    swissColl = swissColl,
    playerColl = playerColl,
    pairingColl = pairingColl
  )

  private val socketMap: SocketMap = lidraughts.socket.SocketMap[SwissSocket](
    system = system,
    mkTrouper = (swissId: String) => new SwissSocket(
      system = system,
      swissId = swissId,
      history = new History(ttl = HistoryMessageTtl),
      lightUser = lightUserApi.async,
      uidTtl = UidTimeout,
      keepMeAlive = () => socketMap touch swissId
    ),
    accessTimeout = SocketTimeout,
    monitoringName = "swiss.socketMap",
    broomFrequency = 3701 millis
  )

  def version(swissId: Swiss.Id): Fu[SocketVersion] =
    socketMap.askIfPresentOrZero[SocketVersion](swissId.value)(GetVersion)

  lazy val json = new SwissJson(
    swissColl = swissColl,
    pairingColl = pairingColl,
    lightUserApi = lightUserApi
  )

  lazy val forms = new SwissForm

  private[swiss] lazy val swissColl = db(CollectionSwiss)
  private[swiss] lazy val playerColl = db(CollectionPlayer)
  private[swiss] lazy val pairingColl = db(CollectionPairing)
}

object Env {

  lazy val current = "swiss" boot new Env(
    config = lidraughts.common.PlayApp loadConfig "swiss",
    system = lidraughts.common.PlayApp.system,
    db = lidraughts.db.Env.current,
    lightUserApi = lidraughts.user.Env.current.lightUserApi
  )
}
