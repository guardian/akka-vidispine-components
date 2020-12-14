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

/**
  * Base class for implementing Vidispine searches. VSItemSearchSource and VSCollectionSearchSource both derive from this;
  * if you want to perform custom processing on the data that comes back from the server, then extend this class and
  * implement the `processParsedXML` method.
  * This uses VSCommunicator internally, which in turn uses Akka HTTP which requires an ActorSystem and Materializer to
  * be in implicit scope.
  * @param metadataFields include these metadata fields.  The search is performed with the ?field= parameter and ?content=metadata
  * @param searchDoc string representing an XML ItemSearchDocument to send to the server
  * @param includeShape include shape adata for items. The search is performed with the ?content=shape parameter. This works
  *                     in conjunection with metadata
  * @param includeAllMetadata include ALL metadata, not just ones from metadataFields. The search is performed with ?content=metadata
  *                           and no ?field= parameter
  * @param customStartPoint start from this record number. Defaults to 1 (the first record). Must be a positive integer that
  *                         is 1 or greater. Passed to the server as the `from` parameter
  * @param pageSize retrieve this many records in a page. Passed to the server as the `number` parameter. Defaults to 100
  * @param retryDelay on error, wait this long before delay. Defaults to 30 seconds.
  * @param searchType indicates the endpoint to search, either 'item' or 'collection'. Defaults to 'item'
  * @param comm implicitly provided VSCommunicator
  * @param actorSystem implicitly provided ActorSystem
  * @param mat implicitly provided Materializer
  * @tparam T the type of the domain object that is expected to be returned from processParsedXML.
  */
abstract class VSGenericSearchSource[T](metadataFields:Seq[String], searchDoc:String, includeShape:Boolean,
                                        includeAllMetadata:Boolean=false, customStartPoint:Int=1, pageSize:Int=100,
                                        retryDelay:FiniteDuration=30.seconds, searchType:String="item")
                           (implicit val comm:VSCommunicator, actorSystem: ActorSystem, mat:Materializer)
  extends GraphStage[SourceShape[T]] {

  private val out:Outlet[T] = Outlet.create("VSItemSearchSource.out")
  override def shape: SourceShape[T] = SourceShape.of(out)

  /**
    * this must be implemented by a subclass, it translates the XML data into a domain object
    * @param content Scala XML element representing the root of the returned XML from the server
    * @return a sequence of one or more domain objects of type T
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
    private var currentItem = customStartPoint //VS starts enumerating from 1 not 0

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
