package lidraughts.app
package mashup

import lidraughts.forum.MiniForumPost
import lidraughts.team.{ Team, RequestRepo, MemberRepo, RequestWithUser, TeamApi }
import lidraughts.tournament.{ Tournament, TournamentRepo }
import lidraughts.user.{ User, UserRepo }

case class TeamInfo(
    mine: Boolean,
    createdByMe: Boolean,
    requestedByMe: Boolean,
    subscribed: Boolean,
    requests: List[RequestWithUser],
    forumPosts: List[MiniForumPost],
    tournaments: List[Tournament]
) {

  def hasRequests = requests.nonEmpty

  def userIds = forumPosts.flatMap(_.userId)
}

final class TeamInfoApi(
    api: TeamApi,
    getForumPosts: String => Fu[List[MiniForumPost]],
    preloadTeams: Set[Team.ID] => Funit
) {

  def apply(team: Team, me: Option[User]): Fu[TeamInfo] = for {
    requests ← (team.enabled && me.??(m => team.isCreator(m.id))) ?? api.requestsWithUsers(team)
    mine <- me.??(m => api.belongsTo(team.id, m.id))
    requestedByMe ← !mine ?? me.??(m => RequestRepo.exists(team.id, m.id))
    subscribed <- me.ifTrue(mine) ?? { api.isSubscribed(team, _) }
    forumPosts ← getForumPosts(team.id)
    tours <- lidraughts.tournament.TournamentRepo.byTeam(team.id, 5)
    _ <- tours.nonEmpty ?? {
      preloadTeams(tours.flatMap(_.teamBattle.??(_.teams)).toSet)
    }
  } yield TeamInfo(
    mine = mine,
    createdByMe = ~me.map(m => team.isCreator(m.id)),
    requestedByMe = requestedByMe,
    subscribed = subscribed,
    requests = requests,
    forumPosts = forumPosts,
    tournaments = tours
  )
}
