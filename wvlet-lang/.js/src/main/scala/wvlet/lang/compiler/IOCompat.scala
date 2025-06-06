package wvlet.lang.compiler

import java.net.URI
import java.io.File

trait IOCompat:
  self: SourceIO.type =>

  def readAsString(filePath: String): String  = ???
  def readAsString(uri: java.net.URI): String = ???
  def listResources(path: String): List[VirtualFile] =
    listFiles(path).map(f => URIResource(File(f).toURI)).toList

  def existsFile(path: String): Boolean     = false
  def lastUpdatedAt(path: String): Long     = ???
  def listFiles(path: String): List[String] = Nil
  def fileName(path: String): String        = path.split("/").lastOption.getOrElse(path)
  def fileExists(path: String): Boolean     = false
  def isDirectory(path: String): Boolean    = false
