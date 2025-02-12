package lidraughts.gameSearch

import lidraughts.game.Game
import play.api.mvc.Request

final class UserGameSearch(
    forms: DataForm,
    paginator: lidraughts.search.PaginatorBuilder[Game, Query]
) {

  def apply(user: lidraughts.user.User, page: Int)(implicit req: Request[_]) =
    paginator(
      query = forms.search.bindFromRequest.fold(
        _ => SearchData(SearchPlayer(a = user.id.some)),
        data => data.copy(
          players = data.players.copy(a = user.id.some)
        )
      ).query,
      page = page
    )

  def requestForm(implicit req: Request[_]) = forms.search.bindFromRequest

  def defaultForm = forms.search
}
