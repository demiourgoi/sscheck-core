name := "sscheck-core"

organization := "io.github.demiourgoi"

version := "0.5.3-SNAPSHOT"

scalaVersion := "2.13.15"

crossScalaVersions := Seq("2.13.15")

// https://mvnrepository.com/artifact/org.specs2/specs2-core_2.13
lazy val specs2Version = "4.21.0"

// Use `sbt doc` to generate scaladoc, more on chapter 14.8 of "Scala Cookbook"

// show all the warnings: http://stackoverflow.com/questions/9415962/how-to-see-all-the-warnings-in-sbt-0-11
scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation")

// additional libraries: NOTE as we are writing a testing library they should also be available for main
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19"

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.18.1"

libraryDependencies += "org.specs2" %% "specs2-core" % specs2Version

libraryDependencies += "org.specs2" %% "specs2-scalacheck" % specs2Version

libraryDependencies += "org.specs2" %% "specs2-matcher-extra" % specs2Version

libraryDependencies += "org.specs2" %% "specs2-junit" % specs2Version

libraryDependencies += "org.slf4j" % "slf4j-api" % "2.0.16"

libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.3.8"

// Parallel collections not in standard lib on Sclaa 2.13 https://github.com/scala/scala-parallel-collections
// https://mvnrepository.com/artifact/org.scala-lang.modules/scala-parallel-collections
libraryDependencies += "org.scala-lang.modules" %% "scala-parallel-collections" % "1.1.0"

resolvers ++= Seq(
  "MVN Repository.com" at "https://mvnrepository.com/artifact/",
  "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
  "Spark Packages Repo" at "https://dl.bintray.com/spark-packages/maven"
)

// https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html
// Configure Sonatype credentials
credentials += Credentials(Path.userHome / ".demiourgoi_sonatype_creds")

ThisBuild / organizationName := "demiourgoi"
ThisBuild / organizationHomepage := Some(url("https://github.com/demiourgoi"))
ThisBuild / versionScheme := Some("semver-spec")
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/demiourgoi/sscheck-core"),
    "scm:git@github.com:demiourgoi/sscheck-core.git"
  )
)
ThisBuild / developers := List(
  Developer(
    id    = "juanrh",
    name  = "Juan Rodriguez Hortala",
    email = "",
    url   = url("https://juanrh.github.io")
  )
)

ThisBuild / description := "Distributed computing engine independent code for sscheck."
ThisBuild / licenses := List(
  "Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")
)
ThisBuild / homepage := Some(url("https://github.com/demiourgoi/sscheck-core"))

// Remove all additional repository other than Maven Central from POM
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishMavenStyle := true

// Determine if this is a snapshot version
ThisBuild / isSnapshot := {
  version.value.endsWith("-SNAPSHOT")
}

// new setting for the Central Portal
// https://central.sonatype.org/publish/publish-portal-snapshots/#publishing-snapshot-releases-for-your-project
ThisBuild / publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
  else localStaging.value
}
