package lidraughts.event

import org.joda.time.DateTime
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._

import lidraughts.i18n.LangList
import lidraughts.common.Lang

object EventForm {

  import lidraughts.common.Form.UTCDate._

  val form = Form(mapping(
    "title" -> text(minLength = 3, maxLength = 40),
    "headline" -> text(minLength = 5, maxLength = 30),
    "description" -> optional(text(minLength = 5, maxLength = 4000)),
    "homepageHours" -> number(min = 0, max = 336),
    "url" -> nonEmptyText,
    "lang" -> text.verifying(l => LangList.choices.exists(_._1 == l)),
    "enabled" -> boolean,
    "startsAt" -> utcDate,
    "finishesAt" -> utcDate
  )(Data.apply)(Data.unapply)) fill Data(
    title = "",
    headline = "",
    description = none,
    homepageHours = 0,
    url = "",
    lang = lidraughts.i18n.enLang.language,
    enabled = true,
    startsAt = DateTime.now,
    finishesAt = DateTime.now
  )

  case class Data(
      title: String,
      headline: String,
      description: Option[String],
      homepageHours: Int,
      url: String,
      lang: String,
      enabled: Boolean,
      startsAt: DateTime,
      finishesAt: DateTime
  ) {

    def update(event: Event) = event.copy(
      title = title,
      headline = headline,
      description = description,
      homepageHours = homepageHours,
      url = url,
      lang = Lang(lang),
      enabled = enabled,
      startsAt = startsAt,
      finishesAt = finishesAt
    )

    def make(userId: String) = Event(
      _id = Event.makeId,
      title = title,
      headline = headline,
      description = description,
      homepageHours = homepageHours,
      url = url,
      lang = Lang(lang),
      enabled = enabled,
      startsAt = startsAt,
      finishesAt = finishesAt,
      createdBy = Event.UserId(userId),
      createdAt = DateTime.now
    )
  }

  object Data {

    def make(event: Event) = Data(
      title = event.title,
      headline = event.headline,
      description = event.description,
      homepageHours = event.homepageHours,
      url = event.url,
      lang = event.lang.language,
      enabled = event.enabled,
      startsAt = event.startsAt,
      finishesAt = event.finishesAt
    )
  }
}
