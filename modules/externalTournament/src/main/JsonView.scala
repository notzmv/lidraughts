package lidraughts.externalTournament

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.libs.json._
import scala.concurrent.duration._

import lidraughts.user.{ LightUserApi, User }

final class JsonView(
    lightUserApi: LightUserApi
) {

  def apply(
    tour: ExternalTournament,
    me: Option[User]
  ): Fu[JsObject] = fuccess(
    Json.obj(
      "id" -> tour.id,
      "title" -> tour.title
    )
  )
}
