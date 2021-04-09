package controllers

import lidraughts.app._
import views._

object ExternalTournament extends LidraughtsController {

  private def env = Env.externalTournament
  private def api = Env.externalTournament.api

  def show(id: String) = Open { implicit ctx =>
    OptionOk(api one id) { tour =>
      html.externalTournament.show(tour)
    }
  }

  def create = ScopedBody(_.Tournament.Write) { implicit req => me =>
    if (me.isBot || me.lame) notFoundJson("This account cannot create tournaments")
    else api.createForm.bindFromRequest.fold(
      jsonFormErrorDefaultLang,
      data => api.create(data, me.id) flatMap { tour =>
        env.jsonView(tour, me.some)
      } map { Ok(_) }
    )
  }
}
