import com.typesafe.sbt.packager.Keys.scriptClasspath
import com.typesafe.sbt.SbtScalariform.autoImport.scalariformFormat
import com.typesafe.sbt.web.SbtWeb.autoImport._
import play.Play.autoImport._
import play.sbt.PlayImport._
import PlayKeys._

import BuildSettings._
import Dependencies._

lazy val root = Project("lidraughts", file("."))
  .enablePlugins(_root_.play.sbt.PlayScala)
  .dependsOn(api)
  .aggregate(api)

scalaVersion := globalScalaVersion
resolvers ++= Dependencies.Resolvers.commons
scalacOptions ++= compilerOptions
incOptions := incOptions.value.withNameHashing(true)
updateOptions := updateOptions.value.withCachedResolution(true)
sources in doc in Compile := List()
// disable publishing the main API jar
publishArtifact in (Compile, packageDoc) := false
// disable publishing the main sources jar
publishArtifact in (Compile, packageSrc) := false
// don't stage the conf dir
externalizeResources := false
// shorter prod classpath
scriptClasspath := Seq("*")
// offline := true
libraryDependencies ++= Seq(
  scalaz, compression, scalalib, hasher, typesafeConfig, findbugs,
  reactivemongo.driver, reactivemongo.iteratees, akka.actor, akka.slf4j,
  maxmind, prismic, netty, guava, markdown,
  kamon.core, kamon.influxdb, scalatags,
  java8compat, semver, scrimage, scalaConfigs, scaffeine
)
resourceDirectory in Assets := (sourceDirectory in Compile).value / "assets"
unmanagedResourceDirectories in Assets ++= (if (scala.sys.env.get("SERVE_ASSETS").exists(_ == "1")) Seq(baseDirectory.value / "public") else Nil)

scalariformPreferences := scalariformPrefs(scalariformPreferences.value)
excludeFilter in scalariformFormat := "*Routes*"

routesGenerator := LidraughtsRoutesGenerator

lazy val modules = Seq(
  common, db, draughts, rating, user, security, hub, socket,
  message, notifyModule, i18n, game, bookmark, search,
  gameSearch, timeline, forum, forumSearch, team, teamSearch,
  analyse, mod, site, round, pool, lobby, setup,
  importer, tournament, simul, relation, report, pref, // simulation,
  evaluation, chat, puzzle, tv, coordinate, blog,
  history, shutup, push, explorer,
  playban, insight, perfStat, slack, quote, challenge,
  study, studySearch, draughtsnet, learn, plan,
  event, practice, evalCache, irwin,
  activity, relay, streamer, bot, swiss
)

lazy val moduleRefs = modules map projectToRef
lazy val moduleCPDeps = moduleRefs map { new sbt.ClasspathDependency(_, None) }

lazy val api = module("api", moduleCPDeps)
  .settings(
    libraryDependencies ++= provided(
      play.api, hasher, typesafeConfig, findbugs,
      reactivemongo.driver, reactivemongo.iteratees,
      kamon.core, kamon.influxdb
    ),
    aggregate in Runtime := false,
    aggregate in Test := true  // Test <: Runtime
  ) aggregate (moduleRefs: _*)

lazy val i18n = module("i18n", Seq(common, db, user, hub)).settings(
  sourceGenerators in Compile += Def.task {
    MessageCompiler(
      sourceDir = new File("translation/source"),
      destDir = new File("translation/dest"),
      dbs = List("site", "arena", "emails", "learn", "activity", "coordinates", "study", "tfa", "team", "swiss", "broadcast", "streamer", "lag", "patron"),
      compileTo = (sourceManaged in Compile).value / "messages"
    )
  }.taskValue,
  libraryDependencies ++= provided(play.api, scalatags)
)

lazy val insight = module("insight", Seq(common, game, user, analyse, relation, pref, socket, round, security)
).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver, reactivemongo.iteratees, scalatags)
)

lazy val draughts = module("draughts", Seq()).settings(
  parallelExecution in Test := false
)

lazy val common = module("common", Seq(draughts)).settings(
  libraryDependencies ++= provided(play.api, play.test, reactivemongo.driver, kamon.core, scalatags) ++ Seq(scaffeine)
)

lazy val puzzle = module("puzzle", Seq(common, memo, hub, history, db, user, rating, pref, tree, game)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver, reactivemongo.iteratees)
)

lazy val quote = module("quote", Seq())

lazy val coordinate = module("coordinate", Seq(common, db)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val explorer = module("explorer", Seq(common, db, game, importer)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver, reactivemongo.iteratees)
)

lazy val blog = module("blog", Seq(common, memo, timeline)).settings(
  libraryDependencies ++= provided(play.api, prismic, reactivemongo.driver, reactivemongo.iteratees)
)

lazy val evaluation = module("evaluation", Seq(common, hub, db, user, game, analyse)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val rating = module("rating", Seq(common, db, draughts)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val perfStat = module("perfStat", Seq(common, db, user, game, rating)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val history = module("history", Seq(common, db, memo, game, user)).settings(
  libraryDependencies ++= provided(play.api, scalatags, reactivemongo.driver)
)

lazy val db = module("db", Seq(common)).settings(
  libraryDependencies ++= provided(play.test, play.api, reactivemongo.driver, hasher, scrimage)
)

lazy val memo = module("memo", Seq(common, db)).settings(
  libraryDependencies ++= Seq(findbugs, scaffeine, scalaConfigs) ++ provided(play.api, reactivemongo.driver)
)

lazy val search = module("search", Seq(common, hub)).settings(
  libraryDependencies ++= provided(play.api)
)

lazy val chat = module("chat", Seq(common, db, user, security, i18n, socket)).settings(
  libraryDependencies ++= provided(play.api, scalatags, reactivemongo.driver)
)

lazy val timeline = module("timeline", Seq(common, db, game, user, hub, security, relation)).settings(
  libraryDependencies ++= provided(play.api, play.test, reactivemongo.driver)
)

lazy val event = module("event", Seq(common, db, memo, i18n)).settings(
  libraryDependencies ++= provided(play.api, play.test, scalatags, reactivemongo.driver)
)

lazy val mod = module("mod", Seq(common, db, user, hub, security, tournament, simul, game, analyse, evaluation,
  report, notifyModule, history, perfStat)).settings(
  libraryDependencies ++= provided(play.api, play.test, reactivemongo.driver)
)

lazy val user = module("user", Seq(common, memo, db, hub, rating)).settings(
  libraryDependencies ++= provided(play.api, play.test, reactivemongo.driver, hasher,
    reactivemongo.iteratees // only for bcrypt migration
  )
)

lazy val game = module("game", Seq(common, memo, db, draughts, hub, user, chat)).settings(
  libraryDependencies ++= provided(compression, play.api, reactivemongo.driver, reactivemongo.iteratees)
)

lazy val gameSearch = module("gameSearch", Seq(common, hub, search, game)).settings(
  libraryDependencies ++= provided(
    play.api, reactivemongo.driver, reactivemongo.iteratees
  )
)

lazy val tv = module("tv", Seq(common, db, hub, socket, game, round, user)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver, hasher)
)

lazy val analyse = module("analyse", Seq(common, hub, game, user, notifyModule, evalCache, draughts)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val bot = module("bot", Seq(common, db, hub, game, user, challenge, chat)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val round = module("round", Seq(common, db, memo, hub, socket, evalCache, draughtsnet, analyse, game, user, i18n, pref, chat, history, playban)).settings(
  libraryDependencies ++= provided(play.api, scalatags, hasher, kamon.core, reactivemongo.driver, reactivemongo.iteratees)
)

lazy val pool = module("pool", Seq(common, game, user, playban)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val activity = module("activity", Seq(common, game, analyse, user, forum, study, pool, puzzle, tournament, practice, team)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val lobby = module("lobby", Seq(common, db, memo, hub, socket, game, user, round, timeline, relation, playban, security, pool)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val setup = module("setup", Seq(common, db, memo, hub, socket, game, user, lobby, pref, relation)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val importer = module("importer", Seq(common, game, round)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val tournament = module("tournament", Seq(common, hub, socket, game, round, security, chat, memo, quote, history, notifyModule, i18n)).settings(
  libraryDependencies ++= provided(play.api, scalatags, reactivemongo.driver, reactivemongo.iteratees)
)

lazy val swiss = module("swiss", Seq(common, hub, socket, game, round, security, chat, memo, quote, i18n)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver, scalatags)
)

lazy val simul = module("simul", Seq(common, hub, socket, game, round, chat, memo, quote, evaluation)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver, scalatags)
)

lazy val draughtsnet = module("draughtsnet", Seq(common, game, analyse, db, evalCache)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver, semver)
)

lazy val irwin = module("irwin", Seq(common, db, user, game, tournament, mod)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val oauth = module("oauth", Seq(common, db, user)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val security = module("security", Seq(common, hub, db, user, i18n, slack, oauth)).settings(
  libraryDependencies ++= provided(play.api, scalatags, reactivemongo.driver, maxmind, hasher)
)

lazy val shutup = module("shutup", Seq(common, db, hub, game, relation)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val challenge = module("challenge", Seq(common, db, hub, setup, game, relation, pref)).settings(
  libraryDependencies ++= provided(play.api, scalatags, reactivemongo.driver)
)

lazy val study = module("study", Seq(common, db, hub, socket, game, round, importer, notifyModule, relation, evalCache, explorer, i18n)).settings(
  libraryDependencies ++= provided(play.api, scalatags, reactivemongo.driver)
)

lazy val relay = module("relay", Seq(common, study, simul)).settings(
  libraryDependencies ++= Seq(scalaUri) ++ provided(play.api, reactivemongo.driver, markdown)
)

lazy val studySearch = module("studySearch", Seq(common, hub, study, search)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver, reactivemongo.iteratees)
)

lazy val learn = module("learn", Seq(common, db, user)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val streamer = module("streamer", Seq(common, hub, db, user, notifyModule)).settings(
  libraryDependencies ++= provided(play.api, scalatags, reactivemongo.driver)
)

lazy val evalCache = module("evalCache", Seq(common, db, user, security, socket, tree)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val practice = module("practice", Seq(common, db, memo, user, study)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val playban = module("playban", Seq(common, db, game, message, chat)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val push = module("push", Seq(common, db, user, game, challenge, message)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val slack = module("slack", Seq(common, hub, user)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val plan = module("plan", Seq(common, user, notifyModule)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val relation = module("relation", Seq(common, db, memo, hub, user, game, pref)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver, reactivemongo.iteratees)
)

lazy val pref = module("pref", Seq(common, db, user)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val message = module("message", Seq(common, db, user, hub, relation, security, shutup, notifyModule)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val forum = module("forum", Seq(common, db, user, security, hub, mod, notifyModule)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val forumSearch = module("forumSearch", Seq(common, hub, forum, search)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver, reactivemongo.iteratees)
)

lazy val team = module("team", Seq(common, memo, db, user, forum, security, hub, notifyModule)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver, reactivemongo.iteratees)
)

lazy val teamSearch = module("teamSearch", Seq(common, hub, team, search)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver, reactivemongo.iteratees)
)

lazy val bookmark = module("bookmark", Seq(common, memo, db, hub, user, game)).settings(
  libraryDependencies ++= provided(play.api, play.test, reactivemongo.driver)
)

lazy val report = module("report", Seq(common, db, user, game, security, playban)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver, reactivemongo.iteratees)
)

lazy val notifyModule = module("notify", Seq(common, db, game, user, hub, relation)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val site = module("site", Seq(common, socket)).settings(
  libraryDependencies ++= provided(play.api)
)

lazy val tree = module("tree", Seq(common, draughts)).settings(
  libraryDependencies ++= provided(play.api)
)

lazy val socket = module("socket", Seq(common, hub, memo, tree)).settings(
  libraryDependencies ++= provided(play.api, reactivemongo.driver)
)

lazy val hub = module("hub", Seq(common, draughts)).settings(
  libraryDependencies ++= Seq(scaffeine) ++ provided(play.api)
)
