
name := "mobile-dynamo-backups"

version := "1.0"

scalaVersion := "2.12.8"


resolvers ++= Seq(
  "Guardian Platform Bintray" at "https://dl.bintray.com/guardian/platforms",
  "Guardian Frontend Bintray" at "https://dl.bintray.com/guardian/frontend",
  "Guardian Mobile Bintray" at "https://dl.bintray.com/guardian/mobile"
)

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-simple" % "1.7.25",
  "com.typesafe" % "config" % "1.3.2",
  "com.gu" %% "simple-configuration-ssm" % "1.4.1",
  "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.11.555",
  "com.amazonaws" % "aws-lambda-java-core" % "1.1.0"
)

enablePlugins(RiffRaffArtifact)

assemblyJarName := s"${name.value}.jar"
riffRaffPackageType := assembly.value
riffRaffManifestProjectName := s"mobile-notifications:${name.value}"
riffRaffUploadArtifactBucket := Option("riffraff-artifact")
riffRaffUploadManifestBucket := Option("riffraff-builds")
riffRaffArtifactResources += (file("cfn.yaml"), s"${name.value}-cfn/cfn.yaml")

