# 版本特性

1.1.2 特性:

* 各模块(controller,service等)允许配置多个包名
* 通过Singleton注解的类可通过使用ServiceFramework中registerStartWithSystemServices方法进行设置
* 添加了API实时计数模块，可统计QPS(自定义采样时长),各个接口请求累计次数
* 修正controller过滤器中，过滤器没有实现导致的异常。
* 修正当某个类标记了@Service类时，特定条件下无法得到加载
* controller 单元测试时，允许同时传递url参数以及post请求体
* 添加了服务降级限流功能.规定每个API最大的qps数，超过的会丢弃。
