package lidraughts.common

import play.api.libs.json.{ Json, OWrites }

case class LightUser(
    id: String,
    name: String,
    title: Option[String],
    isPatron: Boolean
) {

  def shortTitle = title.map(t => if (t.endsWith("-64")) t.dropRight(3) else t)

  def titleName = shortTitle.fold(name)(_ + " " + name)
  def fullTitleName = title.fold(name)(_ + " " + name)

  def isBot = title has "BOT"
}

object LightUser {

  implicit val lightUserWrites = OWrites[LightUser] { u =>
    Json.obj(
      "id" -> u.id,
      "name" -> u.name
    ).add("title" -> u.title)
      .add("patron" -> u.isPatron)
  }

  def fallback(userId: String) = LightUser(
    id = userId,
    name = userId,
    title = None,
    isPatron = false
  )

  type Getter = String => Fu[Option[LightUser]]
  type GetterSync = String => Option[LightUser]
  type IsBotSync = String => Boolean
}
