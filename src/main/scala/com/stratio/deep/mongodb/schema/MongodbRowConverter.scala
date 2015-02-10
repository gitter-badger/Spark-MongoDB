/*
 *  Licensed to STRATIO (C) under one or more contributor license agreements.
 *  See the NOTICE file distributed with this work for additional information
 *  regarding copyright ownership. The STRATIO (C) licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package com.stratio.deep.mongodb.schema

import com.mongodb.casbah.Imports._
import com.stratio.deep.schema.DeepRowConverter
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.catalyst.expressions.GenericRow
import org.apache.spark.sql.catalyst.types._
import org.apache.spark.sql.{Row, StructType}

import scala.collection.mutable.ArrayBuffer

/**
 * Created by rmorandeira on 3/02/15.
 */
object MongodbRowConverter extends DeepRowConverter[DBObject]
with JsonSupport
with Serializable {

  def asRow(schema: StructType, rdd: RDD[DBObject]): RDD[Row] = {
    rdd.map { record =>
      recordAsRow(dbObjectToMap(record), schema)
    }
  }

  def recordAsRow(
    json: Map[String, AnyRef],
    schema: StructType): Row = {
    val values: Seq[Any] = schema.fields.map {
      case StructField(name, dataType, _, _) =>
        json.get(name).flatMap(v => Option(v)).map(
          toSQL(_, dataType)).orNull
    }
    Row.fromSeq(values)
  }

  def rowAsDBObject(row: Row, schema: StructType): DBObject = {
    val attMap: Map[String, Any] = schema.fields.zipWithIndex.map {
      case (att, idx) => (att.name, toDBObject(row(idx),att.dataType))
    }.toMap
    attMap
  }

  def toDBObject(value: Any, dataType: DataType): Any = {
    Option(value).map{v =>
      (dataType,v) match {
        case (ArrayType(elementType, _),array: ArrayBuffer[Any@unchecked]) =>
          val list: List[Any] = array.map{
            case obj => toDBObject(obj,elementType)
          }.toList
          list
        case (struct: StructType,value: GenericRow) =>
          rowAsDBObject(value,struct)
        case _ => v
      }
    }.orNull
  }

  def toSQL(value: Any, dataType: DataType): Any = {
    Option(value).map{value =>
      dataType match {
        case ArrayType(elementType, _) =>
          value.asInstanceOf[BasicDBList].map(toSQL(_, elementType))
        case struct: StructType =>
          recordAsRow(dbObjectToMap(value.asInstanceOf[DBObject]), struct)
        case _ =>
          //Assure value is mapped to schema constrained type.
          enforceCorrectType(value, dataType)
      }
    }.orNull
  }

  def dbObjectToMap(dBObject: DBObject): Map[String, AnyRef] = {
    dBObject.seq.toMap
  }

}
