package lidraughts.externalTournament

import scala.concurrent.duration._

import lidraughts.db.dsl._
import lidraughts.user.User

final class ExternalTournamentApi(
    coll: Coll,
    asyncCache: lidraughts.memo.AsyncCache.Builder
) {

  import BsonHandlers._

  def createForm = DataForm.form

  def create(
    data: DataForm.Data,
    userId: User.ID
  ): Fu[ExternalTournament] = {
    val tour = data make userId
    coll.insert(tour) inject tour
  }

  def one(id: String) = coll.byId[ExternalTournament](id)
}
