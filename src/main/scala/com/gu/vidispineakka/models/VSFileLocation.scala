package com.gu.vidispineakka.models

import com.gu.vidispineakka.vidispine.VSFile

case class VSFileLocation (storageId:String, fileId:String, fileUri:String)

object VSFileLocation extends ((String,String,String)=>VSFileLocation) {
  def fromVsFile(vsFile:VSFile):VSFileLocation = {
    new VSFileLocation(
      vsFile.storage,
      vsFile.vsid,
      vsFile.uri
    )
  }
}