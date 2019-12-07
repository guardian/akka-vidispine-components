package com.gu.vidispineakka.streamcomponents

import com.gu.vidispineakka.vidispine.VSCommunicator.OperationType
import com.gu.vidispineakka.vidispine.{VSCommunicator, VSFile}


import scala.concurrent.ExecutionContext

object VSFileContentSource {
  def sourceFor(file:VSFile)(implicit comm:VSCommunicator, ec:ExecutionContext) =
    comm.sendGeneric(OperationType.GET, s"/API/storage/file/${file.vsid}/data",None,Map(),Map()).map(_.body)
}