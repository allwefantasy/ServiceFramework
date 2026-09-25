# ServiceFramework 字节码增强：当前实现与迁移

日期：2026-09-25。本文描述本仓库已经验收的默认矩阵，不是分批计划。行为细节在后面列出的专题文档里。勾选过的 T01–T15 和每条证据在 [serviceframework-bytecode-todo-jdk8-jdk17-2026-09-24.md](serviceframework-bytecode-todo-jdk8-jdk17-2026-09-24.md)。

权威验收记录是 `target/bytecode-final-verification-20260925/summary.md`。同目录的 `summary.txt` 是逐步命令和退出码，`commands/run-verification.sh` 是当时的驱动脚本，`commands/sanitized-env.txt` 只保留开关和长度，不包含口令。那次运行没有改产品、测试、POM 或文档。本文是那次记录之后的文档对齐。

## 支持范围

默认组合是 Scala **2.13.16**、Java 8 字节码、classpath 上的无名模块、Javassist **3.33.0-GA**。JDK 17 编译，同一批产物再分别用 JDK 8 和 JDK 17 跑，运行时不加 `--add-opens`。

| 项 | 已验收 | 未验证 |
| --- | --- | --- |
| Scala | 2.13.16 | profile `scala-2.11`（2.11.8，只有 `-target:jvm-1.8`）、`scala-2.12`（2.12.8） |
| 部署 | classpath，无名模块 | JPMS module-path |
| Javassist | 3.33.0-GA，见下面的哈希 | 3.30.2-GA 没有在这次全框架矩阵里重跑 |
| 运行时 | JDK `1.8.0_504`、JDK `17.0.20.1` | 其它小版本 |

3.30.2-GA 仍可用 `-Djavassist.version=3.30.2-GA` 做对照，父 POM 默认不是它。更早的 common-only 对照记在文末，不能代替这次全框架结果。

类定义没有热替换，也没有 `redefine`。同一个还活着的加载器再 `define` 会失败。要再启动，换一个新的应用 `ClassLoader`，或者新进程。已经定义进 JVM 的类撤不掉。`CtClass.detach()` 只丢掉 Javassist 缓存。

## 类定义和包锚点

入口是 `net.csdn.common.enhancer.ClassDefiner`，挂在一个 `EnhancementContext` 上，不是全局单例。ORM、Mongo、Controller 的规则只改 `CtClass`，不调用 `toClass()`。调用方在 `EnhancementPlan.apply` 成功之后 `define` 一次。

每个要被 `define` 的包，在 **JDK 8 和 JDK 17 上都要有一份事先编译好的** `ServiceFrameworkPackageAnchor`（`ClassDefiner.ANCHOR_SIMPLE_NAME`）。它和目标类同一个加载器、同一个包、同一个 `ProtectionDomain`。不要把即将增强的模型或控制器先加载进来当锚点，也不要在启动时临时生成锚点。查询处理器如果发现该包还没有这个类，会在生成伴生类时写出一份空类；已经有手写类就不覆盖。

`define` 在两条分支之前都会解析锚点，并用 `isNamedModule` 拒绝命名模块。判断用 Java 8 能编译的反射；JDK 8 上没有模块 API，结果是 false。JDK 17 上 classpath 类是无名模块，可以定义；`java.lang.String` 这种命名模块会被拒绝，消息是 `named modules are not supported; use the unnamed classpath module`。

运行时分支看 `java.specification.version` 是否以 `1.` 开头：

- Java 8：`CtClass.toClass(loader, domain)`。这一支不调用 neighbor 重载，类文件里也不引用 `java.lang.Module` 或 `MethodHandles`。
- 其它运行时：`ClassPool.toClass(type, neighbor, loader, domain)`。neighbor 就是已经解析的锚点。

两条路径定义完都检查：结果类的加载器和 `ProtectionDomain` 必须与请求是同一个对象（`==`）。从资源读入且主版本高于 52 的类在定义前失败。运行时 `makeClass` 出来的类在定义前被改成主版本 52。

失败是 `EnhancementFailure`，带 `category`、类名、规则、阶段和 cause。定义阶段的文本里有加载器身份和 CodeSource，不打印环境变量或业务 class 字节。

## 上下文、旧入口和重复启动

`EnhancementContext.activate()` 把上下文压进当前线程。请求线程在 HTTP 处理期间持有 scope，结束时关掉；scope 不跟着线程池走。别的线程用 `ApplicationContext.capture(Runnable)`，或者自己拿到 context 再 `activate()`。外层比内层先关、或者跨线程关，都会失败。

`close()` 时如果这个应用的 scope 还开着，直接拒绝，不停 HTTP，也不丢掉 enhancement context。scope 都关掉之后，`close()` 先进入正在关闭，再停服务器、关数据库。可以重复调用，第二次是空操作。已经 `define` 的类留在那个加载器里，直到加载器自己可以被回收。关闭后的堆和 Metaspace 读数不是卸载证明。

旧的公开入口还在，但不再自己扫类或 `toClass`：

- `ModelLoader` / `DocumentLoader` 要求当前线程上有应用上下文，然后进入 `JPA.configure(configuration, context)` 或 `MongoMongo.configure`。数据源没启用时不另开扫描。异常原样抛出。
- `ServiceFramwork.injector`、`scanService`、`modules` 只是**默认应用**的别名。第二个加载器启动时不改写它们。请求和 dispatcher 使用 `ServiceFramwork.currentInjector()`：有应用 scope 就用这个应用；没有 scope 才回到默认应用；当前线程上如果是一个不属于应用的 enhancement scope，调用失败。
- `serviceframework.dispatcher.ServiceInj.findService` 只调用 `currentInjector()`，不保存自己的 injector。

重复启动用新的应用加载器。终验里的 repeated 组就是：同一个 JVM，丢掉 1 次预热，再测 5 次新加载器。这不是热替换。

## 扩展和规则注册

扩展在应用启动前装好，然后启动。改规则或加扩展之后要重新启动应用。不支持在已经 `define` 的加载器上换一套字节码。

`FrameworkExtension.version()` 默认返回 1。`provides()` 默认就是自己的 `id`。`validate` 不要打开连接或定义类。`close()` 在 `start` 没跑过时也必须安全，并且可以重复调用。

谁会被加载：

| 配置 | 行为 |
| --- | --- |
| `{mode}.datasources.mysql.disable` | 缺省 false。为 true 时不 `Class.forName` ORM 扩展，不扫描，不连接 |
| `{mode}.datasources.mongodb.disable` | 缺省 true。只有写成 false 才加载 Mongo 扩展 |
| `application.extensions` | 逗号分隔的启用类名，会加载 |
| `application.extensions.disabled` | 逗号分隔的类名。只保留字符串，不 `Class.forName`。类可以不存在 |

`enabled(Settings, ApplicationContext)` 在实现类**已经被加载之后**才执行。它返回 false 只会让这个已经加载的类不进入 `validate` / `register` / `start`。单靠 `enabled` 避免不了类加载。要避免加载，用上面的禁用类名或数据源 disable 开关。

禁用 ORM 或 Mongo，验收的是：不加载这两条实现类，不连接，不扫描。这不等于可以把 Web 父 classpath 上的 JPA API、JAXB API、activation API 或 SLF4J API 拿掉。终验的依赖审计仍然解析这些 API jar。框架库这边放的是 API，不强制绑定；应用可以自己选一个绑定，只要不制造重复 class。

`ApplicationLifecycleTest.disabledExtensionsAreNotLoadedFromARefusingLoader` 的范围要看清楚。它用的是 quiet 配置：MySQL、Mongo、HTTP、Thrift、Dubbo 都关掉。子加载器只拒绝 `OrmFrameworkExtension`、`MongoFrameworkExtension`、`net.csdn.jpa.*` 和 `net.csdn.mongo.*`。测试断言拒绝名单是空的，而且 HTTP 没有启动。父加载器上的 API jar 没有被删掉。`disabledMissingClassStillStarts` 同样是 quiet 配置：一个不存在的禁用类名不会被加载，`configureSystem` 能返回，HTTP 也没有启动。

注册 API，都要在对应的 `configure` 或 `start` 之前调用：

| API | 作用域 |
| --- | --- |
| `CSDNORMConfiguration.addEnhancementRule` / `enhancementRules()` | 加进 ORM 同一张规则表，和 `entity-mapping`、`orm-query`、`association` 一起交给 `EnhancementPlan.compile`。最终顺序是 `requires` / `before` 的稳定拓扑，登记顺序只做并列时的先后。没有边时后加的规则会排在后面，这不是无条件的固定末位 |
| `CSDNMongoConfiguration.registerRule` | 加进 Mongo 同一张规则表，和 `mongo-document` 一起编译，同样按拓扑执行，不是无条件排在最后 |
| `ApplicationContext.addEnhancementRule` | 只进入 Controller 计划，和 `controller-filter` 放进同一张表再按拓扑编译。不是全局规则表，也不是无条件排在 `controller-filter` 后面 |
| `ApplicationContext.addExtension` | `start` 开始前（状态仍是 `NEW`）。开始之后再加会失败 |

`addExtension` 或配置里的类名是安装方式。装完要重启应用才会执行。规则 id 重复、依赖缺失或成环，在 `define` 之前失败。

预定规则 id：`entity-mapping`、`orm-query`、`association`、`mongo-document`、`controller-filter`。common 不注册它们，也不依赖 ORM、Mongo、Web。

## 伴生查询和 Quill

伴生查询是显式声明的有界查询：若干字段的 AND 相等、可选 `asc` / `desc`、以及 `offset` / `limit`。不生成 `Like`、`Or`、连接、子查询、更新、删除或 DTO，也不枚举全部字段组合。上限是每个模型 8 个方法、每个方法 4 个相等字段和 3 个排序字段、`limit` 最大 500。声明见 [generated-query-api.md](generated-query-api.md)。

模型类本身不加新方法。生成的是同包的 `模型名Queries`。整体集成已经改过 `JPA`、`JPQL` 和增强器（实体名、上下文、加载）。不能把这件事说成“新代码完全不碰 JPA / JPQL”。处理器自己仍不改模型字节码，也不 `Class.forName` 目标模型。

仓库没有 `META-INF/services/javax.annotation.processing.Processor`。消费方显式指定：

```text
javac --release 8 -encoding UTF-8 \
  -classpath serviceframework-orm.jar:javax.persistence-api-2.2.jar \
  -processor net.csdn.jpa.query.ServiceFrameworkQueryProcessor \
  -processorpath serviceframework-orm.jar \
  -d out/classes -s out/generated \
  src/main/java/com/example/OrderEntityBase.java \
  src/main/java/com/example/OrderEntity.java \
  src/main/java/com/example/client/OrderQueryCaller.java
```

这是最小夹具的示意，不是任意业务模型的完整 classpath。上面的 `OrderEntity` 继承 `OrderEntityBase`，所以基类源文件必须和子类一起交给 javac。下游业务 classpath 还要带上模型超类以及这些类型自己的依赖。只放 `serviceframework-orm.jar` 和 `javax.persistence-api-2.2.jar`，只够这个最小夹具。命令按 JDK 17 的 `javac --release 8` 来写。JDK 8 的 javac 没有 `--release`，改用 `-source 8 -target 8`。

IDE 要把 `-s` 目录标成 generated sources，注解处理的处理器路径指向已经编译好的 ORM jar，处理器名就是上面的类。父 POM 不会替下游应用自动挂上这个处理器。模型和调用方可以在同一次 `javac` 里，也可以先编模型再编调用方。模型超类和它的依赖哪一次都不能缺。

Java：

```java
OrderEntityQueries.findByStatusAndTenant(entityManager, status, tenantId, 0, 50);
OrderEntityQueries.findByStatusAndTenant(status, tenantId, 0, 50);
```

第二个重载使用当前 ORM 上下文里的 `EntityManager`。Scala 同样直接调用，参数类型与生成方法一致：

```scala
OrderEntityQueries.findByStatusAndTenant(em, status, tenantId, offset, limit)
```

增强完成之前不要用类字面量碰到目标模型，否则 JVM 会先装入未增强的字节码。

Quill 的稳定写法是本地导入。`QuillDB.ctx` 是方法，`import QuillDB.ctx._` 不是稳定路径：

```scala
val ctx = QuillDB.ctx
import ctx._
```

`QuillImportCompileTest` 编译的就是这个形式。每个应用的 Quill 上下文属于它自己的 `EnhancementContext`。

## 诊断

默认关闭。终验的冷启动和重复启动也没有注册 `EnhancementObserver`，阶段 JSON 里 `observer` 为 false。诊断关闭且没有观察器，才是零捕获：不做 hash、不读 class 字节、不做方法对比、不写磁盘。只关诊断、但 context 上有观察器时，原始字节仍会读出来交给观察器；诊断哈希、计数和报告保持关闭，`applyReason` / `skipReason` 也不会跑。打开诊断时用 `EnhancementDiagnostics.enabled(目录)` 传给 `ApplicationContext.open` 或 `Bootstrap.configureSystem`。框架不自己挑目录，也不把 Settings 写进报告。

`setEmitGeneratedSource` 和 `setEmitClassFiles` 默认都是 false。内置 ORM、Mongo、Controller 规则不调用 `emitSource`，所以打开诊断也不会凭空出现 `sources/`。class 文件只有显式打开 `setEmitClassFiles` 才落到 `original/` 和 `enhanced/`。

`noteSafeMetadata` 只接受配置版本令牌和可选的 schema 摘要。摘要是 64 个十六进制字符，也就是 256 位 SHA-256，不是 64 位整数。令牌不能含 password、passwd、secret、credential、jdbc。报告和阶段 JSON 里不写 JDBC URL 或口令。失败文本里的 `jdbc:` 和 `password=` 会先换成 `[redacted]`。

`originOf(className, signature)` 给出该方法最后一次新增、改写或移除的规则。终验冒烟在两个 JDK 上都写出了 `WebRecord`、`WebNote`、`BothController` 的增强前后 class，没有 `sources/`，六个哈希跨 JDK 一致。调用方修订 `config-20260924` 被保留。`ALTER` 把 schema 摘要从 `8a7dc706263c98a936ec6255dcd4929b7b53e1e3fbe71a25a75449eee88d4c7b` 换成 `5c68d4af815134731a0e8425846390f7c43cc33e9f9c1661fcc96aabb78daba9`。

冷启动和重复启动的诊断是关的，并且没有观察器。基准如果看到 hash、字节读取、方法检查或写盘不为 0，会中止。那组 0 是零捕获基线，不能当成“有观察器也不会读类字节”。

## 测量

这是一份新基线，不是“相对旧 JDK 17 启动变快了”。旧加载器在 JDK 17 上起不来，没有那份历史对照。也没有增强结果缓存，不能承诺下次启动会复用这次的字节码。

样本很小，而且操作系统页缓存和数据库都是热的：MySQL 8.0.46 和 MongoDB 4.4.29 已经在回环上跑着。冷启动每个 JDK 3 个新 JVM，重复启动是 1 次预热加 5 个新加载器，热调用是同一个 context 上 20 次 `GET /db/both`。服务包和 util 包是空的。`System.gc` 之后的堆和 Metaspace 只是观察，JSON 写明这不是卸载证明。

墙钟 p50（纳秒）：

| 组 | JDK 8 | JDK 17 |
| --- | ---: | ---: |
| 冷启动，n=3 | 4360502596 | 3835738867 |
| 重复启动，n=5 | 720581888 | 703624526 |
| 热调用，n=20 | 9828895 | 15433462 |

JDK 8 冷启动阶段 p50（纳秒，n=3）：`scan.orm` 473518939，`rule.entity-mapping` 70046000，`rule.orm-query` 42374535，`rule.association` 13490487，`define` 29662721，`mongo.connect` 85366498，`scan.mongo` 214895554，`rule.mongo-document` 28228464，`scan.controller` 218387190，`controller.makeClass` 138802，`rule.controller-filter` 1525333，`guice` 529425650，`jpa.emf` 1772315229，`http.bind` 52962741。JDBC 元数据 p50 25824810。JDK 17 和其余 min / p95 / max / mean 在 `target/bytecode-final-verification-20260925/phases/framework-phases-aggregate.txt`。exclusive 是非嵌套阶段加上这一次 JDBC 元数据；这次没有嵌套阶段。residual 是墙钟减去 exclusive，并且不小于 0。每次冷启动和重复启动都是 `getTables` 1 次、`getColumns` 1 次。

扫描峰值是另一件事，这次矩阵没有重测。更早的 common 夹具记录在 `/tmp/sf-t12-bench-verified/enhancement-costs.json`：25 个类，逻辑流峰值从 25 降到 1，p50 大约 15.1 ms 对 14.1 ms，`startupSpeedupClaimed` 是 false。那是逻辑 `InputStream` 数，不是操作系统文件描述符，也不能说成整个启动变快。`DefaultScanServiceTest` 在这次矩阵里跑过。

12 组阶段 JVM（两个 JDK 的 cold×3、repeated、hot、smoke）退出码都是 0。阶段 classpath 清单 830 行，跑前跑后哈希都是 `d2fa923a51595ccadba742d649ec09956a1b62579683f19f33c5f968d1392ae1`。

## ORM 只有这一份

本仓库的 `serviceframework-orm` 是 ORM 的权威实现。终验的 Web classpath 上，`net.csdn.jpa` 的实现来自安装好的 `serviceframework-orm_2.13-2.0.9.jar`，没有第二份 ActiveORM artifact。

独立仓库 `active_orm` 这次没有改，也没有拿来编译或测试。不能写成已经移植、已经对齐 JDK 8 / JDK 17，或已经和本仓库共用一次发布。维护边界在 [active-orm-maintenance.md](active-orm-maintenance.md)。

## 验收矩阵

驱动是构建锁加 `dev/compat-services.sh run`，再执行 `commands/run-verification.sh`。工作目录是快照，不是一份 `git archive`。三个开关一起打开：`SF_ORM_MYSQL=true`、`SF_COMPAT_MONGO=true`、`SF_COMPAT_WEB_DB=true`。数据库用户和口令没有导出。测试自己读权限 600 的 env 文件，那份文件没有被打印。

没开这三个开关时，对应用例会跳过。那种跳过只说明这次没跑实库，不是验收通过。终验把三个开关都打开了，两个 JDK 都是 1212 个测试、0 跳过、0 失败、0 错误。

| 项 | 值 |
| --- | --- |
| JDK 8 | `/usr/local/opt/openjdk@8`，`1.8.0_504` |
| JDK 17 | `/usr/local/Cellar/openjdk@17/17.0.20.1/libexec/openjdk.jdk/Contents/Home`，`17.0.20.1` |
| Maven | `mvn -B --no-transfer-progress -s '/tmp/sf maven central/settings.xml'`，`performRelease=false`，跳过 javadoc。`MAVEN_OPTS`、`JAVA_TOOL_OPTIONS`、`_JAVA_OPTIONS`、`JDK_JAVA_OPTIONS`、`MallocStackLogging` 都未设置。没有 `--add-opens` |
| 构建 | JDK 17 `install -DskipTests`。然后两个 JDK 各跑一次 `surefire:test` 3.2.5 和 `scalatest:test` 2.2.0，中间不 clean、不 compile |
| 数据库 | MySQL 8.0.46，端口 63714；MongoDB 4.4.29，端口 63811。都是 `127.0.0.1`，库名 `sf_compat`。不是 3306 或 27017 |
| 协议 | 实库和生命周期跑的是 HTTP（Jetty 9.2 的端口打开和关闭在 Web 套件里）。Jetty 模块自己没有测试类。Thrift 和 Dubbo 没有作为流量被执行，不能写成已经通过 |
| 依赖审计 | `dev/audit-runtime-dependencies.py` 退出码 0，`AUDIT_OK`，可见 class 181404，高于 52 的可见 class 为 0，`duplicate_key_classes` 为 0 |
| 时间 | 兼容脚本 2026-09-25 00:35:34–00:44:23 +0800，阶段测量 00:44:23–00:46:09 +0800，驱动退出码 0。测完两个服务都停了 |

每个 JDK 的计数：common 559 JUnit，ORM 306 JUnit，Mongo 282 JUnit，Web 37 JUnit + 6 ScalaTest，dispatcher 22 ScalaTest。1020 个参数化用例（common 500、JPA finder 260、Mongo finder 260）没有把跳过藏起来。

同一份 883 行 class/JAR 清单在构建后、JDK 8 测试后、JDK 17 测试后哈希都是 `9dda7796168de94cb036fef000609dc938fecc19ce1cf6b69622b0f37e9feea7`。安装好的 `*_2.13-2.0.9.jar`：

| 模块 | SHA-256 |
| --- | --- |
| common | `ff25361b7aa1e593aae732ca30eff733575b7e3a8b122c5d2950af262eb71e18` |
| orm | `694b163a7244d4053960cbf890bb08026bba18c652e2e058bc1b3271933a6283` |
| mongo | `350b9c387c0e35593f5c436a6210c6b0bc4d7aca9cb7aae39eaf3a8a53ed948e` |
| web | `fa7e2d8a8278ee939b8e3f8881177033d945ef5b9466e079c7b6346524aecf28` |
| dispatcher | `2533f0c8138bab29648f2860d4920caa0add8fc1699d017985c15852c799b5c3` |
| jetty-9-server | `73c2167f8e78904068c0b9518fa56406235be9da160e64aa9fd2ea27b599d1a0` |

Javassist 3.33.0-GA 在每条 classpath 上解析一次，SHA-256 `1620478adc5f4d2eccd356e59513c270f1508bed53ce75deffb3107b7b43db2c`（772302 字节）。SLF4J 不是全仓都 1.7.32：common、dispatcher、jetty-9-server 解析 `slf4j-api` 1.5.8；ORM、Mongo、Web 解析 1.7.32。两边都是 API，没有绑定 jar。Guice 5.1.0 和 `guice-assistedinject` 5.1.0 是两个归档、类名不重复。ORM 的 `target/classes` 和 `target/test-classes` 也是两个归档、类名不重复。审计比较的是这些前缀下的重复 class。同一个前缀可以来自多个归档。应用自己的 SLF4J 绑定只要不重复 class 就允许；终验解析到的是 API，没有绑定 jar。

Hibernate 仍是 5.3.7.Final，`javax.persistence-api` 仍是 2.2，Jetty 仍是 `9.2.16.v20160414`，Connector/J 仍是 5.1.6。Mongo Java 驱动是 **3.12.14**，不是 2.11.4。负向死端点测试的 Surefire 日志里有一条不带用户和口令的 JDBC URL。诊断 `report.txt` 和阶段 JSON 里没有 `jdbc:`，也没有 `password=`。

终验当时 `commands/source-manifest.tsv` 把 582 个文件标成 protected，身份 `b00b0fc3dc747a2ffcb2ae3a6ab16790505f54bd9c6ceb20c78cff135161eaf9`。这是那一次运行前后的身份，不是现在把这 582 个路径重新哈希之后的结果。这 582 个里包含根目录的 `README-EN.md`。它是文档，不是产品、测试、POM、dev 或 config。另外 15 个文件标成 docs（`README.md` 和 `docs/`），身份 `4651c5cf6a995cc68072bf536a3234b1ea2402aa7e70145735af5a1ae59914ac`，不进上面那个 protected 哈希。终验之后再改文档，包括已经和当时不同的 `README-EN.md`，不改变已经验收的二进制源码。Git HEAD `879f45759626c472591de7c33e2e5ae75930ab31` 也不是这份源码身份，因为工作区当时是脏的，按文件复制。

日志：

- `target/bytecode-final-verification-20260925/test-jdk8.log`
- `target/bytecode-final-verification-20260925/test-jdk17.log`
- `target/bytecode-final-verification-20260925/build-jdk17.log`
- `target/bytecode-final-verification-20260925/animal-sniffer.log`
- `target/bytecode-final-verification-20260925/dependency-audit.txt`
- `target/bytecode-final-verification-20260925/phases/framework-phases-aggregate.json`

## 更早的 Javassist 对照

下面是更早一次冻结的 **common-only** 基线，不是当前全框架验收。每个 JDK、每个 Javassist 版本是 500 个真实 common 测试，0 跳过，当次运行内部的 106 行 class/JAR 清单稳定。

| 版本 | 报告 | 原始日志 | SHA-256 | 字节 |
| --- | --- | --- | --- | --- |
| 3.33.0-GA | `/tmp/sf-head-snapshot/target/jdk-compatibility/` | `/tmp/sf-verify-333.out` | `1620478adc5f4d2eccd356e59513c270f1508bed53ce75deffb3107b7b43db2c` | 772302 |
| 3.30.2-GA | `/tmp/sf-head-snapshot/target/jdk-compatibility-3302/` | `/tmp/sf-verify-3302.out` | `eba37290994b5e4868f3af98ff113f6244a6b099385d9ad46881307d3cb01aaf` | 794714 |

发行说明曾核对过 [rel_3_33_0_ga](https://github.com/jboss-javassist/javassist/releases/tag/rel_3_33_0_ga) 和 [rel_3_30_2_ga](https://github.com/jboss-javassist/javassist/releases/tag/rel_3_30_2_ga)。选定的是 3.33.0-GA。当前结论以本节之前的全框架矩阵为准。
