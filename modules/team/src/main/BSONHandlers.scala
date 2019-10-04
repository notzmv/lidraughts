package lidraughts.team

import lidraughts.hub.lightTeam.LightTeam

private object BSONHandlers {

  import lidraughts.db.dsl.BSONJodaDateTimeHandler
  implicit val TeamBSONHandler = reactivemongo.bson.Macros.handler[Team]
  implicit val RequestBSONHandler = reactivemongo.bson.Macros.handler[Request]
  implicit val MemberBSONHandler = reactivemongo.bson.Macros.handler[Member]
  implicit val LightTeamBSONHandler = reactivemongo.bson.Macros.handler[LightTeam]
}
