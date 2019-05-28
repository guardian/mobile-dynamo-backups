package com.gu.mobile.dynamo.backup

import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}
import com.gu.{AppIdentity, AwsIdentity, Logging}
import com.typesafe.config.Config
import collection.JavaConverters._

class Configuration extends Logging {

  val appName = Option(System.getenv("App")).getOrElse(sys.error("No app name set. Lambda will not run"))

  private val conf: Config = {
    val identity = AppIdentity.whoAmI(defaultAppName = appName)
    logger.info(s"Trying $identity")
    ConfigurationLoader.load(identity = identity) {
      case AwsIdentity(_, stack, stage, _) =>
        val path = s"/$appName/$stack/$stage"
        logger.warn(s"Using path $path")
        SSMConfigurationLocation(path)
    }
  }

  val tables: List[String] = conf.getStringList("tables").asScala.toList
  val daysToLookBackup = conf.getInt("days")


}
