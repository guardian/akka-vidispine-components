package com.gu.vidispineakka.streamcomponents

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.gu.vidispineakka.vidispine.{VSCommunicator, VSLazyItem}

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration
import scala.xml.Elem

class VSItemSearchSource(metadataFields:Seq[String],
                         searchDoc:String,
                         includeShape:Boolean,
                         includeAllMetadata:Boolean,
                         customStartPoint:Int=1,
                         pageSize:Int=100,
                         retryDelay:FiniteDuration=30.seconds)
                        (override implicit val comm:VSCommunicator, actorSystem: ActorSystem, mat:Materializer)
  extends VSGenericSearchSource[VSLazyItem](metadataFields, searchDoc, includeShape, includeAllMetadata,
    customStartPoint = customStartPoint,
    pageSize = pageSize,
    retryDelay = retryDelay,
    searchType = "item")(comm, actorSystem, mat) {

  def processParsedXML(parsedData:Elem):Seq[VSLazyItem] = (parsedData \ "item").map(VSLazyItem.fromXmlSearchStanza)
}
