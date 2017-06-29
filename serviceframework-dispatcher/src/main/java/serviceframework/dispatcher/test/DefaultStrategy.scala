package serviceframework.dispatcher.test

import serviceframework.dispatcher.{Compositor, Processor, Strategy}
import java.util
import net.csdn.common.logging.Loggers
import scala.collection.JavaConversions._

/**
 * 5/22/14 WilliamZhu(allwefantasy@gmail.com)
 */
class DefaultStrategy[T >: Boolean] extends Strategy[T]{
  var _name: String = _
  var _ref: util.List[Strategy[T]] = _
  var _compositor: util.List[Compositor[T]] = _
  var _processor: util.List[Processor[T]] = _
  var _configParams: util.Map[Any, Any] = _

  val logger = Loggers.getLogger(classOf[DefaultStrategy[T]])

  def processor: util.List[Processor[T]] = _processor

  def ref: util.List[Strategy[T]] = _ref

  def compositor: util.List[Compositor[T]] = _compositor

  def name: String = _name

  def initialize(name: String, alg: util.List[Processor[T]], ref: util.List[Strategy[T]], com: util.List[Compositor[T]], params: util.Map[Any, Any]): Unit = {
    this._name = name
    this._ref = ref
    this._compositor = com
    this._processor = alg
    this._configParams = params
  }
  def result(params: util.Map[Any, Any]): util.List[T] = {
     processor.get(0).result(params)
     List()
  }

  def configParams: util.Map[Any, Any] = _configParams
}
