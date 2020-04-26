package lidraughts.team

import lidraughts.common.paginator._
import lidraughts.common.MaxPerPage
import lidraughts.common.LightUser
import lidraughts.db.dsl._
import lidraughts.db.paginator._
import lidraughts.user.UserRepo

private[team] final class PaginatorBuilder(
    coll: Colls,
    maxPerPage: MaxPerPage,
    maxUserPerPage: MaxPerPage,
    lightUserApi: lidraughts.user.LightUserApi
) {

  import BSONHandlers._

  def popularTeams(page: Int): Fu[Paginator[Team]] = Paginator(
    adapter = new Adapter(
      collection = coll.team,
      selector = TeamRepo.enabledSelect,
      projection = $empty,
      sort = TeamRepo.sortPopular
    ),
    page,
    maxPerPage
  )

  def teamMembers(team: Team, page: Int): Fu[Paginator[LightUser]] = Paginator(
    adapter = new TeamAdapter(team),
    page,
    maxUserPerPage
  )

  private final class TeamAdapter(team: Team) extends AdapterLike[LightUser] {

    val nbResults = fuccess(team.nbMembers)

    def slice(offset: Int, length: Int): Fu[Seq[LightUser]] =
      for {
        docs <- coll.member
          .find(selector, $doc("user" -> true, "_id" -> false))
          .sort(sorting)
          .skip(offset)
          .list[Bdoc](length)
        userIds = docs.flatMap(_.getAs[String]("user"))
        users <- lightUserApi asyncMany userIds
      } yield users.flatten
    private def selector = MemberRepo teamQuery team.id
    private def sorting = $sort desc "date"
  }
}
