package com.gu.vidispineakka.streamcomponents

import akka.stream.{Attributes, FlowShape, Inlet, Materializer, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import org.slf4j.LoggerFactory
import com.gu.vidispineakka.vidispine.{VSCommunicator, VSFile, VSLazyItem, VSShape}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class NotFoundError(message:String) extends Throwable {
  override def getMessage: String = message
}

class VSGetItem(fieldList:Seq[String], includeShapes:Boolean = false)(implicit comm:VSCommunicator, mat:Materializer, ec:ExecutionContext) extends GraphStage[FlowShape[VSFile, (VSFile, Option[VSLazyItem])]] {
  private final val in:Inlet[VSFile] = Inlet.create("VSGetItem.in")
  private final val out:Outlet[(VSFile, Option[VSLazyItem])] = Outlet.create("VSGetItem.out")
  private val logger = LoggerFactory.getLogger(getClass)

  override def shape = FlowShape.of(in, out)

  def loadVSShapeData(itemId:String) = {
    comm.request(VSCommunicator.OperationType.GET,
      s"/API/item/$itemId",
      None,
      Map("Accept"->"application/xml"),
      Map("content"->"shape")
    ).map({
      case Left(httpErr)=>
        logger.error(s"Could not get shape data for $itemId: ${httpErr.errorCode} ${httpErr.message}")
        throw new NotFoundError("Could not get shape data")
      case Right(xmlString)=>
        val parsedContent = scala.xml.XML.loadString(xmlString)
        val shapes = (parsedContent \ "shape").map(VSShape.fromXml)
        val shapesMap = shapes.map(entry=>entry.tag->entry).toMap
        if(shapesMap.isEmpty) None else Some(shapesMap)
    })
  }

  /**
    * create a new VSLazyItem. Included like this to make testing easier.
    * @param itemId
    * @return
    */
  def makeItem(itemId:String) = VSLazyItem(itemId)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger:org.slf4j.Logger = LoggerFactory.getLogger(getClass)
    val completedCb = createAsyncCallback[(VSFile, Option[VSLazyItem])](itm=>push(out,(itm._1,itm._2)))
    val failedCb = createAsyncCallback[Throwable](err=>failStage(err))

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)
        logger.debug(s"Got incoming item $elem")

        elem.membership match {
          case Some(fileItemMembership)=>
            val item = makeItem(fileItemMembership.itemId)

            val itemWithShapesFut = if(includeShapes) {
              loadVSShapeData(fileItemMembership.itemId).map(shapeinfo=>item.copy(shapes = shapeinfo))
            } else {
              Future(item)
            }

            itemWithShapesFut
              .flatMap(_.getMoreMetadata(fieldList)
                .map({
                  case Left(metadataError)=>
                    logger.error(s"Could not get metadata for item ${fileItemMembership.itemId}: $metadataError")
                    metadataError.httpError match {
                      case None=>
                        failedCb.invoke(new RuntimeException("Could not lookup metadata"))
                      case Some(httpError)=>
                        if(httpError.errorCode==404){
                          completedCb.invoke((elem, None))
                        } else {
                          failedCb.invoke(new RuntimeException("Could not lookup metadata"))
                        }
                    }
                  case Right(updatedItem)=>
                    logger.debug(s"Looked up metadata for item ${updatedItem.itemId}")
                    completedCb.invoke((elem, Some(updatedItem)))
                })
              ).recover({
              case _:NotFoundError=>
                logger.error(s"No item found for ${fileItemMembership.itemId}")
                completedCb.invoke((elem, None))
              case err:Throwable=>
                logger.error(s"Get metadata crashed for item ${fileItemMembership.itemId}")
                failedCb.invoke(err)
            })
          case None=>
            logger.warn(s"Can't look up item metadata for file ${elem.vsid} as it is not a member of any item")
            completedCb.invoke((elem, None))
        }
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })
  }
}
