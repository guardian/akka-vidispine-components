package com.gu.vidispineakka.streamcomponents

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.gu.vidispineakka.vidispine.VSCommunicator.OperationType
import com.gu.vidispineakka.vidispine.{VSCommunicator, VSFile}
import org.slf4j.LoggerFactory
import scala.concurrent.{ExecutionContext, Future}

object VSFileContentSource {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
    * obtain a streaming source for the content of the given file, if possible
    * @param file [[VSFile]] instance describing the file that should be lifted
    * @param comm implicitly provided [[VSCommunicator]] instance describing the VS instance to communicate with
    * @param ec implicitly provided ExecutionContext for async operations
    * @return a Future, with either the streaming source or a string containing an error description
    */
  def sourceFor(file:VSFile)(implicit comm:VSCommunicator, ec:ExecutionContext) = {
    def tryForUrl(url:String):Future[Either[String,Source[ByteString,Any]]] =
      comm.sendGeneric(OperationType.GET,url, None, headers = Map("Accept" -> "application/octet-stream"), queryParams = Map())
        .flatMap(response => response.code match {
          case 200 => Future(response.body)
          case 303 => //VS spec shows that this should be the only redirect returned for this call
            logger.debug(s"sourceFor got redirect, location header is ${response.header("Location")}")
            response.header("Location") match {
              case Some(newloc) =>
                logger.debug(s"recursing to $newloc")
                tryForUrl(newloc)
              case None => Future(Left("Got a redirect without a location present, don't know where to go"))
            }
          case _=>
            logger.error(s"Got an error ${response.code} on url $url with content type ${response.contentType} and content length ${response.contentLength}")
            val descString = response.body match {
              case Left(s)=>s
              case Right(_)=>"Error data came through in Source, this is not expected"
            }
            Future(Left(s"Vidispine error ${response.code}: $descString"))
        })

    tryForUrl(s"/API/storage/file/${file.vsid}/data")
  }
}