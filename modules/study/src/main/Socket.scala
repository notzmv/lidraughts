package lidraughts.study

import akka.actor._
import play.api.libs.iteratee._
import play.api.libs.json._
import scala.concurrent.duration._

import draughts.Centis
import draughts.format.pdn.Glyphs
import lidraughts.hub.TimeBomb
import lidraughts.socket.actorApi.{ Connected => _, _ }
import lidraughts.socket.Socket.{ Uid, GetVersion, SocketVersion }
import lidraughts.socket.{ SocketActor, History, Historical, AnaDests }
import lidraughts.tree.Node.{ Shapes, Comment }
import lidraughts.user.User

private final class Socket(
    studyId: Study.Id,
    jsonView: JsonView,
    studyRepo: StudyRepo,
    chapterRepo: ChapterRepo,
    lightUserApi: lidraughts.user.LightUserApi,
    val history: History[Socket.Messadata],
    uidTimeout: Duration,
    socketTimeout: Duration,
    lightStudyCache: LightStudyCache
) extends SocketActor[Socket.Member](uidTimeout) with Historical[Socket.Member, Socket.Messadata] {

  import Socket._
  import JsonView._
  import jsonView.membersWrites
  import lidraughts.tree.Node.{ openingWriter, commentWriter, glyphsWriter, shapesWrites, clockWrites }

  private val timeBomb = new TimeBomb(socketTimeout)

  private var delayedCrowdNotification = false

  override def preStart(): Unit = {
    super.preStart()
    lidraughtsBus.subscribe(self, Symbol(s"chat:$studyId"))
  }

  override def postStop(): Unit = {
    super.postStop()
    lidraughtsBus.unsubscribe(self)
  }

  def sendStudyDoor(enters: Boolean)(userId: User.ID) =
    lightStudyCache.get(studyId) foreach {
      _ foreach { study =>
        lidraughtsBus.publish(
          lidraughts.hub.actorApi.study.StudyDoor(
            userId = userId,
            studyId = studyId.value,
            contributor = study contributors userId,
            public = study.isPublic,
            enters = enters
          ),
          'study
        )
      }
    }

  def receiveSpecific = ({

    case SetPath(pos, uid) =>
      notifyVersion("path", Json.obj(
        "p" -> pos,
        "w" -> who(uid).map(whoWriter.writes)
      ), noMessadata)

    case AddNode(pos, node, variant, uid, sticky, relay) =>
      val dests = AnaDests(
        variant,
        node.fen,
        pos.path.toString,
        pos.chapterId.value.some,
        none
      )
      notifyVersion("addNode", Json.obj(
        "n" -> TreeBuilder.toBranch(node),
        "p" -> pos,
        "w" -> who(uid),
        "d" -> dests.dests,
        "o" -> dests.opening,
        "s" -> sticky
      ).add("relay", relay), noMessadata)

    case DeleteNode(pos, uid) => notifyVersion("deleteNode", Json.obj(
      "p" -> pos,
      "w" -> who(uid)
    ), noMessadata)

    case Promote(pos, toMainline, uid) => notifyVersion("promote", Json.obj(
      "p" -> pos,
      "toMainline" -> toMainline,
      "w" -> who(uid)
    ), noMessadata)

    case ReloadMembers(studyMembers) =>
      notifyVersion("members", studyMembers, noMessadata)
      val ids = studyMembers.ids.toSet
      notifyIf(makeMessage("reload")) { m =>
        m.userId.exists(ids.contains)
      }

    case ReloadChapters(chapters) => notifyVersion("chapters", chapters, noMessadata)

    case ReloadAll => notifyVersion("reload", JsNull, noMessadata)

    case ChangeChapter(uid, pos) => notifyVersion("changeChapter", Json.obj(
      "p" -> pos,
      "w" -> who(uid)
    ), noMessadata)

    case UpdateChapter(uid, chapterId) => notifyVersion("updateChapter", Json.obj(
      "chapterId" -> chapterId,
      "w" -> who(uid)
    ), noMessadata)

    case DescChapter(uid, chapterId, description) => notifyVersion("descChapter", Json.obj(
      "chapterId" -> chapterId,
      "desc" -> description,
      "w" -> who(uid)
    ), noMessadata)

    case DescStudy(uid, description) => notifyVersion("descStudy", Json.obj(
      "desc" -> description,
      "w" -> who(uid)
    ), noMessadata)

    case AddChapter(uid, pos, sticky) => notifyVersion("addChapter", Json.obj(
      "p" -> pos,
      "w" -> who(uid),
      "s" -> sticky
    ), noMessadata)

    case ValidationError(uid, error) => notifyUid("validationError", Json.obj(
      "error" -> error
    ))(uid)

    case SetShapes(pos, shapes, uid) => notifyVersion("shapes", Json.obj(
      "p" -> pos,
      "s" -> shapes,
      "w" -> who(uid)
    ), noMessadata)

    case SetComment(pos, comment, uid) => notifyVersion("setComment", Json.obj(
      "p" -> pos,
      "c" -> comment,
      "w" -> who(uid)
    ), noMessadata)

    case SetTags(chapterId, tags, uid) => notifyVersion("setTags", Json.obj(
      "chapterId" -> chapterId,
      "tags" -> tags,
      "w" -> who(uid)
    ), noMessadata)

    case DeleteComment(pos, commentId, uid) => notifyVersion("deleteComment", Json.obj(
      "p" -> pos,
      "id" -> commentId,
      "w" -> who(uid)
    ), noMessadata)

    case SetGlyphs(pos, glyphs, uid) => notifyVersion("glyphs", Json.obj(
      "p" -> pos,
      "g" -> glyphs,
      "w" -> who(uid)
    ), noMessadata)

    case SetClock(pos, clock, uid) => notifyVersion("clock", Json.obj(
      "p" -> pos,
      "c" -> clock,
      "w" -> who(uid)
    ), noMessadata)

    case SetConceal(pos, ply) => notifyVersion("conceal", Json.obj(
      "p" -> pos,
      "ply" -> ply.map(_.value)
    ), noMessadata)

    case SetLiking(liking, uid) => notifyVersion("liking", Json.obj(
      "l" -> liking,
      "w" -> who(uid)
    ), noMessadata)

    case lidraughts.chat.actorApi.ChatLine(_, line) => line match {
      case line: lidraughts.chat.UserLine =>
        notifyVersion("message", lidraughts.chat.JsonView(line), Messadata(trollish = line.troll))
      case _ =>
    }

    case ReloadUid(uid) => notifyUid("reload", JsNull)(uid)

    case ReloadUidBecauseOf(uid, chapterId) => notifyUid("reload", Json.obj(
      "chapterId" -> chapterId
    ))(uid)

    case Ping(uid, vOpt, lt) =>
      ping(uid, lt)
      timeBomb.delay
      pushEventsSinceForMobileBC(vOpt, uid)

    case Broom =>
      broom
      if (timeBomb.boom) self ! PoisonPill

    case GetVersion => sender ! history.version

    case Socket.Join(uid, userId, troll, version) =>
      import play.api.libs.iteratee.Concurrent
      val (enumerator, channel) = Concurrent.broadcast[JsValue]
      val member = Socket.Member(channel, userId, troll = troll)
      addMember(uid, member)
      notifyCrowd
      sender ! Socket.Connected(
        prependEventsSince(version, enumerator, member),
        member
      )

      userId foreach sendStudyDoor(true)

    case Quit(uid) => withMember(uid) { member =>
      quit(uid)
      member.userId foreach sendStudyDoor(false)
      notifyCrowd
    }

    case NotifyCrowd =>
      delayedCrowdNotification = false
      val json =
        if (members.size <= maxSpectatorUsers) showSpectators(lightUserApi.async)(members.values)
        else studyRepo uids studyId map showSpectatorsAndMembers
      json foreach { notifyAll("crowd", _) }

    case Broadcast(t, msg) => notifyAll(t, msg)

    case ServerEval.Progress(chapterId, tree, analysis, division) =>
      import lidraughts.game.JsonView.divisionWriter
      notifyAll("analysisProgress", Json.obj(
        "analysis" -> analysis,
        "ch" -> chapterId,
        "tree" -> tree,
        "division" -> division
      ))

    case GetNbMembers => sender ! NbMembers(members.size)

  }: Actor.Receive) orElse lidraughts.chat.Socket.out(
    send = (t, d, _) => notifyVersion(t, d, noMessadata)
  )

  def notifyCrowd: Unit = {
    if (!delayedCrowdNotification) {
      delayedCrowdNotification = true
      context.system.scheduler.scheduleOnce(1 second, self, NotifyCrowd)
    }
  }

  // always show study members
  // since that's how the client knows if they're online
  // WCC has thousands of spectators. mutable implementation.
  def showSpectatorsAndMembers(studyMemberIds: Set[User.ID]): JsValue = {
    var nb = 0
    var titleNames = List.empty[String]
    members foreachValue { w =>
      nb = nb + 1
      w.userId.filter(studyMemberIds.contains) foreach { userId =>
        titleNames = lightUserApi.sync(userId).fold(userId)(_.titleName) :: titleNames
      }
    }
    Json.obj("nb" -> nb, "users" -> titleNames)
  }

  protected def shouldSkipMessageFor(message: Message, member: Member) =
    (message.metadata.trollish && !member.troll)

  private def who(uid: Uid) = uidToUserId(uid) map { Who(_, uid) }

  private val noMessadata = Messadata()
}

object Socket {

  case class Member(
      channel: JsChannel,
      userId: Option[String],
      troll: Boolean
  ) extends lidraughts.socket.SocketMember

  case class Who(u: String, s: Uid)
  import JsonView.uidWriter
  implicit private val whoWriter = Json.writes[Who]

  case class Join(uid: Uid, userId: Option[User.ID], troll: Boolean, version: Option[SocketVersion])
  case class Connected(enumerator: JsEnumerator, member: Member)

  case class ReloadUid(uid: Uid)
  case class ReloadUidBecauseOf(uid: Uid, chapterId: Chapter.Id)

  case class AddNode(
      position: Position.Ref,
      node: Node,
      variant: draughts.variant.Variant,
      uid: Uid,
      sticky: Boolean,
      relay: Option[Chapter.Relay]
  )
  case class DeleteNode(position: Position.Ref, uid: Uid)
  case class Promote(position: Position.Ref, toMainline: Boolean, uid: Uid)
  case class SetPath(position: Position.Ref, uid: Uid)
  case class SetShapes(position: Position.Ref, shapes: Shapes, uid: Uid)
  case class ReloadMembers(members: StudyMembers)
  case class SetComment(position: Position.Ref, comment: Comment, uid: Uid)
  case class DeleteComment(position: Position.Ref, commentId: Comment.Id, uid: Uid)
  case class SetGlyphs(position: Position.Ref, glyphs: Glyphs, uid: Uid)
  case class SetClock(position: Position.Ref, clock: Option[Centis], uid: Uid)
  case class ReloadChapters(chapters: List[Chapter.Metadata])
  case object ReloadAll
  case class ChangeChapter(uid: Uid, position: Position.Ref)
  case class UpdateChapter(uid: Uid, chapterId: Chapter.Id)
  case class DescChapter(uid: Uid, chapterId: Chapter.Id, desc: Option[String])
  case class DescStudy(uid: Uid, desc: Option[String])
  case class AddChapter(uid: Uid, position: Position.Ref, sticky: Boolean)
  case class ValidationError(uid: Uid, error: String)
  case class SetConceal(position: Position.Ref, ply: Option[Chapter.Ply])
  case class SetLiking(liking: Study.Liking, uid: Uid)
  case class SetTags(chapterId: Chapter.Id, tags: draughts.format.pdn.Tags, uid: Uid)
  case class Broadcast(t: String, msg: JsObject)

  case object GetNbMembers
  case class NbMembers(value: Int)

  case class Messadata(trollish: Boolean = false)
  case object NotifyCrowd
}