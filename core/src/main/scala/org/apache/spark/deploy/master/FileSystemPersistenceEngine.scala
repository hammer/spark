/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.deploy.master

import java.io._
import java.nio.ByteBuffer

import org.apache.spark.Logging
import org.apache.spark.serializer.Serializer

import scala.reflect.ClassTag

/**
 * Stores data in a single on-disk directory with one file per application and worker.
 * Files are deleted when applications and workers are removed.
 *
 * @param dir Directory to store files. Created if non-existent (but not recursively).
 * @param serialization Used to serialize our objects.
 */
private[spark] class FileSystemPersistenceEngine(
    val dir: String,
    val serialization: Serializer)
  extends PersistenceEngine with Logging {

  val serializer = serialization.newInstance()
  new File(dir).mkdir()

  override def persist(name: String, obj: Object): Unit = {
    serializeIntoFile(new File(dir + File.separator + name), obj)
  }

  override def unpersist(name: String): Unit = {
    new File(dir + File.separator + name).delete()
  }

  override def read[T: ClassTag](prefix: String) = {
    val files = new File(dir).listFiles().filter(_.getName.startsWith(prefix))
    files.map(deserializeFromFile[T])
  }

  private def serializeIntoFile(file: File, value: AnyRef) {
    val created = file.createNewFile()
    if (!created) { throw new IllegalStateException("Could not create file: " + file) }

    val out = serializer.serializeStream(new FileOutputStream(file))   
    try {
      out.writeObject(value)
    } finally {
      out.close()
    }

  }

  def deserializeFromFile[T](file: File): T = {
    val fileData = new Array[Byte](file.length().asInstanceOf[Int])
    val dis = new DataInputStream(new FileInputStream(file))
    try {
      dis.readFully(fileData)
    } finally {
      dis.close()
    }

    serializer.deserializeStream(dis).readObject()
  }
}
