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

  def parseRemoteContext(params: java.util.Map[String, Array[String]]) = {
    val traceId = params.get(RemoteTraceElementKey.TRACEID)(0)
    val rpcId = params.get(RemoteTraceElementKey.RPCID)(0) + ".0"
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

  def configRemoteTraceElement(rte: RemoteTraceElement) = {
    remoteTraceElement = rte
  }

  def newRemoteTraceElement(door: Boolean, rpcId: String, url: String, visitType: VisitType) = {
    RemoteTraceElement(TraceId(traceId, door, 0), rpcId, url, System.nanoTime(), 0, 0, 0, "", visitType)
  }

  def start(url: String, visitType: VisitType) = {
    remoteTraceElement = newRemoteTraceElement(false, rpcId, url, visitType)
  }

  def openDoor(rpcId: String, url: String, visitType: VisitType) = {
    remoteTraceElement = newRemoteTraceElement(true, rpcId, url, visitType)
  }

  def finish(_resultCode: Int, responseLength: Long, _message: String, logger: CSLogger) = {
    val _startTime = remoteTraceElement.startTime
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
    logger.info("___trace___\t" + s"${rte.traceId.id}\t${rte.traceId.door}\t${rte.visitType.toString}\t${rte.rpcId}\t${rte.url}\t${rte.startTime}\t${rte.timeConsume}\t${rte.resultCode}\t${rte.length}\t${rte.message}")
  }

  var rpcId = "0.0"

  def currentRpcId = rpcId

  def nextRpcId = {
    synchronized {
      val phase = rpcId.split("\\.")
      rpcId = rpcId.take(phase.length - 1) + "." + (phase.last.toInt + 1)
      rpcId
    }
  }
}
