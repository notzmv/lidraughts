package lidraughts.swiss

import com.typesafe.config.Config

import lidraughts.socket.Socket.{ GetVersion, SocketVersion }
import lidraughts.user.LightUserApi

final class Env(
    config: Config,
    db: lidraughts.db.Env,
    lightUserApi: lidraughts.user.LightUserApi
) {

  private val settings = new {
    val CollectionSwiss = config getString "collection.swiss"
    val CollectionPlayer = config getString "collection.player"
    val CollectionPairing = config getString "collection.pairing"
  }
  import settings._

  lazy val api = new SwissApi(
    swissColl = swissColl,
    playerColl = playerColl,
    pairingColl = pairingColl
  )

  def version(tourId: Swiss.Id): Fu[SocketVersion] =
    fuccess(SocketVersion(0))
  // socketMap.askIfPresentOrZero[SocketVersion](tourId)(GetVersion)

  lazy val json = new SwissJson(lightUserApi = lightUserApi)

  lazy val forms = new SwissForm

  private[swiss] lazy val swissColl = db(CollectionSwiss)
  private[swiss] lazy val playerColl = db(CollectionPlayer)
  private[swiss] lazy val pairingColl = db(CollectionPairing)
}

object Env {

  lazy val current = "swiss" boot new Env(
    config = lidraughts.common.PlayApp loadConfig "swiss",
    db = lidraughts.db.Env.current,
    lightUserApi = lidraughts.user.Env.current.lightUserApi
  )
}
