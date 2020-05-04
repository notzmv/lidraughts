package lidraughts.swiss

import draughts.Clock.{ Config => ClockConfig }
import draughts.variant.Variant
import lidraughts.db.BSON
import lidraughts.db.dsl._
import reactivemongo.bson._

private object BsonHandlers {

  implicit val clockHandler = new BSONHandler[BSONDocument, ClockConfig] {
    def read(doc: BSONDocument) = ClockConfig(
      doc.getAs[Int]("limit").get,
      doc.getAs[Int]("increment").get
    )

    def write(config: ClockConfig) = BSONDocument(
      "limit" -> config.limitSeconds,
      "increment" -> config.incrementSeconds
    )
  }
  implicit val variantHandler = new BSONHandler[BSONInteger, Variant] {
    def read(b: BSONInteger): Variant = Variant.orDefault(b.value)
    def write(x: Variant) = BSONInteger(x.id)
  }
  implicit val swissPointsHandler = intAnyValHandler[Swiss.Points](_.double, Swiss.Points.apply)
  implicit val swissScoreHandler = doubleAnyValHandler[Swiss.Score](_.value, Swiss.Score.apply)
  implicit val playerNumberHandler = intAnyValHandler[SwissPlayer.Number](_.value, SwissPlayer.Number.apply)
  implicit val roundNumberHandler = intAnyValHandler[SwissRound.Number](_.value, SwissRound.Number.apply)
  implicit val swissIdHandler = stringAnyValHandler[Swiss.Id](_.value, Swiss.Id.apply)
  implicit val playerIdHandler = stringAnyValHandler[SwissPlayer.Id](_.value, SwissPlayer.Id.apply)

  implicit val playerHandler = new BSON[SwissPlayer] {
    import SwissPlayer.Fields._
    def reads(r: BSON.Reader) = SwissPlayer(
      _id = r.get[SwissPlayer.Id](id),
      swissId = r.get[Swiss.Id](swissId),
      number = r.get[SwissPlayer.Number](number),
      userId = r str userId,
      rating = r int rating,
      provisional = r boolD provisional,
      points = r.get[Swiss.Points](points),
      score = r.get[Swiss.Score](score)
    )
    def writes(w: BSON.Writer, o: SwissPlayer) = $doc(
      id -> o._id,
      swissId -> o.swissId,
      number -> o.number,
      userId -> o.userId,
      rating -> o.rating,
      provisional -> w.boolO(o.provisional),
      points -> o.points,
      score -> o.score
    )
  }

  implicit val pairingStatusHandler: BSONHandler[BSONValue, SwissPairing.Status] = new BSONHandler[BSONValue, SwissPairing.Status] {
    def read(v: BSONValue): SwissPairing.Status = v match {
      case BSONInteger(n) => Right(SwissPlayer.Number(n).some)
      case BSONBoolean(true) => Left(SwissPairing.Ongoing)
      case _ => Right(none)
    }
    def write(s: SwissPairing.Status) = s match {
      case Left(_) => BSONBoolean(true)
      case Right(Some(n)) => BSONInteger(n.value)
      case _ => BSONNull
    }
  }
  implicit val pairingHandler = new BSON[SwissPairing] {
    import SwissPairing.Fields._
    def reads(r: BSON.Reader) =
      r.get[List[SwissPlayer.Number]](players) match {
        case List(w, b) =>
          SwissPairing(
            _id = r str id,
            swissId = r.get[Swiss.Id](swissId),
            round = r.get[SwissRound.Number](round),
            white = w,
            black = b,
            status = r.get[SwissPairing.Status](status)
          )
        case _ => sys error "Invalid swiss pairing users"
      }
    def writes(w: BSON.Writer, o: SwissPairing) = $doc(
      id -> o._id,
      swissId -> o.swissId,
      round -> o.round,
      gameId -> o.gameId,
      players -> o.players,
      status -> o.status
    )
  }

  implicit val swissHandler = Macros.handler[Swiss]
}
