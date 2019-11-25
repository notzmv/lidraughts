package lidraughts

package object blog extends PackageObject {

  private[blog] def logger = lidraughts.log("blog")

  lazy val thisYear = org.joda.time.DateTime.now.getYear

  lazy val allYears = (thisYear to 2019 by -1).toList
}
