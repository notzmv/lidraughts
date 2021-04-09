package views.html
package externalTournament

import play.api.libs.json.Json

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.String.html.safeJsonValue
import lidraughts.externalTournament.ExternalTournament

import controllers.routes

object show {

  def apply(
    tour: ExternalTournament
  )(implicit ctx: Context) = views.html.base.layout(
    title = tour.title,
    moreCss = cssTag("tournament-ext"),
    draughtsground = false
  )(
      main(cls := s"tour-ext")(
        h1(tour.title)
      )
    )
}
