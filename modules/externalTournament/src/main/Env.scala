package lidraughts.externalTournament

import akka.actor._
import com.typesafe.config.Config

final class Env(
    config: Config,
    system: ActorSystem,
    db: lidraughts.db.Env,
    asyncCache: lidraughts.memo.AsyncCache.Builder,
    lightUserApi: lidraughts.user.LightUserApi
) {

  private lazy val externalTournamentColl = db(config getString "collection.externalTournament")

  lazy val jsonView = new JsonView(lightUserApi)

  lazy val api = new ExternalTournamentApi(
    coll = externalTournamentColl,
    asyncCache = asyncCache
  )
}

object Env {

  lazy val current = "externalTournament" boot new Env(
    config = lidraughts.common.PlayApp loadConfig "externalTournament",
    system = lidraughts.common.PlayApp.system,
    db = lidraughts.db.Env.current,
    asyncCache = lidraughts.memo.Env.current.asyncCache,
    lightUserApi = lidraughts.user.Env.current.lightUserApi
  )
}
