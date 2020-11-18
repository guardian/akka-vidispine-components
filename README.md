#akka-vidispine-components

## What is it?

This library brings together a few Akka Stream components that I have been using across a couple of projects to interface
with the Vidispine (https://www.vidispine.com) asset management system.

The aim is not to provide a full-featured interface but rather to keep re-usable code in one place.  If you find it useful,
please do contribute!

## Setup

The components use the provided class `VSCommunicator` to manage communication with the app server, and normally expect an 
instance to be provided as an implicit argument.  For example:

```
import com.gu.vidispineakka.vidispine.VSCommunicator

.
.
.


implicit comm:VSCommunicator = new VSCommunicator("https://vidispine-uri","username","password")
```

## What is provided?

### Sources

- VSItemSearchSource

Performs an item search, paginates/caches internally and yields the results as a stream of VSLazyItem objects.
For example:

```scala
import com.gu.vidispineakka.streamcomponents.VSGenericSearchSource
import com.gu.vidispineakka.vidispine.VSCommunicator
import akka.stream.scaladsl.GraphDSL._
import akka.stream.scaladsl.GraphDSL
import scala.xml._

val interestingMetadataFields = Seq("title","originalFormat","duration")  //etc. etc.; any valid Vidispine field names here, or empty.
val xmlSearchDoc = <ItemSearchDocument xmlns="http://xml.vidispine.com/schema/vidispine">.....</ItemSearchDocument>

GraphDSL.create() { implicit builder=>
  val src = builder.add(new VSGenericSearchSource(interestingMetadataFields, xmlSearchDoc.toString, includeShape=true))
  SourceShape(src.out)
}
```

- VSStorageScanSource

Performs a file search optionally limited to a given storage or a given file status and yields the results as a stream of VSFile objects.
For example:

```scala
import com.gu.vidispineakka.streamcomponents.VSStorageScanSource
import com.gu.vidispineakka.vidispine.VSCommunicator
import akka.stream.scaladsl.GraphDSL._
import akka.stream.scaladsl.GraphDSL
import scala.xml._


GraphDSL.create() { implicit builder=>
  val src = builder.add(new VSStorageScanSource(Some("storage-id-here"),Some("file-status-here")))
  SourceShape(src.out)
}
```

### Flows

- VSGetItem

Receives a VSFile objects (that must contain shape data) and looks up the associated item as a VSLazyItem object.
The output is a tuple of (VSFile, Option(VSLazyItem)) where `VSFile` is the VSFile object that was input and the 
VSLazyItem is set if an item was found. If there is no membership then None is returned.

- VSFileIdInList

Fanout shape that pushes an incoming VSFile to a "YES" output if the ID of the incoming file is in a string list provided
at construction or to a "NO" output if it isn't.

- VSDeleteFile

Receives a VSFile object and tells Vidispine to delete the File with the corresponding ID (not the item).  It then passes
on the incoming VSFile object for further processing.
It can also be constituted as a Sink by using `VSDeleteFile.asSink(reallyDelete=true)`
