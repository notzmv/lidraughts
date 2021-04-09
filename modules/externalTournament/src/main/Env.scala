package lidraughts.externalTournament

import akka.actor._
import com.typesafe.config.Config
import scala.concurrent.duration._

import lidraughts.memo._

final class Env(
    config: Config,
    system: ActorSystem,
    db: lidraughts.db.Env,
    asyncCache: lidraughts.memo.AsyncCache.Builder,
    lightUserApi: lidraughts.user.LightUserApi
) {

  private lazy val externalTournamentColl = db(config getString "collection.externalTournament")

  private lazy val nameCache = new Syncache[String, Option[String]](
    name = "externalTournament.name",
    compute = id => api byId id map2 { (tour: ExternalTournament) => tour.title },
    default = _ => none,
    strategy = Syncache.WaitAfterUptime(20 millis),
    expireAfter = Syncache.ExpireAfterAccess(1 hour),
    logger = logger
  )(system)

  def name(id: String): Option[String] = nameCache sync id

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
