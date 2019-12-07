package com.gu.vidispineakka.streamcomponents

import akka.stream.{Attributes, FlowShape, Inlet, Materializer, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import com.gu.vidispineakka.vidispine.{VSCommunicator, VSLazyItem}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

/**
  * lifts the entire raw metadata document for the given item and "writes" it using the provided callback
  * @param writer A callback function that is invoked in a subthread. It should receive the complete XML MEtadataDocument from the server
  *               as a String, do something with it (possibly asynchronously) and return a Future.  The stage won't continue until the
  *               Future has resolved. If it resolves successfully then processing will continue, if it resolves with a Failure then the stage
  *               will be aborted.
  * @param comm Implicitly provided [[VSCommunicator]] instance
  * @param mat  Implicitly provided akka Materializer instance
  */
class VSItemGetFullMeta(writer: (VSLazyItem, String)=>Future[Unit])(implicit comm:VSCommunicator, mat:Materializer) extends GraphStage[FlowShape[VSLazyItem,VSLazyItem]] {
  private final val in:Inlet[VSLazyItem] = Inlet.create("VSItemGetFullMeta.in")
  private final val out:Outlet[VSLazyItem] = Outlet.create("VSItemGetFullMeta.out")

  override def shape: FlowShape[VSLazyItem, VSLazyItem] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger:org.slf4j.Logger = LoggerFactory.getLogger(getClass)

    private val successCb = createAsyncCallback[VSLazyItem](i=>push(out,i))
    private val failedCb = createAsyncCallback[Throwable](err=>failStage(err))

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        val writtenFut = elem.getFullMetadataDoc.flatMap({
          case Right(content)=>writer(elem, content)
          case Left(err)=>Future.failed(new RuntimeException(err.toString))
        })

        writtenFut.onComplete({
          case Success(_)=>successCb.invoke(elem)
          case Failure(err)=>failedCb.invoke(err)
        })
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })
  }
}
