package lidraughts.team

import reactivemongo.bson._
import reactivemongo.api.commands.WriteResult

import lidraughts.db.dsl._
import lidraughts.user.User

object MemberRepo {

  // dirty
  private val coll = Env.current.colls.member

  import BSONHandlers._

  // expensive with thousands of members!
  def userIdsByTeam(teamId: Team.ID): Fu[Set[User.ID]] =
    coll.distinct[String, Set]("user", $doc("team" -> teamId).some)

  def subscribedUserIds(teamId: Team.ID): Fu[Set[User.ID]] =
    coll.distinct[String, Set]("user", Some($doc("team" -> teamId) ++ $doc("unsub" $ne true)))

  def teamIdsByUser(userId: User.ID): Fu[Set[Team.ID]] =
    coll.distinct[String, Set]("team", $doc("user" -> userId).some)

  def removeByteam(teamId: Team.ID): Funit =
    coll.remove(teamQuery(teamId)).void

  def removeByUser(userId: User.ID): Funit =
    coll.remove(userQuery(userId)).void

  def exists(teamId: Team.ID, userId: User.ID): Fu[Boolean] =
    coll.exists(selectId(teamId, userId))

  def add(teamId: Team.ID, userId: User.ID): Funit =
    coll.insert(Member.make(team = teamId, user = userId)).void

  def remove(teamId: Team.ID, userId: User.ID): Fu[WriteResult] =
    coll.remove(selectId(teamId, userId))

  def countByTeam(teamId: Team.ID): Fu[Int] =
    coll.countSel(teamQuery(teamId))

  def isSubscribed(team: Team, user: User): Fu[Boolean] =
    !coll.exists(selectId(team.id, user.id) ++ $doc("unsub" -> true))

  def subscribe(teamId: Team.ID, userId: User.ID, v: Boolean): Funit =
    coll
      .update(
        selectId(teamId, userId),
        if (v) $unset("unsub")
        else $set("unsub" -> true)
      )
      .void

  def teamQuery(teamId: Team.ID) = $doc("team" -> teamId)
  def selectId(teamId: Team.ID, userId: User.ID) = $id(Member.makeId(teamId, userId))
  def userQuery(userId: Team.ID) = $doc("user" -> userId)
}
