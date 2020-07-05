package lidraughts.user

case class Title(value: String) extends AnyVal with StringValue {
  def is64 = value.endsWith("-64")
  def with64 = if (!is64) Title(value + "-64") else this
  def without64 = if (is64) Title(value.dropRight(3)) else this
}

object Title {

  implicit val titleIso = lidraughts.common.Iso.string[Title](Title.apply, _.value)
  implicit val titleBsonHandler = lidraughts.db.dsl.stringIsoHandler(Title.titleIso)
  implicit val titleJsonWrites = lidraughts.common.PimpedJson.stringIsoWriter(Title.titleIso)

  val LM = Title("LM")
  val BOT = Title("BOT")

  // important: abbreviations are as stated on fmjd profile pages
  val all = Seq(
    Title("GMI") -> "International Grandmaster",
    Title("MI") -> "International Master",
    Title("MF") -> "FMJD Master",
    Title("GMN") -> "National Grandmaster",
    Title("MN") -> "National Master",
    Title("cMN") -> "Candidate National Master",
    Title("GMIF") -> "Woman International Grandmaster",
    Title("MIF") -> "Woman International Master",
    Title("MFF") -> "Woman FMJD Master",
    Title("MNF") -> "Woman National Master",
    Title("cMNF") -> "Woman Candidate National Master",
    Title("GMI-64") -> "International Grandmaster (64 squares)",
    Title("MI-64") -> "International Master (64 squares)",
    Title("MF-64") -> "FMJD Master (64 squares)",
    Title("GMN-64") -> "National Grandmaster (64 squares)",
    Title("MN-64") -> "National Master (64 squares)",
    Title("cMN-64") -> "Candidate National Master (64 squares)",
    Title("GMIF-64") -> "Woman International Grandmaster (64 squares)",
    Title("MIF-64") -> "Woman International Master (64 squares)",
    Title("MFF-64") -> "Woman FMJD Master (64 squares)",
    Title("MNF-64") -> "Woman National Master (64 squares)",
    Title("cMNF-64") -> "Woman Candidate National Master (64 squares)",
    LM -> "Lidraughts Master",
    BOT -> "Draughts Robot"
  )

  val names = all.toMap
  lazy val fromNames = all.map(_.swap).toMap

  def titleName(title: Title): String = names.getOrElse(title, title.value)

  def get(str: String): Option[Title] = Title(str.toUpperCase).some filter names.contains
  def get(strs: List[String]): List[Title] = strs flatMap { get(_) }

  object fromUrl {

    import play.api.libs.ws.WS
    import play.api.Play.current

    // https://www.fmjd.org/?p=pcard&id=16091
    private val FmjdProfileUrlRegex = """(?:https?://)(?:www\.)?fmjd\.org/\?p=pcard&id=(\d+)""".r
    //<tr>\n<td>TITLE<\/td>\n<td>RATING\s(POSITION)<td>\n<\/tr><tr>
    private val FmjdProfileTitleRegex = """<tr>\s*<td>(\w{2,4})</td>\s*<td>\d{1,4}\s+\(\d+\)<td>\s*</tr>\s*<tr>""".r.unanchored

    def apply(url: String): Fu[Option[Title]] = url.trim match {
      case FmjdProfileUrlRegex(id) => parseIntOption(id) ?? fromFmjdProfile
      case _ => fuccess(none)
    }

    private def fromFmjdProfile(id: Int): Fu[Option[Title]] = {
      val url = s"""https://www.fmjd.org/?p=pcard&id=$id"""
      WS.url(url).get().map(_.body) map {
        case FmjdProfileTitleRegex(titleStr) =>
          val title = Title(titleStr)
          Title.names.contains(title) option title
        case _ =>
          logger.info(s"No title found on $url")
          none
      }
    }
  }
}
