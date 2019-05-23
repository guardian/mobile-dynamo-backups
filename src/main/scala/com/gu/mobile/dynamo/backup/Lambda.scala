package com.gu.mobile.dynamo.backup

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, ZoneId}
import java.util.Date

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

  val daysToBackup = configuration.daysToLookBackup
  val daysToBackupLong = daysToBackup - 1

  val formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy")

  def handler() : Unit = {

     def maybeEmptyString2Option(s: String) = Option(s).filter(_.trim.isEmpty)

    //Because aws uses java.util.date!
     def toUtilDate(localDate: LocalDate) : Date = {
      Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant)
    }

    def makeListBackupsRequest(tableName: String, now: LocalDate) = new ListBackupsRequest()
      .withTimeRangeUpperBound(toUtilDate(now))
      .withTimeRangeLowerBound(toUtilDate(now.minusDays(daysToBackup)))
      .withTableName(tableName)


    val now = LocalDate.now()

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
           def processLastArn(lastBackupArn: String, backupResult: ListBackupsResult, deletedCount: Int): Int = {
               val backupSummaries = backupResult.getBackupSummaries.asScala
               backupSummaries.foreach { record =>
                 val backupArn = record.getBackupArn
                 dynamoDBClient.deleteBackup(new DeleteBackupRequest().withBackupArn(backupArn))
                 logger.info(s"Has deleted $backupArn")
               }
               val latestBackupResult = dynamoDBClient.listBackups(makeListBackupsRequest(tableName,now))
               maybeEmptyString2Option(latestBackupResult.getLastEvaluatedBackupArn) match {
                 case Some(latestArn) =>
                   processLastArn(latestArn, latestBackupResult, deletedCount + 1)
                 case None => deletedCount
              }
           }

           val deleted = maybeEmptyString2Option(latestResponse.getLastEvaluatedBackupArn).map {
             latestBackupArn => processLastArn(latestBackupArn, latestResponse, 0)
           }.getOrElse(0)
           logger.info(s"Deleted ${deleted} backups")
         } else {
           logger.info(s"No recent backups to delte")
         }
     }
  }
}
