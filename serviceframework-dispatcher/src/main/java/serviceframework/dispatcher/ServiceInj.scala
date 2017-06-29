package serviceframework.dispatcher

import net.csdn.ServiceFramwork


/**
 * 4/10/14 WilliamZhu(allwefantasy@gmail.com)
 */
trait ServiceInj {
  def findService[T](clzz:Class[T]):T = {
    ServiceFramwork.injector.getInstance(clzz)
  }

}
