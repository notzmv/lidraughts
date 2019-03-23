package lidraughts.matches

import play.api.libs.json._

import lidraughts.common.LightUser
import lidraughts.game.{ Game, GameRepo }

final class JsonView(getLightUser: LightUser.Getter) {

  def apply(mtch: Match): Fu[JsObject] = for {
    applicants <- mtch.applicants.sortBy(-_.player.rating).map(applicantJson).sequenceFu
  } yield Json.obj(
    "id" -> mtch.id,
    "name" -> mtch.name,
    "fullName" -> mtch.fullName,
    "variants" -> mtch.variants.map(variantJson(draughts.Speed(mtch.clock.config.some))),
    "applicants" -> applicants,
    "isCreated" -> mtch.isCreated,
    "isRunning" -> mtch.isRunning,
    "isFinished" -> mtch.isFinished,
    "quote" -> lidraughts.quote.Quote.one(mtch.id)
  )

  private def playerJson(player: MatchPlayer): Fu[JsObject] =
    getLightUser(player.user) map { light =>
      Json.obj(
        "id" -> player.user,
        "variant" -> player.variant.key,
        "rating" -> player.rating
      ).add("username" -> light.map(_.name))
        .add("title" -> light.map(_.title))
        .add("provisional" -> player.provisional.filter(identity))
        .add("patron" -> light.??(_.isPatron))
    }

  private def applicantJson(app: MatchApplicant): Fu[JsObject] =
    playerJson(app.player) map { player =>
      Json.obj(
        "player" -> player,
        "accepted" -> app.accepted
      )
    }

  private def variantJson(speed: draughts.Speed)(v: draughts.variant.Variant) = Json.obj(
    "key" -> v.key,
    "icon" -> lidraughts.game.PerfPicker.perfType(speed, v, none).map(_.iconChar.toString),
    "name" -> v.name
  )

  private implicit val colorWriter: Writes[draughts.Color] = Writes { c =>
    JsString(c.name)
  }
}
