package com.gu.vidispineakka.streamcomponents

import java.time.ZonedDateTime

import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import com.gu.vidispineakka.models.HttpError
import com.gu.vidispineakka.vidispine.{GetMetadataError, VSCommunicator, VSFile, VSFileItemMembership, VSLazyItem}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import testhelpers.AkkaTestkitSpecs2Support

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try

class VSGetItemSpec extends Specification with Mockito {
  "VSGetItem" should {
    "initialise a VSLazyItem from the given VSFile and populate with the given fields" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val fieldList = Seq("field1","field2","field3")
      implicit val comm:VSCommunicator = mock[VSCommunicator]

      val incomingFile = VSFile("VX-123","/path/to/file","file://path/to/file",None,1234L,None,ZonedDateTime.now(),1,"VX-1",
        None,Some(VSFileItemMembership("VX-456",Seq())),None,None)

      val mockedItem = mock[VSLazyItem]
      val lookedUpItem = mock[VSLazyItem]
      mockedItem.getMoreMetadata(any)(any,any) returns Future(Right(lookedUpItem))

      val mockedMakeItem = mock[String=>VSLazyItem]
      mockedMakeItem.apply(any) returns mockedItem

      val toTestFact = new VSGetItem(fieldList)(comm,mat,system.dispatcher) {
        override def makeItem(itemId: String): VSLazyItem = mockedMakeItem(itemId)
      }

      val sinkFact = Sink.seq[Option[VSLazyItem]]

      val graph = GraphDSL.create(sinkFact) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        //val src = Source.single(incomingFile)
        val src = builder.add(Source.fromIterator(()=>Seq(incomingFile,incomingFile).toIterator))
        val vsGetItem = builder.add(toTestFact)

        src ~> vsGetItem
        vsGetItem.out.map(_._2) ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run(), 4 second)

      result.head must beSome(lookedUpItem)
      there were two(mockedMakeItem).apply("VX-456")
      there were two(mockedItem).getMoreMetadata(fieldList)
    }

    "pass through None if the given file is not a member of an item" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val fieldList = Seq("field1","field2","field3")
      implicit val comm:VSCommunicator = mock[VSCommunicator]

      val incomingFile = VSFile("VX-123","/path/to/file","file://path/to/file",None,1234L,None,ZonedDateTime.now(),1,"VX-1",
        None,None,None,None)

      val mockedItem = mock[VSLazyItem]
      val lookedUpItem = mock[VSLazyItem]
      mockedItem.getMoreMetadata(any)(any,any) returns Future(Right(lookedUpItem))

      val mockedMakeItem = mock[String=>VSLazyItem]
      mockedMakeItem.apply(any) returns mockedItem

      val toTestFact = new VSGetItem(fieldList)(comm,mat,system.dispatcher) {
        override def makeItem(itemId: String): VSLazyItem = mockedMakeItem(itemId)
      }

      val sinkFact = Sink.seq[Option[VSLazyItem]]

      val graph = GraphDSL.create(sinkFact) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        //val src = Source.single(incomingFile)
        val src = builder.add(Source.fromIterator(()=>Seq(incomingFile,incomingFile).toIterator))
        val vsGetItem = builder.add(toTestFact)

        src ~> vsGetItem
        vsGetItem.out.map(_._2) ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run(), 4 second)
      result.head must beNone
      there were no(mockedMakeItem).apply(any)
      there were no(mockedItem).getMoreMetadata(any)(any,any)
    }

    "error if lookup fails" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)

      val fieldList = Seq("field1","field2","field3")
      implicit val comm:VSCommunicator = mock[VSCommunicator]

      val incomingFile = VSFile("VX-123","/path/to/file","file://path/to/file",None,1234L,None,ZonedDateTime.now(),1,"VX-1",
        None,Some(VSFileItemMembership("VX-456",Seq())),None,None)

      val mockedItem = mock[VSLazyItem]
      val lookedUpItem = mock[VSLazyItem]
      mockedItem.getMoreMetadata(any)(any,any) returns Future(Left(GetMetadataError(None,None,None)))

      val mockedMakeItem = mock[String=>VSLazyItem]
      mockedMakeItem.apply(any) returns mockedItem

      val toTestFact = new VSGetItem(fieldList)(comm,mat,system.dispatcher) {
        override def makeItem(itemId: String): VSLazyItem = mockedMakeItem(itemId)
      }

      val sinkFact = Sink.seq[Option[VSLazyItem]]

      val graph = GraphDSL.create(sinkFact) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        //val src = Source.single(incomingFile)
        val src = builder.add(Source.fromIterator(()=>Seq(incomingFile,incomingFile).toIterator))
        val vsGetItem = builder.add(toTestFact)

        src ~> vsGetItem
        vsGetItem.out.map(_._2) ~> sink
        ClosedShape
      }

      val result = Try { Await.result(RunnableGraph.fromGraph(graph).run(), 4 second) }
      result must beFailedTry
    }

    "if the item membership points to a non-existing item, return success with a None item" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = ActorMaterializer.create(system)
      implicit val comm:VSCommunicator = mock[VSCommunicator]

      val incomingFile = VSFile("VX-123","/path/to/file","file://path/to/file",None,1234L,None,ZonedDateTime.now(),1,"VX-1",
        None,Some(VSFileItemMembership("VX-456",Seq())),None,None)

      comm.request(any,any,any,any,any,any)(any,any) returns Future(Left(HttpError("not found", 404)))

      val mockedItem = mock[VSLazyItem]
      val lookedUpItem = mock[VSLazyItem]
      mockedItem.getMoreMetadata(any)(any,any) returns Future(Right(lookedUpItem))

      val mockedMakeItem = mock[String=>VSLazyItem]
      mockedMakeItem.apply(any) returns mockedItem

      val sinkFact = Sink.seq[(VSFile, Option[VSLazyItem])]

      val graph = GraphDSL.create(sinkFact) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.fromIterator(()=>Seq(incomingFile,incomingFile).toIterator))
        val vsGetItem = builder.add(new VSGetItem(Seq(), includeShapes = true) {
          override def makeItem(itemId: String): VSLazyItem = mockedMakeItem(itemId)
        })

        src ~> vsGetItem ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run(), 4 seconds)
      result.headOption must beSome((incomingFile, None))
    }
  }

  "if the item membership points to a non-existing item, return success with a None item" in new AkkaTestkitSpecs2Support {
    implicit val mat:Materializer = ActorMaterializer.create(system)
    implicit val comm:VSCommunicator = mock[VSCommunicator]

    val incomingFile = VSFile("VX-123","/path/to/file","file://path/to/file",None,1234L,None,ZonedDateTime.now(),1,"VX-1",
      None,Some(VSFileItemMembership("VX-456",Seq())),None,None)

    comm.request(any,any,any,any,any,any)(any,any) returns Future(Left(HttpError("not found", 404)))


    val sinkFact = Sink.seq[(VSFile, Option[VSLazyItem])]

    val graph = GraphDSL.create(sinkFact) { implicit builder=> sink=>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val src = builder.add(Source.fromIterator(()=>Seq(incomingFile,incomingFile).toIterator))
      val vsGetItem = builder.add(new VSGetItem(Seq("field_1")))

      src ~> vsGetItem ~> sink
      ClosedShape
    }

    val result = Await.result(RunnableGraph.fromGraph(graph).run(), 4 seconds)
    result.headOption must beSome((incomingFile, None))
  }
}
