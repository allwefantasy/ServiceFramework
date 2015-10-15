package net.csdn.trace

import java.util.UUID

import net.csdn.common.logging.CSLogger
import net.csdn.trace.VisitType.VisitType

/**
 * 10/14/15 WilliamZhu(allwefantasy@gmail.com)
 */
object TraceContext {

  def createRemoteContext = {
    val traceId = UUID.randomUUID().getMostSignificantBits + ""
    val context = new TraceContext(traceId)
    context
  }

  def parseRemoteContext(params: java.util.Map[String, String]) = {
    val traceId = params.get(RemoteTraceElementKey.TRACEID)
    val rpcId = params.get(RemoteTraceElementKey.RPCID) + ".0"
    val context = new TraceContext(traceId)
    context.rpcId = rpcId
    context
  }


}

class TraceContext(traceId: String) {

  var remoteTraceElement: RemoteTraceElement = _

  def start(visitType: VisitType) = {
    val rpcId = nextRpcId
    remoteTraceElement = RemoteTraceElement(TraceId(traceId, true, 0), rpcId, System.nanoTime(), 0, 0, 0, "", visitType)
  }

  def finish(_resultCode: Int, responseLength: Long, _message: String, logger: CSLogger) = {
    val rte = remoteTraceElement.copy(
      traceId = TraceId(traceId, false, 0),
      timeConsume = (System.nanoTime() - remoteTraceElement.starTime),
      resultCode = _resultCode,
      message = if (_message != null) _message else remoteTraceElement.message,
      length = responseLength
    )
    remoteTraceElement = null
    logger.info("___trace___" + s"${rte.traceId.id}\t${rte.traceId.door}\t${rte.visitType.toString}\t${rte.rpcId}\t${rte.starTime}\t${rte.timeConsume}\t${rte.resultCode}\t${rte.starTime}\t${rte.length}\t${rte.message}")
  }

  var rpcId = "0.0"

  def currentRpcId = rpcId

  def nextRpcId = {
    val phase = rpcId.split("\\.")
    rpcId = rpcId.take(phase.length - 1) + "." + phase.last.toInt + 1
    rpcId
  }
}
