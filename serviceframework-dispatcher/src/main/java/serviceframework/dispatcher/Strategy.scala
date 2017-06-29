package serviceframework.dispatcher

import java.util.{Map => JMap,List=>JList}
import java.util

/**
 * 4/10/14 WilliamZhu(allwefantasy@gmail.com)
 */
trait Strategy[T] extends ServiceInj{
  def processor:JList[Processor[T]]
  def ref:JList[Strategy[T]]
  def compositor:JList[Compositor[T]]
  def name:String
  def initialize(name:String,alg:JList[Processor[T]],ref:JList[Strategy[T]],com:JList[Compositor[T]],params:JMap[Any,Any])
  def result(params:JMap[Any,Any]):JList[T]
  def configParams:util.Map[Any, Any]
  def stop = {}
}
