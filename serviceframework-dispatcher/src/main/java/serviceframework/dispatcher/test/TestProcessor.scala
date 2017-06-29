package serviceframework.dispatcher.test

import serviceframework.dispatcher.Processor
import java.util

/**
 * 5/29/14 WilliamZhu(allwefantasy@gmail.com)
 */
class TestProcessor[T]  extends Processor[T]{
  private var _name: String = _
  private var _configParams: util.List[util.Map[Any, Any]] = _


  def name(): String = _name

  def initialize(name: String, params: util.List[util.Map[Any, Any]]): Unit = {
    this._name = name
    this._configParams = params
  }

  def result(params: util.Map[Any, Any]): util.List[T] = {
    println("我是天才")
    new util.ArrayList[T]()
  }
}
