package lidraughts.externalTournament

import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._

object DataForm {

  import lidraughts.common.Form.cleanNonEmptyText

  val form = Form(mapping(
    "title" -> cleanNonEmptyText(minLength = 3, maxLength = 40)
  )(Data.apply)(Data.unapply)) fill Data(
    title = ""
  )

  case class Data(
      title: String
  ) {

    def make(userId: String) = ExternalTournament(
      _id = ExternalTournament.makeId,
      title = title,
      createdBy = userId
    )
  }

  object Data {

    def make(tour: ExternalTournament) = Data(
      title = tour.title
    )
  }
}
