**ServiceFramework 字节码增强与扩展性优化 TODO：同时兼容 JDK 8 / JDK 17**

日期：2026-09-24。状态：源码评估与实施清单，尚未实施产品改造。

建议先完成“正确生成、可靠加载、真实调用”的基础，再统一增强规则与模块扩展入口，最后增加类型可见的查询能力和性能优化。兼容目标是：同一套业务增强逻辑、同一版本的 Java 8 基线产物，能够在 JDK 8 和 JDK 17 上运行，并分别通过实际启动和业务测试。

**范围与当前事实**

本文的 ORM 指 ServiceFramework 内的 ActiveORM/JPA 实现及其独立同源仓库 `active_orm`。评估覆盖整个 ServiceFramework，但优化重点是字节码增强、扫描与类加载，以及直接影响这些能力的依赖和运行生命周期。

| 范围 | 当前实现 | 本次重点 |
| --- | --- | --- |
| `serviceframework-common` | `DynamicBytecode`、扫描、反射、配置及公共工具 | 正确性、统一类定义入口、元数据与诊断 |
| `serviceframework-orm` | ActiveORM、JPA/Hibernate、实体/静态查询/关联增强 | 多级继承、属性映射、查询行为、事务与注册身份 |
| `serviceframework-mongo` | MongoMongo、Document、Criteria、setter 与 finder 增强 | 自定义 setter、alias、继承、实际读写与查询 |
| `serviceframework-web` | Bootstrap、Controller/Filter、Service/Util、Guice、HTTP/RPC | 启动顺序、必需增强失败、扩展注册与关闭 |
| `serviceframework-dispatcher` | Strategy、Processor、Compositor 的配置与反射实例化 | 扩展类型校验、实例生命周期、错误定位 |
| `serviceframework-jetty-9-server` | Jetty 服务容器 | 双 JDK 启停及 HTTP 集成验证 |
| 独立 `active_orm` 仓库 | 与框架内 ORM 同源，若干同包同名实现已经不同 | 确立维护主线，减少两份实现长期分叉 |
| 实际依赖 | Javassist、Hibernate/JPA、Guice、数据库驱动、Jetty、Scala、common-utils 等 | 依赖版本收敛、Java 8 API 基线、JDK 17 实际运行 |

当前 `serviceframework-orm/pom.xml` 没有把独立 `ActiveORM` artifact 作为依赖；相关实现直接存在于框架模块中。抽查的 `JPA`、`JPAEnhancer`、`ClassMethodEnhancer`、`EntityEnhancer`、`Model` 与独立仓库均有差异。因此，修复默认先落在框架实际使用的代码上，再明确独立仓库如何同步。

`common-utils` 在本框架中的直接调用主要是集合、字符串/配置辅助、缓存和 classpath 扫描。本次检索未发现框架调用其 Java/Scala 动态编译器，优化清单只覆盖实际使用的部分。

**从哪里开始：第一批必须完成的 TODO**

以下均为待办，勾选条件是对应验收通过；文档中的方案和已有小探针不能代替完成状态。

- [ ] **T01 · P0：定义支持矩阵，建立双 JDK 验收入口。** 从父 POM 和现有三组字节码测试开始。公共 Java/Scala 代码及依赖按 Java 8 可运行基线约束；增加 JDK 8、JDK 17 的独立测试进程，并验证同一批发布 JAR 在两个运行时上工作。JDK 17 编译 Java 时使用 `--release 8` 或等效的 API 基线检查，不能只看 `source/target=1.8`。验收：两个 JDK 都有真实生成、加载和调用结果，记录实际运行参数、依赖版本与产物摘要。默认 Scala 2.13 组合先纳入验收，旧 Scala profiles 分别列出支持状态。[S1][S9]

- [ ] **T02 · P0：收敛字节码及启动依赖。** 将 Javassist `3.30.2-GA` 作为本轮升级候选基线，并统一管理全模块使用的版本。审计 Guice 与 Mycila Guice 的同名类冲突，以及 Hibernate 代理、JPA API、Jetty、驱动和公共依赖的实际解析结果。当前直接声明包含 Javassist `3.23.1-GA`、Guice `3.0`/Mycila `3.0-20100927`、Hibernate `5.3.7.Final`、Jetty `9.2.16.v20160414`。这些是待审计的声明版本，不代表每项都已实测失败。验收：依赖树可解释、关键类唯一、JDK 8 可见类及 API 满足基线，保留当前 `javax.persistence` 等业务接口边界。[S1][S2]

- [ ] **T03 · P0：把所有定义类的操作收口到一个入口。** 在 common 中设计 `ClassDefiner`，明确目标类名、目标加载器、同包锚点、ProtectionDomain 与错误信息。迁移 ORM、Mongo、Controller/Filter、Service、Util、Application 和测试启动代码中的分散 `toClass()`。JDK 17 路径使用合法的同包锚点/Lookup；JDK 8 走兼容路径。验收：主力 classpath 部署不依赖全局 `--add-opens`，生成类落在预期加载器中，同包访问与框架类型转换均成功；锚点或访问条件不满足时明确失败。[S3][S4][S5][S6]

- [ ] **T04 · P0：修复已复现的字节码语义问题。** `DynamicBytecode` 替换 setter 时按精确方法签名匹配，保留其他重载；生成 getter/setter 前检查完整继承关系及 final/static/返回类型冲突；跨类复制属性时转换目标常量池。另明确静态字段初值、初始化代码与元数据容器是共享还是每个模型独立，不能认为复制字段声明就完成了初始化。验收：已有重载、继承 final、静态常量反例转为正确行为；自定义 setter 的校验/副作用和模型间元数据独立性有实际调用断言。[S2][S5]

- [ ] **T05 · P0：修正扫描、继承遍历和模型身份。** 核查并修复 `DefaultScanService.scanArchives(String)` 的自身递归、URL 协议用 `==` 比较、资源读取失败处理；修正 `ModelClass` 中把 CtClass 实现类名当模型名比较，以及循环不向更上层父类推进的问题。JPA 注册当前按 `simpleName` 存储，应改为无歧义身份，并为旧短名称保留唯一时才生效的别名。验收：目录/JAR/带空格路径扫描一致，三级以上继承及字段遮蔽正确，两个包内同名模型不会静默覆盖。[S3][S7]

- [ ] **T06 · P0：让必需增强失败准确中止启动。** 扫描、ORM 加载、Controller 增强中的异常不再只打印后继续注册。将错误分为可选模块未启用、配置错误、增强冲突、类定义失败等明确类别；必需模型和控制器必须完整就绪后再启动服务。验收：故意制造 final 冲突、缺失依赖、错误关联或非法字节码时，启动给出类名、规则、阶段和原因，HTTP/RPC 不以半成品状态开始服务；失败清理关闭已创建资源。[S3][S4][S6]

- [ ] **T07 · P0：补真实应用和数据库验收。** 现有参数化测试继续验证命名、签名和生成片段，同时增加加载后调用及真实业务链路。验收至少包括：ORM 保存/查询/事务回滚/关联/继承；Mongo 的 alias、setter、自定义逻辑及读写查询；Controller 的 before/after/around 顺序和异常路径；Service/Util 注入；Jetty 启停；dispatcher 的策略加载。每个支持的运行配置都在 JDK 8/17 上跑，覆盖启用与关闭可选模块。轻量数据库可做快速测试，最终结论需要实际支持的数据库/驱动组合。[S4][S5][S6][S9]

建议实际开工顺序：**T01 验收骨架 → T02/T03 依赖与加载入口 → T04/T05 正确性 → T06/T07 完整启动与业务验收**。第一批完成标准是“同一应用产物在两种 JDK 上正确工作”，而不是测试数量增加或 Maven 编译成功。

**第二批：让框架能够稳定扩展**

- [ ] **T08 · P1：增加增强规则 SPI 与执行计划。** 将字段/访问器、ORM 实体、ORM 查询、关联、Mongo、Controller 规则分开注册，共用分析与冲突检查。规则声明 ID、版本、适用对象和前后依赖，顺序稳定，循环依赖和相互冲突在类定义前发现。验收：新增规则只需提供扩展实现和注册信息；同一规则重复执行有明确的跳过/冲突结果；业务规则不直接调用 `toClass()`。[S2][S3][S5][S6]

- [ ] **T09 · P1：统一模块注册和启停契约。** 在已有 `registerModule`、Guice Module、`type_mapping`、验证器配置和 dispatcher 接口之上增加模块描述及生命周期。模块声明所需能力、提供能力、配置检查、注册、启动和关闭；核心负责依赖顺序及反向关闭。验收：增加验证器、查询扩展或数据库适配器时，主体改动位于扩展模块；未启用的模块不要求其实现类在运行 classpath 上可用，错误配置在启动前可定位。[S4][S10]

- [ ] **T10 · P1：把全局状态收归应用上下文。** 将 ClassPool、模型树、模型注册表、Guice 模块列表、增强记录与资源关闭动作纳入有明确所有者的 `ApplicationContext`/`EnhancementContext`。重点处理 `ModelClass.ROOTS`、`CTModelClasses`、`JPA.models` 和 `ServiceFramwork` 的静态集合。先支持单应用正确启停，再验证两个独立应用上下文；现有静态 API 可通过默认上下文逐步兼容。验收：重复初始化不累积旧模型或模块，关闭后释放连接/线程/上下文引用；若采用独立应用加载器，释放句柄后再检查加载器回收及 Metaspace 走势。[S3][S7][S10]

- [ ] **T11 · P1：确定 ActiveORM 的单一维护主线。** 先以框架内实际使用版本修复和建立测试，再决定把共用 ORM 核心抽成可独立发布的模块，或让独立仓库按明确版本同步。当前独立仓库仍声明 Java 6 编译目标与更老的 Hibernate，不能直接当成已满足本轮兼容目标的替代品。验收：增强实现有一个权威来源，两种使用方式共享回归契约，依赖检查阻止同包同名的两份 ORM 实现同时进入同一 classpath。[S11]

- [ ] **T12 · P1：补足增强诊断和成本指标。** 为每个类记录原始字节摘要、规则/配置/模式元数据版本、加载器归属、生成方法数量、耗时、跳过或失败原因。按需输出增强前后差异、生成源码片段或 class 文件，报告存于应用自己的诊断目录。验收：一次启动能查明某个方法由哪条规则生成、为什么未生成、失败在哪个阶段；关闭诊断时开销可测且受控。

**第三批：增加业务开发能力并优化性能**

- [ ] **T13 · P2：提供编译器和 IDE 可见的生成 API。** 优先根据模型/查询元数据在构建期生成伴生查询类或 Repository 接口，例如 `OrderQueries.findByStatus(...)`；类型信息明确，运行时生成与构建期生成共用规则。注解处理器适合生成新源文件，不能直接视为给原 Model 原地添加方法的机制；若需要原 Model 上的静态生成方法，必须设计业务调用方编译之前的构建阶段。验收：普通 Java/Scala 业务源码可直接编译调用，IDE 可补全，运行结果与声明一致。

- [ ] **T14 · P2：按声明扩展查询与映射能力。** 先支持实际需要的组合条件、排序/分页、DTO 映射或自定义校验。使用显式查询声明或有限命名规则，检查字段/类型/运算符并绑定查询参数。限制单类生成的方法数量与字节码体积，避免枚举所有字段组合。验收：一组真实业务查询能减少重复代码，结果与手写实现相同，错误声明在启动或构建时准确报错。

- [ ] **T15 · P2：根据测量优化热点。** 先分别测 classpath 扫描、数据库元数据读取、增强、类定义、容器初始化和业务调用。按结果选择复用扫描/模式快照、减少重复生成、缓存已解析调用入口或生成直接访问器。缓存键包含原始类、规则与配置版本、数据库模式摘要和目标环境，并限定所属应用上下文。验收：报告冷启动、重复启动、热调用及内存相对基线的变化；依赖或模式变化不会误用旧产物。

**JDK 8 / JDK 17 如何同时支持**

公共接口和业务增强规则保持 Java 8 基线，把类定义操作集中到适配层。Scala 二进制版本仍按自己的 artifact 区分；“同时兼容两个 JDK”不等于 Scala 2.11/2.12/2.13 共用一个二进制包。

本次会话已核对 Javassist `3.30.2-GA`：发行包的 426 个普通 class 均为版本 52，官方加载实现保留旧 JDK 路径，并在较新 JDK 上利用同包锚点取得 Lookup。它可作为本轮候选基线；依赖升级与加载入口迁移必须一起验收。[官方对应版本实现](https://github.com/jboss-javassist/javassist/blob/rel_3_30_2_ga/src/main/javassist/util/proxy/DefineClassHelper.java)

同包锚点方案使用的是四参数 `ClassPool.toClass` 入口，示意如下：

```java
Class<?> defined = pool.toClass(
    enhancedClass,
    packageAnchor,
    packageAnchor.getClassLoader(),
    packageAnchor.getProtectionDomain()
);
```

`packageAnchor` 必须是目标加载器内、与目标类同包的另一个已加载类。不能为获取锚点而先加载待增强目标本身。可以由应用 starter 或构建工具提供每个受增强包的锚点；获取不到合法锚点时明确诊断，并采用预先设计的构建期增强或应用加载器方案。JDK 17 还必须满足对应模块访问条件；不能把 `toClass(neighbor)`、`toClass(Lookup)` 单独视为在 JDK 8 上也能无条件直接执行的 API。

本次会话的独立探针已验证：该四参数入口在 JDK 17.0.20.1 上加载 Java 8 格式生成类，调用同包非 public 方法得到 42，加载器身份正确，未使用额外模块开放参数。这验证了一个加载方案；本次尚未运行 JDK 8 实机探针，也没有据此宣称整个框架已通过双 JDK 验收。

| 验收层次 | JDK 8 | JDK 17 | 通过条件 |
| --- | --- | --- | --- |
| 构建与依赖 | 用 Java 8 API 基线检查并运行构建/测试 | 编译时限制 Java 8 API，检查完整解析依赖 | 产物及实际可见依赖满足 Java 8 基线；正确处理 multi-release JAR 与 module-info |
| 相同产物运行 | 加载并调用 | 加载并调用 | 使用同一批发布 JAR，生成行为及结果一致 |
| 字节码边界 | 重载、final、多级继承、注解、泛型、初始值、重复增强 | 同左，另检查模块访问和加载器归属 | 实际 JVM 校验与调用断言通过 |
| 打包与扫描 | 目录及普通 JAR | 目录及普通 JAR | 扫描集合、映射和生成结果一致；嵌套 JAR 等额外包装形式单独声明支持 |
| ORM/Mongo/Web | 实际支持的数据库与启动配置 | 同一组业务场景 | 事务、关联、alias、过滤器、依赖注入及服务器启停正确 |
| 扩展和生命周期 | 新增规则/模块、重复初始化、关闭 | 同左 | 不重复定义类，不残留旧上下文，失败状态可诊断 |

首批支持口径以当前 classpath 部署方式为准。JPMS module-path 部署若要支持，应另加模块可读性/开放性用例。当前 Scala 2.11/2.12 profiles 使用较老版本，需要各自的工具链与依赖核验，不能由默认 Scala 2.13 测试通过推断它们也通过。

**扩展机制具体怎样设计**

建议沿着现有增强器逐步增加以下契约；这些是拟新增的设计名称，尚未实现。

| 契约 | 负责什么 | 应保持的边界 |
| --- | --- | --- |
| `EnhancementRule` | 匹配元数据、声明需要的规则、提出字段/方法/注解修改 | 不定义 JVM 类，不注册运行实例 |
| `EnhancementContext` | 提供类层级、原始字节、配置/模式快照、ClassPool、加载策略与诊断 | 生命周期归一个应用上下文，规则之间不通过全局静态变量通信 |
| `EnhancementPlan` / `EnhancementResult` | 保存修改计划、冲突检查、规则版本、输入/输出摘要和诊断 | 顺序确定，同一输入可解释、可复核；执行前先检查完整计划 |
| `ClassDefiner` | 在指定环境中定义完成校验的类 | 统一处理 JDK 差异、包/模块条件和重复定义问题 |
| `FrameworkExtension` | 贡献规则、Guice 绑定、验证器、元数据/数据库适配器及启停钩子 | 先校验依赖，统一注册和关闭，保留已有配置接入方式 |

一条完整流程应为：

`扫描原始 class → 建立类型/关系与模式元数据 → 生成增强计划 → 检查冲突与顺序 → 生成并校验字节码 → 按依赖顺序定义类 → 注册 ORM/IOC/路由 → 启动服务`

这样，字段、查询、关联和 Controller 扩展都经过同一个计划与校验过程。新增规则可以通过显式注册或 Java SPI 发现；SPI 实现本身不得为了匹配对象而提前加载应用目标类。执行顺序采用依赖关系及稳定排序，缺少前置规则或存在环时在启动前失败。

例如，新增一类组合查询可以只提交查询声明、规则实现与构建期 API 生成器，共用已有 ORM/Mongo 元数据和加载入口；新增数据库适配则把当前 DBInfo 的元数据读取及 DBType 的类型映射整理成可替换契约，通过模块注册接入，保留现有 MySQL 路径的回归测试。

增强中新增字段、方法和关联结构，应在类首次定义前完成。第一阶段的扩展模式是“安装扩展后重启应用”。如果后续确实需要运行期切换，单独设计应用加载器与上下文替换、请求排空和旧引用释放，并重新验收类型共享边界。JVM 类定义不可回滚，因此生成计划可在定义前撤销，但定义阶段失败后不能声称已经恢复为原来的类。

**关键依据及验证边界**

已有独立复现确认了四类与本范围直接相关的问题：旧加载入口在 JDK 17 默认参数下失败；setter 的其他重载被删除；继承 final getter 导致类定义失败；字段属性跨常量池复制后，探针常量值变为 null。字段复制结论是工具层反例，尚未证明现有业务数据已损坏。

本轮源码复核还发现扫描自身递归、URL 字符串身份比较、继承遍历和模型短名称注册等具体风险，已列入 T05；这些尚未新增运行探针或数据库验收。静态 ClassPool 和注册集合说明需要明确生命周期，不能仅凭静态字段就宣称已发生内存泄漏；`CtClass.detach()` 也不等于 JVM 类已经卸载。

现有手册记载共 1032 条测试，其中新增 1020 条来自三组参数化字节码测试，主要覆盖命名、签名和生成片段。手册里的历史 JDK 8/17 测试记录保留为历史依据；本轮没有重跑整套测试，真实应用兼容结论按 T01/T07 的验收标准补齐。

本机工作区快照：ServiceFramework `ded2363`、active_orm `35e40ac`、common-utils `66d35ec`；存在工作区修改，HEAD 不等于全部被读源码。报告编写没有修改框架实现、依赖配置或运行服务。

**源码索引**

- **[S1] 模块和依赖声明：** [父 POM](/Users/williammacintel/projects/ServiceFramework/pom.xml:50)、[common POM](/Users/williammacintel/projects/ServiceFramework/serviceframework-common/pom.xml:46)、[ORM POM](/Users/williammacintel/projects/ServiceFramework/serviceframework-orm/pom.xml:16)。
- **[S2] 公共增强操作：** [DynamicBytecode](/Users/williammacintel/projects/ServiceFramework/serviceframework-common/src/main/java/net/csdn/common/enhancer/DynamicBytecode.java:152)。
- **[S3] ORM 增强与加载：** [JPAEnhancer](/Users/williammacintel/projects/ServiceFramework/serviceframework-orm/src/main/java/net/csdn/jpa/enhancer/JPAEnhancer.java:66)、[JPA.JPAModelLoader](/Users/williammacintel/projects/ServiceFramework/serviceframework-orm/src/main/java/net/csdn/jpa/JPA.java:354)、[EntityEnhancer](/Users/williammacintel/projects/ServiceFramework/serviceframework-orm/src/main/java/net/csdn/jpa/enhancer/EntityEnhancer.java:44)、[ClassMethodEnhancer](/Users/williammacintel/projects/ServiceFramework/serviceframework-orm/src/main/java/net/csdn/jpa/enhancer/ClassMethodEnhancer.java:73)。
- **[S4] 启动和 IOC：** [Bootstrap](/Users/williammacintel/projects/ServiceFramework/serviceframework-web/src/main/java/net/csdn/bootstrap/Bootstrap.java:62)、[ModuelLoader](/Users/williammacintel/projects/ServiceFramework/serviceframework-web/src/main/java/net/csdn/bootstrap/loader/impl/ModuelLoader.java:29)。
- **[S5] Mongo 增强：** [MongoEnhancer](/Users/williammacintel/projects/ServiceFramework/serviceframework-mongo/src/main/java/net/csdn/mongo/enhancer/MongoEnhancer.java:90)。
- **[S6] Controller 增强：** [FilterEnhancer](/Users/williammacintel/projects/ServiceFramework/serviceframework-web/src/main/java/net/csdn/filter/FilterEnhancer.java:39)。
- **[S7] 扫描和模型元数据：** [DefaultScanService](/Users/williammacintel/projects/ServiceFramework/serviceframework-common/src/main/java/net/csdn/common/scan/DefaultScanService.java:32)、[ModelClass](/Users/williammacintel/projects/ServiceFramework/serviceframework-orm/src/main/java/net/csdn/jpa/enhancer/ModelClass.java:25)。
- **[S8] 实际使用的扫描依赖：** [common-utils ClassPath](/Users/williammacintel/projects/common-utils/src/main/java/tech/mlsql/common/utils/reflect/ClassPath.java:434)。当前实现已有系统加载器的 `java.class.path` 路径；目录/JAR 包装及框架包装层仍需按 T05 验证。
- **[S9] 已有测试及历史说明：** [使用手册](/Users/williammacintel/projects/ServiceFramework/docs/ServiceFramework-Usage-Manual.md:378)、[common 测试](/Users/williammacintel/projects/ServiceFramework/serviceframework-common/src/test/java/net/csdn/common/enhancer/DynamicBytecodeConventionTest.java)、[ORM 测试](/Users/williammacintel/projects/ServiceFramework/serviceframework-orm/src/test/java/net/csdn/jpa/enhancer/DynamicJpaFinderBytecodeTest.java)、[Mongo 测试](/Users/williammacintel/projects/ServiceFramework/serviceframework-mongo/src/test/java/net/csdn/mongo/enhancer/DynamicMongoFinderBytecodeTest.java)。
- **[S10] 扩展注册与共享状态：** [ServiceFramwork](/Users/williammacintel/projects/ServiceFramework/serviceframework-web/src/main/java/net/csdn/ServiceFramwork.java:19)、[StrategyDispatcher](/Users/williammacintel/projects/ServiceFramework/serviceframework-dispatcher/src/main/java/serviceframework/dispatcher/StrategyDispatcher.scala:146)。
- **[S11] 独立 ORM 仓库：** [ActiveORM POM](/Users/williammacintel/projects/active_orm/pom.xml)、[ActiveORM JPA](/Users/williammacintel/projects/active_orm/src/main/java/net/csdn/jpa/JPA.java)。

第一批完成后再推进 T08–T12 的扩展基础，随后以 T13–T15 交付可编译调用的新能力及可测量的收益。上述 TODO 均保持未完成状态，直至对应源码改造与验收实际完成。
