package lidraughts.externalTournament

import reactivemongo.bson._

import lidraughts.db.dsl._

private[externalTournament] object BsonHandlers {

  implicit val TournamentBsonHandler = Macros.handler[ExternalTournament]
}
