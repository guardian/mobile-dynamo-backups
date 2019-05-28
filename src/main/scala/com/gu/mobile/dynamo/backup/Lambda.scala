package com.gu.mobile.dynamo.backup

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, ZoneId}
import java.util.Date
import java.util.concurrent.TimeUnit

import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Regions.EU_WEST_1
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model.{CreateBackupRequest, DeleteBackupRequest, ListBackupsRequest, ListBackupsResult}
import com.gu.Logging

import collection.JavaConverters._


object Lambda extends Logging {

  lazy val configuration: Configuration = {
    logger.debug("Creating configuration")
    new Configuration()
  }

  val credentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("mobile"),
    DefaultAWSCredentialsProviderChain.getInstance()
  )

  lazy val dynamoDBClient: AmazonDynamoDBClient = {
    logger.debug("Creating dynamo db client")
    new AmazonDynamoDBClient(credentials).withRegion(EU_WEST_1)
  }

  val daysToBackup = configuration.daysToBackup
  val daysToBackupLong = daysToBackup - 1

  val formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy")

  def handler() : Unit = {

     def maybeEmptyString2Option(s: String) = Option(s).filter(_.trim.isEmpty)

    //Because aws uses java.util.date!
     def toUtilDate(localDate: LocalDateTime) : Date = {
      Date.from(localDate.atZone(ZoneId.systemDefault()).toInstant)
    }

    def makeListBackupsRequest(tableName: String, now: LocalDateTime) = new ListBackupsRequest()
      .withTimeRangeLowerBound(toUtilDate(now.minusMinutes(120)))
      .withTimeRangeUpperBound(toUtilDate(now.minusMinutes(15)))
      .withTableName(tableName)


    val now = LocalDateTime.now()

    configuration.tables.map {
       tableName =>
         logger.info(s"Backing up $tableName")
         val backupRequest = new CreateBackupRequest()
           .withTableName(tableName)
           .withBackupName(s"$tableName-backup-${now.format(formatter)}")
         val backupResponse = dynamoDBClient.createBackup(backupRequest)
         logger.info(s"Backuo completed succesfully for table $tableName")

         val latestResponse = dynamoDBClient.listBackups(makeListBackupsRequest(tableName,now))

         val latestBackupCount = latestResponse.getBackupSummaries.size
         logger.info(s"Total backup count for table: $latestBackupCount")

         if (latestBackupCount > daysToBackup) {
             val backupSummaries = latestResponse.getBackupSummaries.asScala
             backupSummaries.foreach { record =>
               val backupArn = record.getBackupArn
               dynamoDBClient.deleteBackup(new DeleteBackupRequest().withBackupArn(backupArn))
             }
           logger.info(s"Deleted ${latestBackupCount} backups")
         } else {
           logger.info(s"No recent backups to delte")
         }
     }
  }
}
