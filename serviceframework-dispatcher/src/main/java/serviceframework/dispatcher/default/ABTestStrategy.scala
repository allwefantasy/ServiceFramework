package serviceframework.dispatcher.default

import java.util

import net.csdn.common.logging.Loggers
import serviceframework.dispatcher.{Compositor, Processor, Strategy}


/**
 * 4/19/16 WilliamZhu(allwefantasy@gmail.com)
 */
class ABTestStrategy[T] extends Strategy[T] {
  var _name: String = _
  var _ref: util.List[Strategy[T]] = _
  var _compositor: util.List[Compositor[T]] = _
  var _processor: util.List[Processor[T]] = _
  var _configParams: util.Map[Any, Any] = _

  def processor: util.List[Processor[T]] = _processor

  def ref: util.List[Strategy[T]] = _ref

  def compositor: util.List[Compositor[T]] = _compositor

  def name: String = _name


  val logger = Loggers.getLogger(getClass)

  def cid = {
    if (_configParams.containsKey("cid")) _configParams.get("cid").asInstanceOf[String] else "cid"
  }


  override def initialize(name: String, alg: util.List[Processor[T]], ref: util.List[Strategy[T]], com: util.List[Compositor[T]], params: util.Map[Any, Any]): Unit = {
    this._name = name
    this._ref = ref
    this._compositor = com
    this._processor = processor
    this._configParams = params
  }

  override def configParams: util.Map[Any, Any] = {
    _configParams
  }


  def result(params: util.Map[Any, Any]): util.List[T] = {
    import scala.collection.JavaConversions._

    require(params.containsKey(cid), s"AB策略，${cid}参数是必须的")

    val cidValue = params.get(cid).asInstanceOf[String]

    val algOrStra = new util.ArrayList[Strategy[T]]()

    algOrStra.addAll(ref)


    require(algOrStra.size() == 2, s"算法和策略必须只有两个")

    val jack = algOrStra.filter(f => _configParams.containsKey(f.name))
    if (jack.size == 0) {
      if (Math.abs(cidValue.hashCode % 10) < 5) {
        algOrStra.get(0).result(params)
      } else {
        algOrStra.get(1).result(params)
      }
    }
    else {
      val as1 = jack.get(0)
      val as2 = if (algOrStra.indexOf(as1) == 0) algOrStra.get(1) else algOrStra.get(0)
      if (Math.abs(cidValue.hashCode % 10) < _configParams.get(as1.name).asInstanceOf[Double] * 10) {
        as1.result(params)
      } else {
        as2.result(params)
      }
    }

  }


}
