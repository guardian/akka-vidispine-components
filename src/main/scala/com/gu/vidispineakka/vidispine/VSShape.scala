package com.gu.vidispineakka.vidispine

import akka.event.DiagnosticLoggingAdapter
import akka.stream.Materializer
import com.softwaremill.sttp.Uri
import com.gu.vidispineakka.models.HttpError
import org.slf4j.LoggerFactory
import VSCommunicator.OperationType

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.xml.{NodeSeq, XML}

case class VSShape (vsid:String,essenceVersion:Int,tag:String,mimeType:String, files:Seq[VSFile])

object VSShape {
  private val logger = LoggerFactory.getLogger(getClass)

  def vsIdForShapeTag(itemId:String, shapeTag:String)(implicit vsCommunicator:VSCommunicator,mat:Materializer) = {
    val uri = s"/API/item/$itemId/shape?tag=$shapeTag"
    vsCommunicator.request(OperationType.GET, uri, None, Map("Accept"->"application/xml"))
      .map({
        case Right(stringData)=>UriListDocument.fromXmlString(stringData) match {
          case Success(uriListDocument)=>
            Right(uriListDocument.uri.headOption)
          case Failure(err)=>
            Left(HttpError(err.toString, -1))
        }
        case Left(err)=>
          Left(err)
      })
  }

  def forItemWithId(itemId:String, shapeId:String)(implicit vsCommunicator:VSCommunicator,mat:Materializer) = {
    val uri = s"/API/item/$itemId/shape/$shapeId"
    logger.debug(s"VSShape.forItemWithId: URI is $uri")
    vsCommunicator.request(OperationType.GET, uri,None, headers=Map("Accept" -> "application/xml"))
      .map({
        case Right(stringData) => VSShape.fromXmlString(stringData) match {
          case Success(vsShape) =>
            logger.debug(s"VSShape.forItemWithID: Converted XML to domain object")
            Right(vsShape)
          case Failure(err) =>
            logger.warn(s"VSShape.forItemWithID: Could not interpret returned XML: ", err)
            Left(HttpError(err.toString, -1))
        }
        case Left(err) =>
          logger.warn(s"VSShape.forItemWithID: Got HTTP error $err")
          Left(err)
      })
  }

  def forItemWithTag(itemId:String, shapeTag:String)(implicit vsCommunicator:VSCommunicator,mat:Materializer) = {
    vsIdForShapeTag(itemId, shapeTag).flatMap({
      case Left(err) => Future(Left(err))
      case Right(None) => Future(Left(HttpError(s"No shape exists for tag $shapeTag", 404)))
      case Right(Some(shapeId)) => forItemWithId(itemId, shapeId)
    })
  }


  def fromXmlString(xmlString:String):Try[VSShape] = Try {
    val xmlNodes = XML.loadString(xmlString)
    fromXml(xmlNodes)
  }

  def fromXml(xmlNodes:NodeSeq):VSShape = {
    //see http://apidoc.vidispine.com/latest/ref/xml-schema.html?highlight=shapedocument#schema-element-ShapeDocument for a list of all the possible components
    val containerNodesList = (xmlNodes \ "containerComponent" \ "file") ++ (xmlNodes \ "binaryComponent" \ "file") ++ (xmlNodes \ "descriptorComponent" \ "file") ++ (xmlNodes \ "audioComponent" \ "file") ++ (xmlNodes \ "videoComponent" \ "file") ++ (xmlNodes \ "subtitleComponent" \ "file")
    new VSShape(
      (xmlNodes \ "id").text,
      (xmlNodes \ "essenceVersion").text.toInt,
      (xmlNodes \ "tag").text,
      (xmlNodes \ "mimeType").text,
      containerNodesList.map(fileNode => VSFile.fromXml(fileNode) match {
        case Success(Some(vsFile)) => vsFile
        case Success(None) =>
          logger.error(s"Recceived shape document with empty file nodes, this should not happen")
          logger.error(xmlNodes.toString)
          throw new RuntimeException("Recceived shape document with empty file nodes, this should not happen")
        case Failure(err) => throw err //this is picked up immediately by the containing Try and returned as a Failure
      })
    )
  }
}
