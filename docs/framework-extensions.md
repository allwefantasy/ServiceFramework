# 应用生命周期、扩展和 Controller 过滤器

本文描述 `serviceframework-web` 里的启动、扩展和过滤器行为。Jetty 仍是 **9.2.16**（`javax.servlet`），不是 9.4，也不是 Jetty 12 / Jakarta。类定义走 `ClassDefiner` 和 Javassist 3.33.0-GA，不打开 `--add-opens`。整仓矩阵见 [serviceframework-bytecode-migration.md](serviceframework-bytecode-migration.md)。

不设实库开关时，对应用例会跳过。那是可选的默认单元跑法，跳过不算数据库验收。2026-09-25 的全量实库矩阵同时打开三个开关，两个 JDK 都是 0 跳过：

| 开关 | 打开的用例 |
| --- | --- |
| `SF_ORM_MYSQL=true` | ORM 的 MySQL 业务测试 |
| `SF_COMPAT_MONGO=true` | Mongo 实库测试 |
| `SF_COMPAT_WEB_DB=true` | Web Bootstrap 的数据库回归。也可以用 `-Dsf.compat.web.db=true` |

三个都要由 `dev/compat-services.sh run` 注入 `SF_COMPAT_ENV_FILE`。开关开了但没有这份环境文件时测试失败，不会悄悄跳过。只开其中一两个，不能写成整仓实库矩阵。

## 一个应用一个上下文

`ApplicationContext` 拥有这次启动的 `EnhancementContext`、扫描器、Guice 模块、injector、过滤器目录和关闭动作。`ServiceFramwork` 上原来的 `injector`、`scanService`、`classPool`、`modules`、`AllModules` 只作为**默认应用**的别名：第二个 ClassLoader 启动时不会改写它们。`classPool` 这个静态字段也不再是增强用的池；增强用的是当前 `EnhancementContext` 自己的池。

请求线程在进入 HTTP 处理时 `activate()` 所属应用，结束时关掉 scope，外层 scope 会回来。scope 不跟随线程池。别的线程要自己拿 `ApplicationContext.capture(Runnable)`，或者拿到这个 context 再 `activate()`。没有 scope 时，`ServiceFramwork.currentInjector()` 才回退到默认应用的 injector。当前线程上如果已经有一个不属于应用的 enhancement scope，调用会失败，不会悄悄改用默认应用。

同一个 context 再 `start` 一次是空操作。`close()` 在做完之后可以再调，第二次是空操作。如果调用时这个应用的 enhancement scope 还开着（包括调用线程自己持有的 scope），`close()` 直接拒绝，不改状态、不停 HTTP、也不把 enhancement context 丢掉。这次拒绝走 `EnhancementContext.admitShutdown()`，不再反射私有的 scope 计数。scope 结束后再 `close()` 才会释放。这一步不会在自己持有的 scope 上等它结束。

没有 scope 时，`close()` 先把 enhancement context 收成「正在关闭」。从这一刻起，`ApplicationContext.activate()` 和直接的 `EnhancementContext.activate()` 都会失败，然后才停 HTTP、关数据库和其它资源。准入用的那把锁不会握过服务器线程的 join，避免请求线程再进 `activate()` 时和关闭线程互相等。并发的第二次 `close()` 不再做一遍清理，而是等这一次做完。清理过程中的其它失败收成一个主异常，其余挂在 suppressed 上，后面的步骤仍继续做。资源释放本身在 `completeClose()`。

已经在某个加载器里 `define` 过的类撤不掉。这个事实按加载器身份记在一张弱引用表里，不用 `equals`。同一个还活着的加载器再 `open` 会失败，并说明不支持热替换；`equals` 和 `hashCode` 就算相同、对象不是同一个，也不会被拒绝。应用和加载器的句柄都放开之后，这条记录不阻止加载器被回收。失败发生在定义之前时，`RUNNING` / `STARTING` 不留这个加载器，换一个新的 context、沿用这个加载器可以再启动。关掉默认应用后，静态 `defaultContext` 不再指向它。

`Bootstrap.main` 负责打印异常、`System.exit(3)`，以及在确实打开了端口且没有 `enableNoThreadJoin()` 时 `join`。`Bootstrap.configureSystem()` 和 `configureSystem(Settings, Class)` 把异常抛给调用方，不退出进程，也不 join。

## 启动顺序

1. 读配置，确定 mode，以及 HTTP / Thrift / Dubbo / MySQL / MongoDB 是否启用。
2. 装载扩展描述。配置里禁用的扩展只保留类名字符串，不 `Class.forName`。
3. 检查扩展 id、提供的能力、依赖、重复提供和环。这一步失败时还没有注册，也没有打开端口。
4. 按拓扑顺序 `validate`，再 `register`。MySQL 启用时，`register` 调用 `JPA.configure(configuration, context)`。MongoDB 启用时调用 `MongoMongo.configure`。这个配置会在应用的 enhancement context 上登记一个 closer；context 关闭时会 unpublish 并关掉客户端。扩展自己的 `close()` 再调一次 `config.close()`，和那条 closer 是同一条收尾，重复调用是空操作。数据源 disable 或 `application.extensions.disabled` 里的类名不会 `Class.forName`，因此不扫描、不连接、也不加载那条实现类。一个写在禁用名单里、目标加载器上根本不存在的类，不会阻止 `configureSystem` 返回。
5. 扫描 Service / Util。这两类不改字节码，用 `Class.forName(binaryName, false, targetLoader)`。Controller 走 `controller-filter` 规则，全部 `apply` 成功后才 `define`。过滤器缺方法或签名不对，在这里失败。
6. 创建 Guice injector，注册路由。
7. 扩展 `start`。MySQL 启用时这里调用 `JPA.getJPAConfig()`，让 Hibernate 的启动失败发生在监听端口之前。
8. 最后才启动 Thrift、HTTP、Dubbo。`mode=test` 不再跳过端口；要跳过就设 `http.disable`、`thrift.disable` 或 `ServiceFramwork.disableHTTP()`。

任何一步失败都会按启动的反序关闭已经创建的扩展和服务器，原始异常保留，关闭中的新异常进 `addSuppressed`。失败路径不会留下监听中的端口。MySQL 或 MongoDB 的配置、连接、增强或 `EntityManagerFactory` 失败发生在 HTTP 监听之前，已经打开的连接和客户端会关掉。

`ModelLoader` 和 `DocumentLoader` 仍是公开入口，但不再自己扫类、不再 `toClass`、也不再把异常打印后继续。它们要求当前线程上有应用上下文，然后分别进入 `JPA.configure(configuration, context)` 和 `MongoMongo.configure`。数据源没启用时不另开一条扫描。异常原样抛出。类定义只走 common 的 `ClassDefiner`。

HTTP 端口用 Jetty 9.2 的 `ServerConnector`。`http.port=0` 表示让系统分配端口，`HttpServer.getHttpPort()` 在 `start()` 返回后给出实际端口。`start()` 绑定完成后返回，不再为了 `join` 另起一条不会结束的线程。`close()` 会 `stop` 并 `destroy`。

## 扩展

```java
public interface FrameworkExtension {
    String id();
    default int version() { return 1; }
    default List<String> provides() { return Collections.singletonList(id()); }
    default List<String> requires() { return Collections.emptyList(); }
    default boolean enabled(Settings settings, ApplicationContext context) { return true; }
    default void validate(Settings settings, ApplicationContext context) {}
    default void register(Settings settings, ApplicationContext context) {}
    default void start(Settings settings, ApplicationContext context) {}
    default void close() {}
}
```

`version()` 默认是 1。`validate` 不要打开连接或定义类。`close()` 在 `start` 没跑过时也必须安全，并且可以重复调用。

`enabled(Settings, ApplicationContext)` 只在实现类已经被 `Class.forName` 之后调用。返回 false 只会让这个类不进入后面的 `validate` / `register` / `start`，避免不了加载。要避免加载，把类名写进 `application.extensions.disabled`，或关掉对应数据源。

关掉 ORM 或 Mongo，验收的是不加载这两条实现类、不连接、不扫描。这不等于可以把 Web 父 classpath 上的 `javax.persistence-api`、JAXB API、activation API 或 SLF4J API 删掉。终验审计仍然解析这些 API jar。

`ApplicationLifecycleTest.disabledExtensionsAreNotLoadedFromARefusingLoader` 用的是 quiet 配置：MySQL、Mongo、HTTP、Thrift、Dubbo 都关着。子加载器只拒绝 ORM/Mongo 扩展类以及 `net.csdn.jpa.*`、`net.csdn.mongo.*`。断言是拒绝名单为空，而且 HTTP 没有启动。父加载器上的 API jar 还在。`disabledMissingClassStillStarts` 也是这套 quiet 配置：禁用名单里一个不存在的类不会被加载，`configureSystem` 能返回，HTTP 仍然没启动。这两条都不证明开着 HTTP 的生产进程可以丢掉 API jar。

配置：

| 键 | 作用 |
| --- | --- |
| `application.extensions` | 逗号分隔的启用类名，按书写顺序做拓扑的平局 |
| `application.extensions.disabled` | 逗号分隔的类名。类可以不存在。不会加载 |
| `{mode}.datasources.mysql.disable` | 缺省 `false`。为 `true` 时不加载 ORM 扩展 |
| `{mode}.datasources.mongodb.disable` | 缺省 `true`。为 `false` 时才加载 Mongo 扩展 |

内置 ORM 扩展提供 `datasource.mysql`，Mongo 扩展提供 `datasource.mongodb`。增加一个扩展只需要实现接口并把类名写进配置，或者在 `start` 之前 `ApplicationContext.addExtension`。不必改 `Bootstrap`。

`registerModule`、`application.dynamic.implemented.*` 和 ORM 自己的校验器配置还是原来的入口。未启用的数据源不会去加载 `type_mapping` 里的类。

## Controller 锚点和过滤器

每个会被 `define` 的控制器包都要有一份**事先编译**的 `ServiceFrameworkPackageAnchor`，和控制器同一个加载器、同一个包、同一个 `ProtectionDomain`。不要把业务类当锚点，也不要在启动时生成锚点。示例里的 `com.example.model`、`com.example.controller.http`、`com.example.controller.api`、`com.example.controller.api.mock` 已经放了空锚点。

查询注解处理器 `ServiceFrameworkQueryProcessor` 在生成 companion 时，如果该包还没有锚点，会写出同一份空类；已经有手写锚点就不会覆盖。处理器不会把模型类本身当成锚点，也不会提前加载即将增强的目标类。迁移时给每个要被定义的包加这个空类即可，JDK 8 和 JDK 17 都要有。

`application.controller` 里的具体控制器会跑 `controller-filter`：把 `ApplicationController` 上 `parent$_` 字段和方法复制到子类，并把 `<clinit>` 里对 `beforeFilter` / `aroundFilter` / `afterFilter` 的调用改到子类自己的副本。抽象控制器不定义。`application.controller.default` 和 `application.controllerNames` 只 `Class.forName`，不重新定义。

过滤器目录在启动时编好，放在这个 context 里，请求路径只读。类名用类对象身份区分，关闭 context 时丢掉目录，避免第二个应用留着第一个加载器的 `Class`。顺序来自 class 文件里的字段顺序，再加上静态注册用的 `LinkedHashMap`，不用 `HashMap` 或 `getDeclaredMethods()` 的返回顺序。

声明方式仍然是：

```java
static {
    beforeFilter("authorize", map("only", list("show")));
    aroundFilter("outer", map());
    aroundFilter("inner", map());
    afterFilter("cleanup", map());
}
```

`only` 优先于 `except`。也可以用 `@BeforeFilter` / `@AroundFilter` / `@AfterFilter` 标在静态字段上，字段名去掉开头的 `_` 就是方法名，字段值是 `only` / `except` 的 Map。

一次请求的顺序：

1. before，按声明顺序。
2. around 进入，按声明顺序。
3. action。
4. around 退出，是各 around 在 `next.invoke()` 之后的代码，所以自然是声明顺序的逆序。
5. after，按声明顺序。

`render` 抛出的 `RenderFinish` 是正常结束，不是错误。它不会跳过 around 退出，也不会跳过 after。其它异常会从 around 方法里抛出去，那个方法里 `next.invoke()` 后面的代码不会跑，除非方法自己写了 `finally`。before 失败时，剩下的 before、around 和 action 都不跑。after 一律跑，包括 before 失败、action 失败和 around 失败。原来的异常继续抛出；after 自己的异常用 `addSuppressed` 挂在它上面。如果之前没有异常，after 的异常就是这次请求的异常。

before / after 必须是无参实例方法。around 必须是单个 `WowAroundFilter` 参数的实例方法。找不到方法，或者找到了但签名不对，启动失败，阶段是 `filter`，消息里有类名和方法名。

## 日志

`config/logging.yml` 仍然是 Log4j 1 的文档，文件里只有两行说明：它是旧格式，并且 `LogConfigurator` 会翻译、不会把应用配置抄进去。键本身没改：`rootLogger: INFO,console,file`，appender 的 `type` 用 `console` / `dailyRollingFile`，layout 用 `conversionPattern`，滚动用 `datePattern`。`LogConfigurator` 把这几项翻译成 Log4j 2 的 properties（`Console`、`RollingFile`、`pattern`、按天滚动），再交给 `PropertiesConfigurationBuilder`。已经写成 Log4j 2 键（例如 `rootLogger.level`，`type` 是大写的插件名）的文档会原样传过去。这次矩阵跑过这层翻译。

翻译只拿日志文档里的键。应用配置只用来替换 `${path.logs}` 这种占位符，口令和 JDBC 地址不会写进日志配置。翻译或 Log4j 2 构建失败时，进程级的 loaded 标志保持未设置，下一次 `configure` 可以换一份正确文件重试。成功之后，同一次进程里的再次 `configure` 是空操作；`LogConfigurator.reset()` 只清这个标志，不回滚已经装上的配置。`LoggerLoader` 不再把配置异常吃掉，所以坏的 `logging.yml` 会在监听端口之前让启动失败。

## 控制器诊断

诊断默认关闭。要在一次真实启动里打开，把 `EnhancementDiagnostics.enabled(目录)` 传给 `ApplicationContext.open(marker, diagnostics)` 或 `Bootstrap.configureSystem(settings, marker, diagnostics)`。目录由调用方指定，框架不自己挑一个路径，也不会把 Settings 写进去。`setEmitGeneratedSource` 和 `setEmitClassFiles` 默认仍是 false，这条启动路径不会替你打开。

`web-1` 是控制器类名摘要的格式，不是应用配置修订。`Bootstrap.configureSystem` 在诊断打开、且调用方还没有修订时写入 `app-1`。Controller 计划开始前，如果已经有修订，事件保留它，只更换排序后控制器类名的 SHA-256；没有修订时才写 `web-1`。计划结束后，如果进来之前已经有修订，连同原来的摘要写回去，后面的事件不会继续带着控制器摘要。`ControllerFilterRule` 只改当前控制器，仍然用默认的 `affectedClasses`。关掉诊断时不做这次类名摘要，`hashComputations`、`bytecodeReads`、`methodInspections`、`diskWrites` 保持 0。context 上若有 `EnhancementObserver`，原始类字节仍会读给观察器，但这四个计数和这次类名摘要保持关闭。`applyReason` / `skipReason` 也只在诊断打开时调用。

启动阶段时钟是另一件事。调用方在 `ApplicationContext.open` 之后、`start` 之前，把 `StartupPhaseTrace` 放进 enhancement context 的 `serviceframework.startup.phases`。不放的话，钩子只做空判断，不多取一次 `nanoTime`，也不打开诊断。这不是 Settings 开关。

## 验收跑法

不设上面三个开关时的跳过，只说明这次没跑实库。2026-09-25 的默认矩阵三个开关都打开，Web 生命周期和数据库套件没有跳过。命令、退出码和哈希在 [serviceframework-bytecode-migration.md](serviceframework-bytecode-migration.md)。那次实跑的服务器协议是 HTTP。Thrift 和 Dubbo 没有作为流量执行。
