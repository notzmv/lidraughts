package lidraughts.swiss

import com.typesafe.config.Config

final class Env(
    config: Config,
    db: lidraughts.db.Env
) {

  private val settings = new {
    val CollectionSwiss = config getString "collection.swiss"
    val CollectionRound = config getString "collection.round"
  }
  import settings._

  lazy val api = new SwissApi(
    swissColl = swissColl,
    roundColl = roundColl
  )

  lazy val forms = new SwissForm

  private[swiss] lazy val swissColl = db(CollectionSwiss)
  private[swiss] lazy val roundColl = db(CollectionRound)
}

object Env {

  lazy val current = "swiss" boot new Env(
    config = lidraughts.common.PlayApp loadConfig "swiss",
    db = lidraughts.db.Env.current
  )
}
