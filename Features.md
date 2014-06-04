# 版本特性

1.1.2 特性:

* 每个模块(controller,service等)允许配置多个包
* 通过启动时添加，可以保证某些注释了singleton的Service可以随系统启动得到初始化
* 添加了API实时计数模块
* 修正controller过滤器定义了但是没有实现导致的异常
* 修正当某个类标记了@Service类时，特定条件下无法得到加载
* controller 单元测试时，允许同时传递url参数以及post请求体
