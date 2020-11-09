package lidraughts.team

import reactivemongo.bson.Macros

import lidraughts.hub.lightTeam.LightTeam

private object BSONHandlers {

  import lidraughts.db.dsl.BSONJodaDateTimeHandler
  implicit val TeamBSONHandler = Macros.handler[Team]
  implicit val RequestBSONHandler = Macros.handler[Request]
  implicit val MemberBSONHandler = Macros.handler[Member]
  implicit val LightTeamBSONHandler = Macros.handler[LightTeam]
}
