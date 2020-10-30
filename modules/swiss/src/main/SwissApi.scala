package lidraughts.swiss

import org.joda.time.DateTime

import lidraughts.hub.lightTeam.TeamId
import lidraughts.user.User

import lidraughts.db.dsl._

final class SwissApi(
    swissColl: Coll,
    roundColl: Coll
) {

  import BsonHandlers._

  def create(data: SwissForm.SwissData, me: User, teamId: TeamId): Fu[Swiss] = {
    val swiss = Swiss(
      _id = Swiss.makeId,
      name = data.name,
      status = Status.Created,
      clock = data.clock,
      variant = data.realVariant,
      rated = data.rated | true,
      nbRounds = data.nbRounds,
      nbPlayers = 0,
      createdAt = DateTime.now,
      createdBy = me.id,
      teamId = teamId,
      startsAt = data.startsAt,
      winnerId = none,
      description = data.description,
      hasChat = data.hasChat | true
    )
    swissColl.insert(swiss) inject swiss
  }
}
