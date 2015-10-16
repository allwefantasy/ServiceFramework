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

  def createRemoteContextForNewThread(traceId: String, rpcId: String) = {
    val context = new TraceContext(traceId)
    context.rpcId = rpcId
    context
  }


}

class TraceContext(val traceId: String) {

  var remoteTraceElement: RemoteTraceElement = _


  def newRemoteTraceElement(door: Boolean, rpcId: String, visitType: VisitType) = {
    RemoteTraceElement(TraceId(traceId, door, 0), rpcId, System.nanoTime(), 0, 0, 0, "", visitType)
  }

  def start(visitType: VisitType) = {
    val rpcId = nextRpcId
    remoteTraceElement = newRemoteTraceElement(false, rpcId, visitType)
  }

  def finish(_resultCode: Int, responseLength: Long, _message: String, logger: CSLogger) = {
    val _startTime = remoteTraceElement.starTime
    val _oldMessage = remoteTraceElement.message
    val rte = remoteTraceElement.copy(
      traceId = TraceId(traceId, false, 0),
      timeConsume = (System.nanoTime() - _startTime),
      resultCode = _resultCode,
      message = if (_message != null) _message else _oldMessage,
      length = responseLength
    )
    remoteTraceElement = null
    log(logger, rte)
  }

  def log(logger: CSLogger, rte: RemoteTraceElement) = {
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
