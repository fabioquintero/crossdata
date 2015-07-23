/*
 * Copyright (C) 2015 Stratio (http://stratio.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.spark.sql.crossdata

import java.io.File

import org.apache.spark.Logging
import org.apache.spark.sql.catalyst.CatalystConf
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.execution.ClearCacheCommand
import org.mapdb.{DB, DBMaker}

class DefaultCatalog(val conf: CatalystConf) extends XDCatalog with Logging {

  lazy val db: DB = DBMaker.newFileDB(new File("catalog")).closeOnJvmShutdown.make

  lazy val tables: java.util.Map[String, LogicalPlan] = db.getHashMap("catalog")

  override def loadAll(): Unit = {
    logInfo("XDCatalog: loadAll")
  }

  override def tableExists(tableIdentifier: Seq[String]): Boolean = {
    logInfo("XDCatalog: tableExists")
    tables.containsKey(tableIdentifier.mkString("."))
  }

  override def unregisterAllTables(): Unit = {
    logInfo("XDCatalog: unregisterAllTables")
    tables.clear
    db.commit
  }

  override def unregisterTable(tableIdentifier: Seq[String]): Unit = {
    logInfo("XDCatalog: unregisterTable")
    tables.remove(tableIdentifier.mkString("."))
    db.commit
  }

  override def lookupRelation(tableIdentifier: Seq[String], alias: Option[String]): LogicalPlan = {
    logInfo("XDCatalog: lookupRelation")
    tables.get(tableIdentifier.mkString("."))
  }

  override def registerTable(tableIdentifier: Seq[String], plan: LogicalPlan): Unit = {
    logInfo("XDCatalog: registerTable")
    tables.put(tableIdentifier.mkString("."), plan)
    db.commit
  }

  override def getTables(databaseName: Option[String]): Seq[(String, Boolean)] = {
    logInfo("XDCatalog: getTables")
    import collection.JavaConversions._
    tables.map {
      case (name, _) => (name, false)
    }.toSeq
  }

  override def refreshTable(databaseName: String, tableName: String): Unit = {
    logInfo("XDCatalog: refreshTable")
  }

}