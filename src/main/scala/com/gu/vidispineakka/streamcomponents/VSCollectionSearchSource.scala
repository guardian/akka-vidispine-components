package com.gu.vidispineakka.streamcomponents

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.gu.vidispineakka.vidispine.{VSCommunicator, VSLazyItem, VSMetadataEntry, VSMetadataValue}

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration
import scala.xml.Elem

class VSCollectionSearchSource(metadataFields:Seq[String],
                               searchDoc:String,
                               includeShape:Boolean,
                               pageSize:Int=100,
                               retryDelay:FiniteDuration=30.seconds)
                              (override implicit val comm:VSCommunicator, actorSystem: ActorSystem, mat:Materializer)
  extends VSGenericSearchSource[VSLazyItem](metadataFields, searchDoc, includeShape, pageSize, retryDelay, searchType = "collection")(comm, actorSystem, mat) {

  //yeah, so this shouldn't really be a VSLazyItem but I have not got time to go through _all_ the code and make the
  //base search source fully generic
  override def processParsedXML(content: Elem): Seq[VSLazyItem] = (content \ "collection").map(node=>{
    val itemId = (node \ "id").text
    val collectionName = (node \ "name").text

    val metadataMap = Map(
      "name"->VSMetadataEntry("name",None,None,None,None,Seq(VSMetadataValue(collectionName, None,None,None,None)))
    )

    VSLazyItem(itemId, metadataMap)
  })
}
