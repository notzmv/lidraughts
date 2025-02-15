package lidraughts.practice

import scala.collection.breakOut

import lidraughts.study.{ Study, Chapter }

case class PracticeStructure(
    sections: List[PracticeSection]
) {

  def study(id: Study.Id): Option[PracticeStudy] =
    sections.flatMap(_ study id).headOption

  lazy val studiesByIds: Map[Study.Id, PracticeStudy] =
    sections.flatMap(_.studies).map { s =>
      s.id -> s
    }(breakOut)

  lazy val sectionsByStudyIds: Map[Study.Id, PracticeSection] =
    sections.flatMap { sec =>
      sec.studies.map { stu =>
        stu.id -> sec
      }
    }(breakOut)

  lazy val chapterIds: List[Chapter.Id] = sections.flatMap(_.studies).flatMap(_.chapterIds)

  lazy val nbChapters = chapterIds.size

  def findSection(id: Study.Id): Option[PracticeSection] = sectionsByStudyIds get id

  def hasStudy(id: Study.Id) = studiesByIds contains id
}

case class PracticeSection(
    id: String,
    name: String,
    studies: List[PracticeStudy]
) {

  lazy val studiesByIds: Map[Study.Id, PracticeStudy] =
    studies.map { s =>
      s.id -> s
    }(breakOut)

  def study(id: Study.Id): Option[PracticeStudy] = studiesByIds get id
}

case class PracticeStudy(
    id: Study.Id, // study ID
    name: String,
    desc: String,
    chapters: List[Chapter.IdName]
) {

  val slug = lidraughts.common.String slugify name

  def chapterIds = chapters.map(_.id)
}

object PracticeStructure {

  def isChapterNameCommented(name: Chapter.Name) = name.value.startsWith("//")

  def make(conf: PracticeConfig, chapters: Map[Study.Id, Vector[Chapter.IdName]]) =
    PracticeStructure(
      sections = conf.sections.map { sec =>
        PracticeSection(
          id = sec.id,
          name = sec.name,
          studies = sec.studies.map { stu =>
            val id = Study.Id(stu.id)
            PracticeStudy(
              id = id,
              name = stu.name,
              desc = stu.desc,
              chapters = chapters.get(id).??(_.filterNot { c =>
                isChapterNameCommented(c.name)
              }.toList)
            )
          }
        )
      }
    )
}
