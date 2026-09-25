**ServiceFramework 字节码增强与扩展性优化 TODO：同时兼容 JDK 8 / JDK 17**

日期：2026-09-24 列出，2026-09-25 按默认矩阵验收。当前行为和迁移步骤在 [serviceframework-bytecode-migration.md](serviceframework-bytecode-migration.md)。逐项证据指向那次独立运行的 [summary.md](../target/bytecode-final-verification-20260925/summary.md)。

状态：T01–T15 在本仓库默认矩阵上通过。Scala 2.11、Scala 2.12 和 JPMS 没有跑，保持未验证。外部 `active_orm` 仓库没有改，也没有测。没有增强结果缓存，也没有把扫描流峰值写成整个启动变快。Thrift 和 Dubbo 没有作为流量执行。

勾选只表示下面写明的证据成立。原条目里更宽的设想，如果这次没有做，写在该条的边界里，不另算成已完成的优化。

**范围**

ORM 的权威实现是本仓库的 `serviceframework-orm`。独立 `active_orm` 仍是另一份更老的代码，见 [active-orm-maintenance.md](active-orm-maintenance.md)。这次验收覆盖 common、ORM、Mongo、Web、dispatcher 和 jetty-9-server 的默认 Scala 2.13.16 产物。

| 范围 | 现在的入口 |
| --- | --- |
| `serviceframework-common` | `ClassDefiner`、`EnhancementContext`、`EnhancementPlan`、`DynamicBytecode`、`DefaultScanService` |
| `serviceframework-orm` | `OrmEnhancer`、`JPA.configure`、伴生查询处理器 |
| `serviceframework-mongo` | `MongoMongo.configure`、`mongo-document` |
| `serviceframework-web` | `ApplicationContext`、`FrameworkExtension`、Controller 过滤器 |
| `serviceframework-dispatcher` | 策略加载和 `ServiceFramwork.currentInjector()` |
| `serviceframework-jetty-9-server` | HTTP 容器。模块自己没有测试类，端口在 Web 生命周期套件里 |

- [x] **T01 · P0：定义支持矩阵，建立双 JDK 验收入口。** 公共代码和依赖按 Java 8 可运行基线约束。JDK 17 编译，同一批 JAR 再在 JDK 8 和 JDK 17 上跑，中间不编译。Java 使用 `--release 8`，并用 Animal Sniffer 的 `java18` 签名检查，不只看 `source/target=1.8`。默认 Scala 2.13 纳入验收；`scala-2.11` 和 `scala-2.12` 在父 POM 里标成未验证。

  证据：一次 JDK 17 `install -DskipTests`，然后两个 JDK 的 Surefire + ScalaTest，class/JAR 清单不变。Animal Sniffer 六个模块 BUILD SUCCESS。运行时是 `1.8.0_504` 和 `17.0.20.1`。每个 JDK 1212 个测试，0 跳过。见 summary 的 T01 和 [jdk-compatibility.md](jdk-compatibility.md)。

- [x] **T02 · P0：收敛字节码及启动依赖。** 选定并统一的是 Javassist **3.33.0-GA**。3.30.2-GA 是更早的对照，不是这次全框架矩阵的运行版本。Guice 5.1.0，不再使用 Mycila。Hibernate 5.3.7.Final 和 `javax.persistence-api` 2.2 保留。Jetty 仍是 `9.2.16.v20160414`。业务接口仍是 `javax.persistence`。

  证据：每条 classpath 上的 Javassist SHA-256 是 `1620478adc5f4d2eccd356e59513c270f1508bed53ce75deffb3107b7b43db2c`。`duplicate_key_classes` 为 0。Guice core 和 assistedinject 是两个归档、类不重复。JAXB API、activation API、JPA API 在出现时各来自一个归档。SLF4J 只有 API：common、dispatcher、jetty 是 1.5.8，ORM、Mongo、Web 是 1.7.32。没有第二份 ActiveORM。可见 class 主版本高于 52 的数量是 0。`AUDIT_OK`，可见 class 181404。失败条件是这些前缀下的 class 重复，不是每个前缀只能来自一个归档。应用自己的 SLF4J 绑定只要类名不重复就可以留在 classpath 上。见 [jdk-compatibility.md](jdk-compatibility.md)。

- [x] **T03 · P0：把定义类的操作收口到一个入口。** `ClassDefiner` 检查类名、目标加载器、同包锚点、`ProtectionDomain` 和命名模块。JDK 8 走 `CtClass.toClass(loader, domain)`。JDK 9+ 走同包 neighbor 的 `ClassPool.toClass`。两条路径都要求包锚点。命名模块直接拒绝。不使用 `--add-opens`。

  证据：`ClassDefinerTest` 在两个 JDK 上都是 10/10，覆盖锚点、包内访问、重复定义、缺锚点、加载器或保护域不一致，以及 Java 8 字节码上限。实现是 [ClassDefiner.java](../serviceframework-common/src/main/java/net/csdn/common/enhancer/ClassDefiner.java)。

- [x] **T04 · P0：修复已复现的字节码语义问题。** setter 按精确签名替换，其它重载保留。生成 getter/setter 前检查继承链上的 final、static 和返回类型。静态字段复制进目标常量池；`<clinit>` 不复制。每个模型自己的静态元数据不和父类共用。

  证据：`DynamicBytecodeBehaviorTest` 8/8。说明在 [enhancement-contract.md](enhancement-contract.md)。

- [x] **T05 · P0：修正扫描、继承遍历和模型身份。** `scanArchives(String)` 不再调用自己。目录、JAR 和带空格的路径按资源打开并关闭流。模型注册主键是二进制名，简单名只有唯一命中时才能用。

  证据：`DefaultScanServiceTest` 8/8，`ModelClassHierarchyTest` 3/3，`ModelRegistryTest` 1/1。嵌套 JAR 仍然不打开。

- [x] **T06 · P0：让必需增强失败准确中止启动。** 扫描、ORM、Mongo 和 Controller 的失败抛出 `EnhancementFailure`，带类名、规则、阶段和原因。失败路径会关掉已经创建的资源。HTTP 不会在增强或数据库失败之后继续监听。

  证据：生命周期和数据库套件覆盖了必需增强、坏过滤器、缺失实体、非法关联、错误的 MySQL 端点和 Mongo 连接失败。这些失败在监听之前端口就是拒绝的。日志里的类名、规则和阶段来自这些测试自己的断言。

  边界：实跑的服务器协议是 HTTP。Thrift 和 Dubbo 的流量测试没有跑，不能写成通过。Jetty 模块没有自己的测试类。

- [x] **T07 · P0：补真实应用和数据库验收。** 参数化用例继续覆盖命名和签名。另外有加载后的调用，以及 MySQL 8.0.46、MongoDB 4.4.29 上的业务链路。可选模块的打开和关闭都在两个 JDK 上跑过。三个环境开关是 `SF_ORM_MYSQL`、`SF_COMPAT_MONGO`、`SF_COMPAT_WEB_DB`，终验三个都打开，所以这些用例没有跳过。

  证据：`ApplicationLifecycleTest`（无数据库的 HTTP 过滤器、Service/Util 注入、端口关闭）、`ApplicationDatabaseTest` 9/9、`OrmMysqlBusinessTest` 6/6、`QuillImportCompileTest`、`MongoEnhancementLiveTest` 18/18、dispatcher ScalaTest 22/22。Connector/J 是 5.1.6，Mongo Java 驱动是 3.12.14。

  边界：没有 Thrift 或 Dubbo 流量。只开 HTTP、只关数据库的单元跑法仍会跳过实库用例；那种跳过不是这一条的验收。

- [x] **T08 · P1：增加增强规则与执行计划。** 规则有 id、版本、`requires` 和 `before`。`EnhancementPlan.compile` 在改类之前检查重复 id、未知依赖和环。规则不调用 `toClass()`。同一个 context 里同一个类名的第二次 `apply` 是 `CONFLICT`。

  证据：`EnhancementPlanTest` 10/10，`OrmRulePlanTest` 3/3。这些测试里规则不定义类。发现方式是显式 `EnhancementRules.register`，不是 ServiceLoader。

- [x] **T09 · P1：统一模块注册和启停契约。** `FrameworkExtension` 声明 id、能力、依赖、`validate`、`register`、`start` 和 `close`。核心按依赖排序，并按反序关闭。配置错误在打开端口之前失败。

  证据：`disabledExtensionsAreNotLoadedFromARefusingLoader` 和 `disabledMissingClassStillStarts` 在 18 个生命周期测试里面。禁用名单上的实现类没有被 `Class.forName`。`enabled(...)` 只在类已经加载之后调用，所以它自己避免不了加载。

  边界：验收的是禁用实现类、不连接、不扫描。没有从 Web 的父 classpath 上删掉 JPA、JAXB、activation 或 SLF4J 的 API jar，审计仍然解析它们。RefusingLoader 只拒绝子加载器上的 ORM/Mongo 实现类名，而且当时 HTTP、Thrift、Dubbo 都是关的。

- [x] **T10 · P1：把全局状态收归应用上下文。** `EnhancementContext` 和 `ApplicationContext` 拥有 ClassPool、模型、Guice 模块、增强记录和关闭动作。`ServiceFramwork` 上的静态字段只别名默认应用。两个独立加载器互不改写。关闭后可以再启动一个新加载器。同一个活着的加载器不能再定义。

  证据：`ApplicationLifecycleTest`、`OrmContextIsolationTest` 7/7、`DefaultContextCloseTest`、`MongoEnhancementLiveTest`。显式禁用或空的上下文不会借另一个应用的池。没有 scope 时才回到默认应用。

  边界：弱引用测试观察到加载器可以被回收。阶段 JSON 里的 Metaspace 不是卸载证明。没有热替换。

- [x] **T11 · P1：确定 ActiveORM 的单一维护主线。** 权威实现是本仓库的 `serviceframework-orm`。框架内的 ORM 用法和“把同一个框架 ORM artifact 单独放上 classpath”共用这一份回归契约。依赖审计拒绝两份同名 `net.csdn.jpa` class。

  证据：Web classpath 上的 `net/csdn/jpa` 实现是安装好的 `serviceframework-orm_2.13-2.0.9.jar`。ORM 模块的 `target/classes` 和 `target/test-classes` 类名不相交，`duplicate_key_classes` 为 0。没有独立 ActiveORM artifact。

  边界：外部 `active_orm` 仓库没有移植，也没有在这次矩阵里测试。

- [x] **T12 · P1：补足增强诊断和成本指标。** 打开诊断时记录原始字节摘要、规则、版本、加载器、方法变化、耗时和失败阶段。诊断关闭且没有 `EnhancementObserver` 时，这些计数保持 0。只关诊断、另有观察器时，原始字节仍会交给观察器，但 hash、字节读取计数、方法检查和写盘仍是 0。`applyReason` / `skipReason` 只在诊断打开时调用。class 文件和生成源码都要另开开关。报告写到调用方给的目录。

  证据：冷启动和重复启动的 JSON 是 `diagnostics: off`，并且 `observer` 为 false。这组基线没有观察器。基准会在 hash、字节读取、方法检查或写盘不为 0 时中止。那组 0 是零捕获，不是“有观察器也不会读类字节”。两个 JDK 的冒烟写出真实的增强前后 class，没有 `sources/`。`WebRecord`、`WebNote`、`BothController` 的六个哈希跨 JDK 一致。`report.txt` 没有 `password=`、`jdbc:` 或 `SF_COMPAT`。schema 摘要在 `ALTER` 之后改变，调用方修订 `config-20260924` 保留。说明在 [enhancement-diagnostics.md](enhancement-diagnostics.md)。

- [x] **T13 · P2：提供编译器和 IDE 可见的生成 API。** 构建期生成同包的 `模型名Queries`，不往模型类里插方法。处理器要显式 `-processor net.csdn.jpa.query.ServiceFrameworkQueryProcessor`。IDE 把 `-s` 目录标成 generated sources。

  证据：`QueryApiRegressionTest` 11/11，含 Java 调用方、Scala 调用方和非法声明。没有因为缺源码树而跳过。见 [generated-query-api.md](generated-query-api.md)。

- [x] **T14 · P2：按声明扩展查询。** 已落地的是有界 AND、排序和分页，字段、运算符和参数在编译期检查，运行期再复核。显式 `EntityManager` 和当前上下文两种重载都有。错误声明让编译失败。

  证据：同一套 `QueryApiRegressionTest`。这次运行不需要全局的所有权覆盖。

  边界：没有 DTO、任意 JPQL，或枚举全部字段组合。

- [x] **T15 · P2：根据测量处理热点。** 验收的是测出冷启动、重复启动和热调用，扫描的逻辑流峰值在更早的夹具里从 25 降到 1，以及没有引入增强结果缓存。原条目里列出的模式快照缓存、调用入口缓存和直接访问器没有做。依赖或模式变化不会碰到一份不存在的旧增强缓存。

  证据：`dev/measure-framework-phases.sh --cold 3 --warmup 1 --repeated 5 --hot 20` 退出码 0。JDK 8 冷启动墙钟 p50 4360502596 纳秒，JDK 17 是 3835738867 纳秒。这是热的操作系统和热的数据库，n 很小。扫描 25→1 的记录在 `/tmp/sf-t12-bench-verified/enhancement-costs.json`，这次矩阵没有重测，`startupSpeedupClaimed` 是 false。数字和限制在迁移说明里。

**和当初草案不同的地方**

类定义不是一份在两个 JDK 上都调用的四参数示意。Java 8 运行时只调用 `CtClass.toClass(ClassLoader, ProtectionDomain)`；更近的 JDK 才把锚点当作 neighbor 传给 `ClassPool.toClass`。锚点和“拒绝命名模块”在两条路径之前都执行。实现见 `ClassDefiner`，不要把更早的探针片段当成现在的代码。

扩展契约已经是 `EnhancementRule`、`EnhancementContext`、`EnhancementPlan`、`ClassDefiner` 和 `FrameworkExtension`。安装方式是启动前 `addExtension`、配置类名，或各模块的 `addEnhancementRule` / `registerRule`。生效方式是重新启动。没有热替换。

更早的 common-only 500 测试和 Javassist 3.30.2 对照，只作为历史基线写在迁移说明的最后一节。当前全框架数字是每个 JDK 1212，不是 500，也不是手册里更早的 1032。

**源码索引**

- 父 POM：[pom.xml](../pom.xml)
- 类定义：[ClassDefiner](../serviceframework-common/src/main/java/net/csdn/common/enhancer/ClassDefiner.java)、[DynamicBytecode](../serviceframework-common/src/main/java/net/csdn/common/enhancer/DynamicBytecode.java)
- ORM：[JPA](../serviceframework-orm/src/main/java/net/csdn/jpa/JPA.java)、[JPAEnhancer](../serviceframework-orm/src/main/java/net/csdn/jpa/enhancer/JPAEnhancer.java)、[EntityEnhancer](../serviceframework-orm/src/main/java/net/csdn/jpa/enhancer/EntityEnhancer.java)、[ClassMethodEnhancer](../serviceframework-orm/src/main/java/net/csdn/jpa/enhancer/ClassMethodEnhancer.java)、[ModelClass](../serviceframework-orm/src/main/java/net/csdn/jpa/enhancer/ModelClass.java)
- 启动：[Bootstrap](../serviceframework-web/src/main/java/net/csdn/bootstrap/Bootstrap.java)、[ApplicationContext](../serviceframework-web/src/main/java/net/csdn/bootstrap/ApplicationContext.java)、[ModuelLoader](../serviceframework-web/src/main/java/net/csdn/bootstrap/loader/impl/ModuelLoader.java)、[ServiceFramwork](../serviceframework-web/src/main/java/net/csdn/ServiceFramwork.java)
- Mongo：[MongoEnhancer](../serviceframework-mongo/src/main/java/net/csdn/mongo/enhancer/MongoEnhancer.java)
- Controller：[FilterEnhancer](../serviceframework-web/src/main/java/net/csdn/filter/FilterEnhancer.java)
- 扫描：[DefaultScanService](../serviceframework-common/src/main/java/net/csdn/common/scan/DefaultScanService.java)。它调用依赖里的 `tech.mlsql.common.utils.reflect.ClassPath`，那个源码不在本仓库。
- Dispatcher：[StrategyDispatcher](../serviceframework-dispatcher/src/main/java/serviceframework/dispatcher/StrategyDispatcher.scala)、[ServiceInj](../serviceframework-dispatcher/src/main/java/serviceframework/dispatcher/ServiceInj.scala)
- 参数化测试：[DynamicBytecodeConventionTest](../serviceframework-common/src/test/java/net/csdn/common/enhancer/DynamicBytecodeConventionTest.java)、[DynamicJpaFinderBytecodeTest](../serviceframework-orm/src/test/java/net/csdn/jpa/enhancer/DynamicJpaFinderBytecodeTest.java)、[DynamicMongoFinderBytecodeTest](../serviceframework-mongo/src/test/java/net/csdn/mongo/enhancer/DynamicMongoFinderBytecodeTest.java)
