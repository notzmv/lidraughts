package controllers

import lidraughts.app._
import lidraughts.game.GameRepo
import views._

object ExternalTournament extends LidraughtsController {

  private def env = Env.externalTournament
  private def api = Env.externalTournament.api

  def show(id: String) = Open { implicit ctx =>
    OptionFuOk(api byId id) { tour =>
      for {
        upcoming <- Env.challenge.api.forExternalTournament(tour.id)
        finished <- GameRepo.finishedByExternalTournament(id, 10)
      } yield html.externalTournament.show(tour, upcoming, finished)
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
