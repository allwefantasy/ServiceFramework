package net.csdn.trace

import net.csdn.trace.VisitType.VisitType

/**
 * 10/14/15 WilliamZhu(allwefantasy@gmail.com)
 */

case class RemoteTraceElement(traceId: TraceId,
                              rpcId: String,
                              url:String,
                              startTime: Long,
                              timeConsume: Long,
                              resultCode: Int,
                              length: Long,
                              message: String, visitType: VisitType)

case class TraceId(id: String, door: Boolean, sign: Int)

object VisitType extends Enumeration {
  type VisitType = Value

  val DB = Value("db")
  val RPC_SERVICE = Value("rpc_service")
  val HTTP_SERVICE = Value("http_service")
  val CACHE = Value("cache")
}

object RemoteTraceElementKey {
  val TRACEID: String = "___traceId___"
  val RPCID: String = "___rpcId___"
}


