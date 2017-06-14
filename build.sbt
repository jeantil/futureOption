import com.typesafe.sbt.SbtGit.GitKeys._
import com.typesafe.sbt.packager.SettingsHelper._

import sbtrelease._
import sbtrelease.ReleaseStateTransformations.{setReleaseVersion=>_,_}

name := "ultimate-build"

scalaVersion := "2.11.7"

lazy val `ultimate-build` = (project in file(".")).enablePlugins(PlayScala, BuildInfoPlugin, GitVersioning, GitBranchPrompt)

//build info
buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)
buildInfoPackage := "eu.byjean"

// Maven convention says that if you're working on `a.b.c-SNAPSHOT`, the NEXT RELEASE will be `a.b.c`.
// The default behaviour of sbt-git says if the most recent release was `a.b.c`, you're working on `a.b.c-SNAPSHOT`.
//
// Since sbt-git is breaking convention, we work around by bumping the inferred version using our chosen version
// bumping strategy - the value of the `releaseVersionBump` setting - so that sbt-release's implementation can save
// us from having to write unnecessary code.
def bumpVersion(inputVersion: String): String = {
  Version.apply(inputVersion)
    .map(_.bump(Version.Bump.default).string)
    .getOrElse(versionFormatError)
}

//git
showCurrentGitBranch
git.useGitDescribe := true
git.baseVersion := "0.0.0"
val VersionRegex = "v([0-9]+.[0-9]+.[0-9]+)-?(.*)?".r
git.gitTagToVersionNumber := {
  case VersionRegex(v, "SNAPSHOT") => Some(s"${bumpVersion(v)}-SNAPSHOT")
  case VersionRegex(v, "") => Some(v)
  case VersionRegex(v, s) => Some(s"${bumpVersion(v)}-$s-SNAPSHOT")
  case v => None
}

// sbt native packager
publishTo := Some("temp" at "file:///tmp/repository")
makeDeploymentSettings(Universal, packageBin in Universal, "zip")

// sbt release
def setVersionOnly(selectVersion: Versions => String): ReleaseStep =  { st: State =>
  val vs = st.get(ReleaseKeys.versions).getOrElse(sys.error("No versions are set! Was this release part executed before inquireVersions?"))
  val selected = selectVersion(vs)

  st.log.info("Setting version to '%s'." format selected)
  val useGlobal =Project.extract(st).get(releaseUseGlobalVersion)
  val versionStr = (if (useGlobal) globalVersionString else versionString) format selected

  reapply(Seq(
    if (useGlobal) version in ThisBuild := selected
    else version := selected
  ), st)
}

lazy val setReleaseVersion: ReleaseStep = setVersionOnly(_._1)

val showNextVersion = settingKey[String]("the future version once releaseNextVersion has been applied to it")
val showReleaseVersion = settingKey[String]("the future version once releaseNextVersion has been applied to it")
showReleaseVersion <<= (version, releaseVersion)((v,f)=>f(v))
showNextVersion <<= (version, releaseNextVersion)((v,f)=>f(v))

releaseProcess := Seq(
  checkSnapshotDependencies,
  inquireVersions,
  setReleaseVersion,
  runTest,
  tagRelease,
  // publishArtifacts,
  ReleaseStep(releaseStepTask(publish in Universal)),
  pushChanges
)
