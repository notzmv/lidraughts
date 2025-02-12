package lidraughts.setup

import draughts.Mode
import draughts.format.FEN
import lidraughts.lobby.Color
import lidraughts.rating.PerfType
import lidraughts.game.PerfPicker

case class FriendConfig(
    variant: draughts.variant.Variant,
    fenVariant: Option[draughts.variant.Variant],
    timeMode: TimeMode,
    time: Double,
    increment: Int,
    days: Int,
    mode: Mode,
    color: Color,
    fen: Option[FEN] = None,
    microMatch: Boolean = false
) extends HumanConfig with Positional {

  val strictFen = false

  def >> = (variant.id, fenVariant.map(_.id), timeMode.id, time, increment, days, mode.id.some, color.name, fen.map(_.value), microMatch).some

  def isPersistent = timeMode == TimeMode.Unlimited || timeMode == TimeMode.Correspondence

  def perfType: Option[PerfType] = PerfPicker.perfType(draughts.Speed(makeClock), variant, makeDaysPerTurn)
}

object FriendConfig extends BaseHumanConfig {

  def <<(v: Int, v2: Option[Int], tm: Int, t: Double, i: Int, d: Int, m: Option[Int], c: String, fen: Option[String], mm: Boolean) =
    new FriendConfig(
      variant = draughts.variant.Variant(v) err "Invalid game variant " + v,
      fenVariant = v2 flatMap draughts.variant.Variant.apply,
      timeMode = TimeMode(tm) err s"Invalid time mode $tm",
      time = t,
      increment = i,
      days = d,
      mode = m.fold(Mode.default)(Mode.orDefault),
      color = Color(c) err "Invalid color " + c,
      fen = fen map FEN,
      microMatch = mm
    )

  val default = FriendConfig(
    variant = variantDefault,
    fenVariant = none,
    timeMode = TimeMode.Unlimited,
    time = 5d,
    increment = 8,
    days = 2,
    mode = Mode.default,
    color = Color.default
  )

  import lidraughts.db.BSON
  import lidraughts.db.dsl._
  import lidraughts.game.BSONHandlers.FENBSONHandler

  private[setup] implicit val friendConfigBSONHandler = new BSON[FriendConfig] {

    override val logMalformed = false

    def reads(r: BSON.Reader): FriendConfig = FriendConfig(
      variant = draughts.variant.Variant orDefault (r int "v"),
      fenVariant = (r intO "v2") flatMap draughts.variant.Variant.apply,
      timeMode = TimeMode orDefault (r int "tm"),
      time = r double "t",
      increment = r int "i",
      days = r int "d",
      mode = Mode orDefault (r int "m"),
      color = Color.White,
      fen = r.getO[FEN]("f") filter (_.value.nonEmpty),
      microMatch = ~r.boolO("mm")
    )

    def writes(w: BSON.Writer, o: FriendConfig) = $doc(
      "v" -> o.variant.id,
      "v2" -> o.fenVariant.map(_.id),
      "tm" -> o.timeMode.id,
      "t" -> o.time,
      "i" -> o.increment,
      "d" -> o.days,
      "m" -> o.mode.id,
      "f" -> o.fen,
      "mm" -> o.microMatch
    )
  }
}
