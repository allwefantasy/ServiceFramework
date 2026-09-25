package serviceframework.dispatcher

import java.util.{Map => JMap,List=>JList}
import net.csdn.ServiceFramwork

/**
 * 4/10/14 WilliamZhu(allwefantasy@gmail.com)
 */
trait Processor[T] extends ServiceInj{
  def initialize(name:String,params:JList[JMap[Any,Any]])
  def result(params:JMap[Any,Any]):JList[T]
  def name():String

  /** 只释放 processor 自己的资源。关闭时机由 StrategyDispatcher 决定。 */
  def stop = {}
}
