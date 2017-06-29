package serviceframework.dispatcher

import java.util.{Map=>JMap, List=>JList}


/**
 * 4/10/14 WilliamZhu(allwefantasy@gmail.com)
 */
trait Compositor[T] extends ServiceInj{
  def initialize(typeFilters:JList[String],configParams:JList[JMap[Any,Any]])
  def result(alg:JList[Processor[T]],ref:JList[Strategy[T]],middleResult:JList[T],params:JMap[Any,Any]):JList[T]
  def stop = {}
}
