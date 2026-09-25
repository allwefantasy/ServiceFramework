# Dispatcher 扩展加载和实例生命周期

本文描述 `serviceframework-dispatcher` 的加载和实例生命周期。`Strategy`、`Processor`、`Compositor` 的方法签名没有改。`ServiceInj.findService` 调用 `ServiceFramwork.currentInjector()`：当前线程有应用 scope 时用这个应用的 injector，没有 scope 时才回到默认应用。如果当前 scope 不属于应用，调用失败，不会改用 `ServiceFramwork.injector`。dispatcher 不保存自己的 injector，也不放进 Bootstrap。

Scala 2.13.16，字节码目标是 Java 8（`-release:8`）。下面的测试在 JDK 17 上编译，同一批 class 再分别用 JDK 8 和 JDK 17 跑。

## 配置

`loadConfig` / `reload` 接受一份完整 JSON 对象。`null` 才去读 `application.strategy.config.file`，缺省文件名是 `strategy.v2.json`。非 null 字符串一直按 JSON 文本解析，不是文件路径；`getOrCreate` 的参数名虽然叫 configFile，走的仍是这条旧约定。

顶层每个键是一个策略。启用时必须有字符串字段 `strategy`。`processor` 与旧名 `algorithm` 二选一，同时写会直接失败。`ref` 是策略名数组。`compositor` 沿用原来的 `name`、`typeFilter`、`params`。`configParams.topic` 如果出现，必须是数组。

`enable` / `enabled` 为 false，或 `disable` / `disabled` 为 true，表示这个策略未启用。未启用的条目不会调用 `ShortNameMapping.forName`，也不会 `Class.forName`。别的策略引用它，按「引用了未启用的策略」失败，而不是静默丢掉这条 ref。两边标志互相矛盾同样失败。

同一份文本里的重复顶层策略名会失败，不再让 JSON 解析器静默留下其中一个。同一个策略的 `ref` 里重复名字也会失败。

## 校验和初始化顺序

1. 解析 JSON，检查形状和引用：缺引用、引用未启用、自环和互环都在加载类之前失败。
2. 对启用策略做稳定拓扑排序。没有依赖的策略按配置里的出现顺序；被引用的策略先创建。
3. 再检查类。`Class.forName(name, false, loader)` 只加载、不执行 static initializer。loader 优先用当前线程的 context class loader；线程没有时，用加载 dispatcher 的框架 loader。接着确认它是目标接口、类本身 public、不是接口或抽象类、并且有 public 无参构造。类型不对、类不是 public、抽象实现，或没有可用构造时，不 `new`，也不调用 `initialize`，因此这些类的 static initializer 不会跑。检查通过后才构造，类初始化发生在构造时。
4. 按拓扑顺序创建。一个策略内部是 processor、compositor，最后才是策略本身。`ref` 复用已经创建的实例。
5. 全部 `initialize` 成功后，原子换成这张候选图，再准备关闭旧图。

失败时只反向关闭这次已经构造出来的实例，当前正在对外服务的图保持原样。异常是 `StrategyLoadException`，消息里有策略名、`app.processor[0]` 这种路径、配置来源（`inline` 或文件路径），`getCause()` 是原来的异常。关闭过程里再抛出的异常挂在 `addSuppressed` 上。

`createStrategy(name, desc)` 仍返回 `Option`，成功时是 `Some`。它只增量登记一个策略：重名抛「重复注册」，显式禁用抛「已禁用」且不加载类，`ref` 必须已经在当前图里。这和旧实现不同——旧实现在名字已存在时返回 `None`，调用方看不出来配置没有生效。`loadConfig` / `reload` 不再走那条分支。

## 谁负责 stop

`Strategy.stop`、`Processor.stop`、`Compositor.stop` 只释放自己额外持有的资源。默认的 `LinearStrategy` 不级联关闭 processor、compositor 或 ref。

这些扩展实例的所有者是 dispatcher。`close()` 幂等，按初始化的反序 stop，并用对象身份去重，所以共享 ref 只会 stop 一次。多个 stop 失败时，第一个抛出，其余进 suppressed。

`reload` 和再次 `loadConfig` 都是整图替换：新配置里的策略全部是新实例，旧图里的实例在不再被请求使用后 stop。只是 JSON 看起来没变，也会换新实例。从配置里消失或改成未启用，等同于从新图移除。

## 并发

`dispatch` 会钉住进入请求时的那张图。`reload` 可以先把新图换上并返回；旧图要等钉住它的请求都退出才 stop。因此一个还在 `result` 里的请求不会看到自己的策略被提前关闭，之后进来的请求看到的是新图。

`findStrategies` 只是当前已发布图的快照，不钉住生命周期。未知 `_client_`，以及 topic 模式下不存在的 topic，都返回 `None`；`dispatch` 得到空列表。以前非 topic 路径会把缺失策略编成 `Some(List(null))`，后面立刻 NPE。

`stop` / `result` 里不要再调用同一实例的 `loadConfig`、`reload` 或 `close`，否则会和替换图用的那把锁撞上。

## 默认实例

`StrategyDispatcher.getOrCreate` 仍然只负责进程里的那一个默认入口：已经创建过就直接返回，不会按新参数重载。`new StrategyDispatcher` 的两个实例互不影响，也不登记成默认实例。`clear()` 会 `close()` 默认实例；再调用 `getOrCreate` 才会新建。没有注册 JVM shutdown hook。

业务 `result` 的组合方式、`_cache_` / `_token_`，以及 `StrategyDispatcher.throwsException` 的含义都没改：链路里抛出的 `Exception` 先记日志，只有这个标志为 true 时才向外抛。

## 测试

`StrategyDispatcherSuite` 仍是原来的 6 个用例。生命周期用例在 `StrategyDispatcherLifecycleSuite`。其中 Java fixture 用 static initializer 记副作用：错误类型、包内可见类、抽象 Strategy / Processor / Compositor、缺少无参构造都不会被初始化；context class loader 里定义的合法扩展会被构造，同一 loader 上的错误类型仍然被拒绝且不初始化。

JDK 17 编译 dispatcher。同一批 class 再分别用 JDK 17 和 JDK 8 跑，JDK 8 那次不要 `clean`，也不要重新编译。全反应器的双 JDK 验收仍由 `dev/verify-jdk-compatibility.sh` 负责。Scala 2.11 / 2.12 profile 不在这次范围内。
