
// https://github.com/sbt/sbt-github-actions/tags
addSbtPlugin("com.github.sbt" % "sbt-github-actions" % "0.24.0")

// https://scalacenter.github.io/scalafix/docs/users/installation.html
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.0")

// For publishing to Sonatype
// https://github.com/xerial/sbt-sonatype
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.10.0")

// For signing artifacts
// https://github.com/sbt/sbt-pgp
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.2.1")

// For automating releases
// https://github.com/sbt/sbt-release
addSbtPlugin("com.github.sbt" % "sbt-release" % "1.4.0")
