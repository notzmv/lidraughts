package lidraughts.swiss

import scala.concurrent.duration._

import lidraughts.db.dsl._
import lidraughts.hub.lightTeam.TeamId
import lidraughts.memo._

final private class SwissCache(
    asyncCache: lidraughts.memo.AsyncCache.Builder,
    swissColl: Coll
)(implicit system: akka.actor.ActorSystem) {

  import BsonHandlers._

  val name = new Syncache[Swiss.Id, Option[String]](
    name = "swiss.name",
    compute = id => swissColl.primitiveOne[String]($id(id), "name"),
    default = _ => none,
    strategy = Syncache.WaitAfterUptime(20 millis),
    expireAfter = Syncache.ExpireAfterAccess(20 minutes),
    logger = logger
  )

  private[swiss] object featuredInTeam {
    private val compute = (teamId: TeamId) => {
      val max = 5
      for {
        enterable <- swissColl.primitive[Swiss.Id](
          $doc("teamId" -> teamId, "finishedAt" $exists false),
          $sort asc "startsAt",
          nb = max,
          "_id"
        )
        finished <- swissColl.primitive[Swiss.Id](
          $doc("teamId" -> teamId, "finishedAt" $exists true),
          $sort desc "startsAt",
          nb = max - enterable.size,
          "_id"
        )
      } yield enterable ::: finished
    }
    private val cache = asyncCache.multi[TeamId, List[Swiss.Id]](
      name = "swiss.visibleByTeam",
      f = compute,
      expireAfter = _.ExpireAfterAccess(30 minutes)
    )

    def get(teamId: TeamId) = cache get teamId
    def invalidate(teamId: TeamId) = cache.put(teamId, compute(teamId))
  }

  private[swiss] object feature {

    private val cache = asyncCache.single[List[Swiss]](
      name = "swiss.featurable",
      f = swissColl
        .find(
          $doc(
            "featurable" -> true,
            "settings.i" $lte 600 // hits the partial index
          )
        )
        .sort($sort desc "nbPlayers")
        .list[Swiss](10)
        .map {
          _.zipWithIndex.collect {
            case (s, i) if s.nbPlayers >= 5 || i < 5 => s
          }
        },
      expireAfter = _.ExpireAfterWrite(10 seconds)
    )

    def get = cache.get
  }

}
