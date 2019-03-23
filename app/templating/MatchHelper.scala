package lidraughts.app
package templating

import controllers.routes
import lidraughts.matches.Match

import play.twirl.api.Html

trait MatchHelper { self: I18nHelper =>

  def matchLink(matchId: Match.ID): Html = Html {
    val url = routes.Match.show(matchId)
    s"""<a class="text" data-icon="|" href="$url">Match</a>"""
  }
}
