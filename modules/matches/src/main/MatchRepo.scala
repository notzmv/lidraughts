package lidraughts.matches

import org.joda.time.DateTime
import reactivemongo.bson._
import reactivemongo.core.commands._

import draughts.Status
import draughts.variant.Variant
import lidraughts.db.BSON
import lidraughts.db.dsl._

private[matches] final class MatchRepo(matchColl: Coll) {

  import lidraughts.db.BSON.BSONJodaDateTimeHandler
  import reactivemongo.bson.Macros
  private implicit val MatchStatusBSONHandler = new BSONHandler[BSONInteger, MatchStatus] {
    def read(bsonInt: BSONInteger): MatchStatus = MatchStatus(bsonInt.value) err s"No such match status: ${bsonInt.value}"
    def write(x: MatchStatus) = BSONInteger(x.id)
  }
  private implicit val DraughtsStatusBSONHandler = lidraughts.game.BSONHandlers.StatusBSONHandler
  private implicit val VariantBSONHandler = new BSONHandler[BSONInteger, Variant] {
    def read(bsonInt: BSONInteger): Variant = Variant(bsonInt.value) err s"No such variant: ${bsonInt.value}"
    def write(x: Variant) = BSONInteger(x.id)
  }
  private implicit val ClockBSONHandler = {
    import draughts.Clock.Config
    implicit val clockHandler = Macros.handler[Config]
    Macros.handler[MatchClock]
  }
  private implicit val PlayerBSONHandler = Macros.handler[MatchPlayer]
  private implicit val ApplicantBSONHandler = Macros.handler[MatchApplicant]

  private implicit val MatchBSONHandler = Macros.handler[Match]

  private val createdSelect = $doc("status" -> MatchStatus.Created.id)
  private val startedSelect = $doc("status" -> MatchStatus.Started.id)
  private val finishedSelect = $doc("status" -> MatchStatus.Finished.id)
  private val createdSort = $doc("createdAt" -> -1)

  def find(id: Match.ID): Fu[Option[Match]] =
    matchColl.byId[Match](id)

  def byIds(ids: List[Match.ID]): Fu[List[Match]] =
    matchColl.byIds[Match](ids)

  def exists(id: Match.ID): Fu[Boolean] =
    matchColl.exists($id(id))

  def findStarted(id: Match.ID): Fu[Option[Match]] =
    find(id) map (_ filter (_.isStarted))

  def findCreated(id: Match.ID): Fu[Option[Match]] =
    find(id) map (_ filter (_.isCreated))

  def allCreated: Fu[List[Match]] =
    matchColl.find(createdSelect).sort(createdSort).list[Match]()

  def allCreatedFeaturable: Fu[List[Match]] = matchColl.find(
    createdSelect ++ $doc("createdAt" $gte DateTime.now.minusMinutes(20))
  ).sort(createdSort).list[Match]()

  def allStarted: Fu[List[Match]] = matchColl.find(
    startedSelect
  ).sort(createdSort).list[Match]()

  def allFinished(max: Int): Fu[List[Match]] = matchColl.find(
    finishedSelect
  ).sort(createdSort).list[Match](max)

  def allNotFinished =
    matchColl.find($doc("status" $ne MatchStatus.Finished.id)).list[Match]()

  def create(mtch: Match): Funit =
    matchColl insert mtch void

  def update(mtch: Match) =
    matchColl.update($id(mtch.id), mtch).void

  def remove(mtch: Match) =
    matchColl.remove($id(mtch.id)).void

  def cleanup = matchColl.remove(
    createdSelect ++ $doc(
      "createdAt" -> $doc("$lt" -> (DateTime.now minusMinutes 60))
    )
  )
}
