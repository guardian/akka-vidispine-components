package com.gu.vidispineakka.streamcomponents

import akka.actor.ActorSystem
import akka.stream.{Attributes, Materializer, Outlet, SourceShape}
import akka.stream.stage.{AbstractOutHandler, AsyncCallback, GraphStage, GraphStageLogic}
import org.slf4j.LoggerFactory
import com.gu.vidispineakka.vidispine.{VSCommunicator, VSFile}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import scala.xml.{Elem, XML}
import scala.concurrent.duration._

/*
content – Comma-separated list of the types of content to retrieve, possible values are metadata, uri, shape, poster, thumbnail, access, merged-access, external.
 */
/**
  *
  * @param searchDoc
  * @param includeShape
  * @param pageSize
  * @param comm
  * @param actorSystem
  * @param mat
  */
abstract class VSGenericSearchSource[T](metadataFields:Seq[String], searchDoc:String, includeShape:Boolean, includeAllMetadata:Boolean=false, pageSize:Int=100, retryDelay:FiniteDuration=30.seconds, searchType:String="item")
                           (implicit val comm:VSCommunicator, actorSystem: ActorSystem, mat:Materializer)
  extends GraphStage[SourceShape[T]] {

  private val out:Outlet[T] = Outlet.create("VSItemSearchSource.out")
  override def shape: SourceShape[T] = SourceShape.of(out)

  /**
    * this must be implemented by a subclass, it translates the XML data into a domain object
    * @param content
    * @return
    */
  def processParsedXML(content:Elem):Seq[T]

  def getNextPage(startAt:Int) = {
    val uri = s"/API/$searchType;first=$startAt;number=$pageSize"

    val contentParam = Seq(
      if(includeShape) Some("shape") else None,
      if(metadataFields.nonEmpty || includeAllMetadata) Some("metadata") else None,
    ).collect({case Some(elem)=>elem}).mkString(",")

    val fieldsParam = if(metadataFields.nonEmpty) Some(metadataFields.mkString(",")) else None
    val queryParams = Seq("content"->Some(contentParam),"field"->fieldsParam)
      .collect({case (key, Some(value))=>(key->value)})
      .toMap

    comm.request(VSCommunicator.OperationType.PUT, uri,
      Some(searchDoc),
      Map("Accept"->"application/xml"),
      queryParams
    ).map(_.map(xmlString=>{
      processParsedXML(XML.loadString(xmlString))
    }))
  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    private var queue:Seq[T] = Seq()
    private var currentItem = 1 //VS starts enumerating from 1 not 0

    val failedCb = createAsyncCallback[Throwable](err=>failStage(err))
    val newItemCb = createAsyncCallback[T](item=>push(out, item))
    val completedCb = createAsyncCallback[Unit](_=>complete(out))

    /**
      * implicit conversion from a lambda function to a Runnable (see https://stackoverflow.com/questions/3073677/implicit-conversion-to-runnable)
      * this is used for actorSystem.scheduler.scheduleOnce, below
      * @param fun lambda function to execute
      * @return the Runnable object
      */
    implicit def funToRunnable(fun: () => Unit) = new Runnable() { def run() = fun() }

    /**
      * either services the request from the queue or calls out to VS for more data, refills the queue and processes that.
      * re-runs after a 30s delay (trigged through ActorSystem scheduler) if an error is returned from VS.
      * @param failedCb
      * @param newItemCb
      * @param completedCb
      */
    def processPull(failedCb:AsyncCallback[Throwable], newItemCb:AsyncCallback[T], completedCb:AsyncCallback[Unit]):Unit = {
      if (queue.nonEmpty) {
        logger.debug(s"Serving next item from queue")
        push(out, queue.head)
        queue = queue.tail
      } else {
        getNextPage(currentItem).onComplete({
          case Failure(err) =>
            logger.error(s"getNextPage crashed", err)
            failedCb.invoke(err)
          case Success(Left(httpError)) =>
            logger.error(s"VS returned an http error ${httpError.errorCode}: ${httpError.message}, retrying in 30 seconds")
            actorSystem.scheduler.scheduleOnce(retryDelay, () =>processPull(failedCb, newItemCb, completedCb))
          //failedCb.invoke(new RuntimeException(httpError.message))
          case Success(Right(moreItems)) =>
            logger.info(s"Got ${moreItems.length} more items from server, processed $currentItem items")
            queue ++= moreItems
            currentItem += moreItems.length
            if (queue.isEmpty) {
              completedCb.invoke(())
            } else {
              newItemCb.invoke(queue.head)
              queue = queue.tail
            }
        })
      }
    }

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = {
        processPull(failedCb, newItemCb, completedCb)
      }
    })
  }

}
