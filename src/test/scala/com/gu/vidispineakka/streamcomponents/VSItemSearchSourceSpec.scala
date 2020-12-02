package com.gu.vidispineakka.streamcomponents

import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink}
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import com.gu.vidispineakka.models.HttpError
import com.gu.vidispineakka.vidispine.{VSCommunicator, VSLazyItem}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import testhelpers.AkkaTestkitSpecs2Support

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.Source

class VSItemSearchSourceSpec extends Specification with Mockito{
  def getTestSearch(filename:String) = {
    val s=Source.fromResource(filename)
    val content = s.mkString
    s.close()
    content
  }

  "VSItemSearchSource" should {
    "load and parse an XML document from the server" in  new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      implicit val mockedComm = mock[VSCommunicator]
      mockedComm.request(any, any,any,any,any,any)(any,any) returns Future(Right(getTestSearch("testsearch.xml"))) thenReturns Future(Right(getTestSearch("emptysearch.xml")))

      val sinkFactory = Sink.fold[Seq[VSLazyItem],VSLazyItem](Seq())((acc,entry)=>acc++Seq(entry))

      val graph = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(new VSItemSearchSource(Seq("field1","field2"),"mock-search-doc",true, includeAllMetadata = false))
        src ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run(), 30 seconds)
      println(s"Got ${result.length} items:")
      result.foreach(item=>println(s"\t$item"))
      result.length mustEqual 18

      there were two(mockedComm).request(VSCommunicator.OperationType.PUT, "/API/item;first=1;number=100",Some("mock-search-doc"),Map("Accept"->"application/xml"),Map("content"->"metadata","field"->"field1,field2"))

      result.head.getSingle("gnm_external_archive_external_archive_status") must beSome("Archived")
      //there was one(mockedComm).request("/API/item;first=101;number=100","mock-search-doc",Map("Accept"->"application/xml"),Map("content"->"metadata","field"->"field1,field2"))
    }

    "retry on error" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      implicit val mockedComm = mock[VSCommunicator]
      mockedComm.request(any,any,any,any,any,any)(any,any) returns Future(Left(HttpError("Vidispine is playing up",503))) thenReturns Future(Right(getTestSearch("testsearch.xml"))) thenReturns Future(Right(getTestSearch("emptysearch.xml")))

      val sinkFactory = Sink.fold[Seq[VSLazyItem],VSLazyItem](Seq())((acc,entry)=>acc++Seq(entry))

      val graph = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(new VSItemSearchSource(Seq("field1","field2"),"mock-search-doc",true, includeAllMetadata = false,retryDelay = 1.second))
        src ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run(), 30 seconds)
      println(s"Got ${result.length} items:")
      result.foreach(item=>println(s"\t$item"))
      result.length mustEqual 18

      there were three(mockedComm).request(VSCommunicator.OperationType.PUT, "/API/item;first=1;number=100",Some("mock-search-doc"),Map("Accept"->"application/xml"),Map("content"->"metadata","field"->"field1,field2"))

      result.head.getSingle("gnm_external_archive_external_archive_status") must beSome("Archived")
    }
  }
}
