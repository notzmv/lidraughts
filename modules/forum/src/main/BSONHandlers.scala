package lidraughts.forum

import lidraughts.db.BSON.{ LoggingHandler, MapValue }
import lidraughts.db.dsl._
import lidraughts.user.User
import reactivemongo.bson._

private object BSONHandlers {

  implicit val CategBSONHandler = LoggingHandler(logger)(Macros.handler[Categ])

  implicit val PostEditBSONHandler = Macros.handler[OldVersion]
  implicit val PostReactionsHandler = MapValue.MapHandler[String, Set[User.ID]]
  implicit val PostBSONHandler = Macros.handler[Post]

  implicit val TopicBSONHandler = LoggingHandler(logger)(Macros.handler[Topic])
}
