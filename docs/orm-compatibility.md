# ORM 与 JDK 8 / JDK 17

本文是 `serviceframework-orm` 的加载、增强、元数据和 Quill 上下文。伴生查询的声明和处理器在 [generated-query-api.md](generated-query-api.md)。类定义走 `ClassDefiner` 和 Javassist 3.33.0-GA，不打开 `--add-opens`。整仓验收在 [serviceframework-bytecode-migration.md](serviceframework-bytecode-migration.md)。

## 配置怎么接

`JPA.configure(configuration)` 在没有当前上下文时自己打开一个 `EnhancementContext`，并把它当作默认上下文。调用方也可以先 `activate()`，或调用 `JPA.configure(configuration, context)` 把已有上下文传进来。传进来的上下文由调用方 `close()`。`JPA.shutdown()` 关闭默认上下文。

同一个上下文里，已经成功跑过的同一配置再调用 `configure` 不会重新增强。配置指纹包含模式、加载器身份、`application.model`、选中的 JDBC 引擎、主机、端口、库名、用户和口令摘要；PostgreSQL 还包含 schema。额外规则的 id 和版本也进指纹。口令不写进日志。

加载器里已经定义过模型之后，换一份配置不能假装热替换。这种情况抛出 `EnhancementFailure`，类别 `CONFLICT`，并要求新的应用 `ClassLoader`。JVM 已定义的类撤不掉。

`ModelClass` 的树、`JPA.models`、配置、`DBInfo`、`JPAConfig`、选中的 JDBC 连接池和 Quill 上下文都挂在当前 `EnhancementContext` 的 `OrmSession` 上。静态方法只在当前线程没有 scope 时回退到默认上下文。线程上已经 `activate()` 的上下文如果没有 ORM 会话，调用失败，不会借默认应用的配置、模型或连接池。关掉内层 scope 之后，外层 scope 或默认上下文按原来的栈恢复。显式传给 `JPA.configure(configuration, context)` 的上下文不会替换默认上下文。

主数据源由 `{mode}.datasources.primary` 选择，允许 `mysql` 和 `postgres`（`postgresql` 是选择器别名），缺省仍是 MySQL。`{mode}.datasources.mysql.*` 和 `{mode}.datasources.postgres.*` 都保留原结构，PostgreSQL 另有可选 `schema`（缺省 `public`）。`JdbcEngine` 是 common 里的共享选择器；`mysql.disable` 只关 MySQL 形态，不会让 `primary=postgres` 被误当成 MySQL 禁用。具体配置和测试命令见 [postgresql-support.md](postgresql-support.md)。

## 模型身份

注册主键是二进制名。`resolveModel(String)` 接受二进制名、`@Entity(name)` 和唯一的简单名。简单名或实体名对应多个类时抛出 `CONFLICT`，不会选第一个。`JPA.models.values()` 每个类只出现一次。

省略的 `@Entity.name` 会写成二进制名，避免两个包里的同名类在 JPQL 里撞车。显式的 `name` 会保留，但全库不能重复。物理表仍是 `@Table.name`，没有注解时用简单名的下划线形式。

`where` / `in` / `select` / `order` / `limit` / `offset` 生成的 JPQL 使用这个实体名。`JPQL` 再按 `resolveModel` 找到类，用 `Metamodel.entity(Class)` 取列，不按简单名扫描后取第一条。

## 增强

`OrmEnhancer` 编译一个 `EnhancementPlan`：`entity-mapping`，然后依赖它的 `orm-query` 和 `association`。额外规则用 `CSDNORMConfiguration.addEnhancementRule` 注册。重复 id、未知依赖和环在 `define` 之前失败。

实体映射是整棵模型树的一次操作，在第一个目标上执行。后面的目标如果属于这棵树就不再改一遍；不属于这棵树则是冲突。规则本身不调用 `toClass`。全部 `apply` 成功之后，按父类在前、子类在后 `EnhancementContext.define`。锚点是目标包里已经编译好的 `ServiceFrameworkPackageAnchor`，保护域来自这个锚点，不用 `JPA.class` 去填另一个加载器。

直接父子关系只看父类的二进制名，不再用传递的 `subclassOf` 把孙子挂到祖父上。字段遍历会沿父类一直走到 `Model` 为止，不把 `Model` 自己的字段算进来，子类声明的同名字段遮蔽父类，循环会失败。这些步骤不把目标类定义进 JVM。

实例字段复制用 `new CtField(source, target)`，注解进目标常量池。`parent$_` 静态字段仍由 `DynamicBytecode.copyStaticFields` / `copyStaticMethods` 复制，每个模型自己懒初始化，不复制 `<clinit>`。

给已有字段加注解时，按最近的声明字段查找。祖先链上某一个类没有这个字段，不再被当成整条链都没有。`MemberValue` 使用该字段真正 `declaringClass` 的常量池。

## 启动失败和库元数据

扫描、增强、定义、校验器加载和 `EntityManagerFactory` 创建失败都会抛出 `EnhancementFailure`，带上类名、规则、阶段和 cause。不再把异常打印后继续用旧的 `toClass`。

`JPA.getJPAConfig()` 不再往 `classLoader.getResource(".")` 写 `persistence.xml`。它用已注册的 `@Entity` 类和 `PersistenceUnitInfo` 做 Hibernate 容器引导。原来的 `new JPAConfig(Map, String)` 仍走 `Persistence.createEntityManagerFactory`，给已经准备好 `persistence.xml` 的调用方。

`DBInfo.refresh()` 用 `DatabaseMetaData.getTables` 和一次 `getColumns` 读取当前 catalog，并用 try-with-resources 关闭连接和结果集。快照整体替换，不把表名越积越多。快照身份包含主机、端口、配置的库名和连接上的 catalog；PostgreSQL 还记录选中 schema。对不上就拒绝使用。列类型按当前引擎规范：MySQL 仍把 `INTEGER` / `INT UNSIGNED` 记成 `INT`，PostgreSQL 会把 `uuid`、`numeric(10,2)`、`timestamp with time zone` 等规范成 `UUID`、`NUMERIC`、`TIMESTAMPTZ`，交给 `PostgresType` 映射。标识符如果要拼进 SQL，走 `DBInfo.quoteIdentifier`；MySQL 用反引号，PostgreSQL 用双引号并加倍内部引号。

`DBInfo.schemaSnapshot()` 是这份快照的规范文本，不是连接身份。第一行是 `db-schema v1`，然后按表名排序，每张表一行 `table 表名`，其下列按列名排序，一行 `column 列名 TYPE`。没有主机、端口、库名、用户、口令或 JDBC URL。`schemaDigest()` 是这段文本的 SHA-256 小写十六进制，每次调用都重新哈希，不缓存；`schemaDigestComputations()` 因此会增加。`refresh()` 换掉快照之后，下一次摘要跟着变，列元数据也是新的。诊断关闭时，`configure` 不会去算这枚摘要。调用方自己要摘要时直接调 `schemaDigest()`，这和诊断开关无关。

## 诊断

默认关闭。`JPA.configure(configuration)` 不传诊断时，仍用 `EnhancementContext.open(loader)`。这条 context 没有观察器，所以不哈希、不读类字节、不对比方法、不写报告，也不调用 `schemaDigest()`。若调用方传入的 context 上已经有 `EnhancementObserver`，诊断仍关闭时会把原始字节交给观察器，hash、方法对比、报告和 schema 摘要计数仍是 0。下面两种打开方式用的是同一个 `EnhancementDiagnostics` 实例。源码和 class 转储默认都关，ORM 不会替调用方打开。

```java
EnhancementDiagnostics diagnostics = EnhancementDiagnostics.enabled(reportDir);
// diagnostics.setEmitGeneratedSource(true);
// diagnostics.setEmitClassFiles(true);

// 调用方已经打开 context。配置不拥有它，调用方 close。
EnhancementContext shared = EnhancementContext.open(anchor.getClassLoader(), diagnostics);
JPA.configure(new JPA.CSDNORMConfiguration(mode, settings, anchor), shared);

// 或者让 configure 自己创建并拥有 context。失败时这个 context 会被关掉，报告在 close 时 flush。
JPA.configure(new JPA.CSDNORMConfiguration(mode, settings, anchor)
        .enhancementDiagnostics(diagnostics));
```

两个入口同时给、但不是同一个 diagnostics 对象时，`configure` 在建库元数据之前抛 `CONFIGURATION`，文案是 `enhancement context and diagnostics disagree`。`enhancementDiagnostics(null)` 直接拒绝。这里不接受 `Settings` 当诊断参数。

`orm-1`（`DBInfo.DIAGNOSTICS_VERSION`）是数据库快照文本的格式，不是应用配置修订。摘要是增强当时那份数据库快照的 `schemaDigest()`，不是配置指纹，也不是 JDBC URL。事件在写入时复制当时的 `configVersion` 和 `schemaDigest`。ORM 在自己的 `apply` / `define` 之前写入这份摘要；如果调用方已经给了修订，事件保留那个修订，只有调用方什么都没给时才写 `orm-1`。增强一结束，如果进来之前已经有修订，就连同原来的摘要写回去。更早的事件不会被改掉；共享 context 上随后的事件也不会一直带着 ORM 的摘要。

`EntityMappingRule` 第一次 `apply` 会改整棵模型树。它在这一次之前通过 `affectedClasses` 返回全部 `roots().hierarchy()` 的 `originClass`（按类名去重，并包含入口类）。父类优先的循环没有改。因此父类和子类的 `originalSha256` 都是第一次修改之前的类字节，子类不会在整树改完之后才被取样。后面的类看到映射已经做过，`affectedClasses` 只返回当前类，原因是 `entity mapping already includes this class`，这次不再产生方法差异。`orm-query` 和 `association` 仍只改当前类。

非叶子模型不会收到查询方法。跳过原因是 `query methods are added only on leaf models`。用户自己写的 getter / setter 不在 `added` / `rewritten` / `removed` 里。`@ManyToOne` 的外键列（例如 `album_id`）仍在 schema 快照里，但不会再生成 `getAlbumId`，因为映射规则把这个列排除掉了。

方法从哪来，看这次增强的真实签名，不看另一条玩具规则。一次 `DiagPhoto` / `DiagAsset` / `DiagAlbum` 增强里能对上的例子：

| 类 | 签名 | 结果 | 规则 |
| --- | --- | --- | --- |
| `DiagPhoto` | `getId()Ljava/lang/Integer;` | `added` | `entity-mapping` |
| `DiagPhoto` | `findById(Ljava/lang/Object;)Lnet/csdn/jpa/model/JPABase;` | `added` | `orm-query` |
| `DiagPhoto` | `album(Lnet/csdn/jpa/ormdiag/DiagAlbum;)Lnet/csdn/jpa/ormdiag/DiagPhoto;` | `added` | `association` |
| `DiagAlbum` | `photos()Lnet/csdn/jpa/association/Association;` | `added` | `association` |
| `DiagAsset` | `getRootTag()Ljava/lang/String;` | 不在变更列表里，自定义 getter 保持原样 | |
| `DiagPhoto` | `getName()Ljava/lang/String;` 和两个 `setName` | 不在变更列表里 | |
| `DiagAsset` | `orm-query` | `phase=skip`，原因是只给叶子加查询方法 | |

父类 `FailBase` 上的 `final getNote()` 挡住子类字段 `note` 的访问器时，抛出的异常是 `CONFLICT`，`phase=enhance`，`rule=bean-accessor`，类名是 `FailLocked`。诊断事件记在这次 `apply` 的入口类 `FailBase` 上，`rule=entity-mapping`，`phase=apply`，failure 文本里带有子类名、`phase=enhance`、`rule=bean-accessor` 和 `final`。这次 `configure` 拥有的 context 会关掉并 `flush`。类没有被定义。报告在 `/tmp/sf-orm-diagnostics-report/failure-<java.specification.version>/report.txt`，正常增强在同目录的 `normal-<版本>/report.txt`。版本号里的点会换成下划线。两份报告都不含口令、`jdbc:` 或 `password=`。源码目录和 class 目录默认不写。

打开诊断和关闭诊断走同一套保存、查询和回滚。关闭时 `hashComputations`、`bytecodeReads`、`methodInspections`、`diskWrites` 和 `schemaDigestComputations` 都是 0，直到调用方自己要 `schemaDigest()`。

校验器实现仍然放在 `JPABase.validateParses` 这个静态列表里，重复 `configure` 不会把同一个解析器再加一遍。它没有按上下文拆开。

`JPABase.mysqlClient` 是指向当前 `OrmSession` 的桥，类初始化时不建池、不连数据库，并且只在 `primary=mysql` 时可用。PostgreSQL 主数据源走 `JPABase.postgresClient()` 或 `OrmSession.current().postgresClient()`；`MysqlClient` 这个历史类名只是 JDBC 客户端包装，不代表实际引擎。`new MysqlClient(DataSource)` 仍然只使用调用方传入的数据源。每个应用的 Druid 池由自己的 `DataSourceManager` 按创建顺序持有；中途创建失败会关掉已经打开的池。Quill 拿到的是不关闭底层池的包装。关闭上下文时按创建的逆序先关 Quill 和 `EntityManager`，再关本上下文的池和 `EntityManagerFactory`。同一个池对象只关一次。主数据源被禁用的上下文不会打开池，也不会使用另一个上下文的池。

`JPAConfig` 只登记还没关闭的 `EntityManager`。`closeTx` 和 `close` 都会把它拿掉，关闭失败也同样拿掉，不再把已经结束的请求留到进程退出。`JPAContext.close` 会把自己的 `EntityManager` 字段清掉，别的线程上残留的 `ThreadLocal` 因此不会一直握着已经关闭的 Hibernate 会话。`em()` 返回的是包装。对这个包装调用 `close()`，或者 `unwrap(Session.class)` 之后再 `close()`，都会先回滚还开着的事务，再注销并关闭真正的会话。Hibernate 在 JPA 引导下如果事务还没结束就 `close()`，只会把会话标成等待自动关闭，JDBC 连接继续算作借出；先回滚再关，连接才会回到池的可用队列。再往下 unwrap 到 Hibernate 实现类不在这个契约里。`shutdown` 仍会关闭别的线程上还开着的会话；某一个 `close` 失败不会挡住其余资源，第一个异常抛出，其余挂在 suppressed 上。

建池失败时抛出的消息，以及 `JPA.properties` 打出的 `connect url`，只保留 `jdbc:mysql://主机:端口/库名` 或 `jdbc:postgresql://主机:端口/库名`。查询串和 userinfo 不进这两处诊断，所以 `jdbc.*` 里的口令不会写出来。真正交给驱动的 URL 仍带着这些参数。

Quill 经 scala-logging 使用 SLF4J。scala-logging 传递依赖是 `slf4j-api` 1.7.26，ORM 直接依赖 1.7.32，把 API 定在这一版，并且不带绑定。没有绑定时 `LoggerFactory` 退回 NOP，上下文照样能建。应用自己选绑定。

`QuillDB.ctx` 和 `QuillDB.postgresCtx` 都是方法，不是进程级 `lazy val`，这样两个应用不会共用一个上下文。`ctx` 仍要求 `primary=mysql` 并返回 `MysqlJdbcContext`；`postgresCtx` 要求 `primary=postgres` 并返回 `PostgresJdbcContext`。Scala 不能写 `import QuillDB.ctx._`；支持的写法是 `val ctx = QuillDB.postgresCtx`，然后 `import ctx._`。命名 PostgreSQL 池配置在 `{mode}.datasources.multi-postgres.<name>`，对应 `QuillDB.createNewPostgresCtxByNameFromYml(<name>)`；字符串入口是 `createNewPostgresCtxByNameFromStr`。`createNewCtxByNameFromStr` 保留原来的 MySQL 语义，在当前增强上下文里没有 JPA 实体、也没有 Hibernate 时，仍能按 snippet 自己建池。这个池归当前上下文关闭，不会去借另一个应用的池。调用发生时如果既没有 scope，也没有 ORM 会话，池归一个 compat 上下文，只能通过 `QuillDB.close()` 关掉。关掉之后再按同一个名字取，得到的是新池，旧池不会被交回来。`createNewCtxByNameFromYml` 和 `createNewPostgresCtxByNameFromYml` 都在，读取当前上下文或这个 compat 上下文里已经建好的连接，不会自己再开一个全局池。

`JPAConfig.shutdown()` 关掉这个配置还开着的 `EntityManager`（未提交的事务回滚）和工厂，包括别的线程上尚未结束的会话。已经正常结束或关闭失败的会话不留在登记里。调用方应先停止新请求再关；不会去杀业务线程。关闭过程里的多个异常保留第一个，其余挂在 suppressed 上。关掉一个配置不影响另一个配置。关闭之后再取 `EntityManager` 或连接会失败。Hibernate 5.3.7 在 JDK 17 上需要 `javax.xml.bind`。ORM 使用 Central 上的 `jakarta.xml.bind-api` 2.3.2 和 `org.glassfish.jaxb:jaxb-runtime` 2.3.2。这两个坐标的类仍在 `javax.xml.bind` 包里，API 的 class 主版本是 52，运行时基线 class 主版本是 51，不是 Jakarta XML Binding 3 或 4。`javax.xml.bind` 的 110 个类只来自 `jakarta.xml.bind-api` 2.3.2，不是重复。Hibernate 和 Connector/J 没有为这件事升级。

Activation 也停在 1.2.x 的 `javax.activation` 包。Hibernate 5.3.7 带 `javax.activation:javax.activation-api` 1.2.0。`jakarta.xml.bind-api` 2.3.2 再带 `jakarta.activation:jakarta.activation-api` 1.2.1；`jaxb-runtime` 2.3.2 的传递依赖里原来也会带上同一份。两份 jar 里是同一套 31 个 `javax.activation` 类。ORM 在这两个依赖上排除 `jakarta.activation-api`，并直接依赖 `javax.activation-api` 1.2.0。不改成 Jakarta Activation 2 的 `jakarta.activation` 包。SLF4J 仍只有 API 1.7.32，没有 `slf4j-nop` 或其他绑定。Connector/J 5.1.6 连接隔离的 MySQL 8.0.46；`org.postgresql:postgresql` 42.7.13 由根 POM 管理，并从 `serviceframework-common` 传入 ORM。

## 自测

JDK 17 编译并 `install` 一次，目标字节码是 8。同一批 `target/classes` 和 `target/test-classes` 再分别用 JDK 17 和 JDK 8 跑 Surefire，中间不 `clean`、不重新编译。哈希记在当次命令输出里。Maven 使用 `-s '/tmp/sf maven central/settings.xml'`。数据库和 Maven 共用协调锁；`dev/compat-services.sh run` 拉起隔离 MySQL 8.0.46，结束时停掉本次进程。口令只留在 `SF_COMPAT_ENV_FILE`。Surefire 子进程认环境变量 `SF_ORM_MYSQL=true`。PostgreSQL 使用独立的 `dev/pg-compat-services.sh run`，注入 `SF_COMPAT_PG_ENV_FILE`，并由 `SF_ORM_PG=true` / `SF_WEB_PG=true` 打开对应测试。没开这些开关时实库测试会跳过，跳过不算通过。开了开关但没有隔离库，测试失败。

聚焦测试：

- `ModelClassHierarchyTest`（含缺父类的模型必须失败，普通非模型类仍跳过）
- `ModelRegistryTest`
- `OrmRulePlanTest`
- `OrmContextIsolationTest`（默认上下文已配置时，空的 active context 和禁用 context 不能借配置；scope 按栈恢复）
- `DynamicJpaFinderBytecodeTest`
- `OrmMysqlBusinessTest`（保存、分页、回滚、关联、三级继承、同名解析，两个真实 `JPAConfig` / 池，多次提交和回滚之后仍打开的 `EntityManager` 回到 0。手动 `close()` 和 `unwrap(Session.class).close()` 之后，Hibernate 池的借出数回到 0，同一条连接回到可用队列并且 `@@autocommit` 恢复为 1。另一个线程上尚未关闭的会话在 shutdown 时被关掉，关掉之后借出数仍是 0。没有 Hibernate 的上下文和 compat 入口上的 Quill `SELECT` 也在这里。`ALTER` 之后 `refresh` 会换掉列元数据和 schema 摘要，连续两次 `schemaDigest()` 计数增加、十六进制不变）
- `OrmPostgresBusinessTest`（PostgreSQL 主数据源上的保存、分页、回滚、关联、`UUID` / `NUMERIC` / `TIMESTAMP` / `BYTEA` 类型、schema 过滤和 refresh、命名 `multi-postgres` 池、`postgresCtx`，以及两个上下文各自持有并关闭自己的池）
- `JdbcEngineSelectionTest`（默认 MySQL、PostgreSQL 别名和 schema 传播、非法引擎、类型映射）
- `OrmDiagnosticsLiveTest`（关闭诊断时不算 schema 摘要、不哈希。打开诊断时父类和子类的原始摘要等于增强前的类字节；查询方法和关联方法记在各自的规则上；用户 getter 保持不变；非叶子跳过查询方法。`final getNote()` 冲突写出类、规则和阶段，并关掉本次拥有的 context。代表报告在 `/tmp/sf-orm-diagnostics-report/`）
- `TrackedEntityManagerCloseTest`（关闭先回滚再关；关闭失败仍注销，rollback 失败是 primary，随后的 close 失败挂在 suppressed 上）
- `JdbcEndpointTest`（诊断里的 JDBC 地址去掉查询串和 userinfo；连接用的 URL 仍保留 `jdbc.*`；用合成口令，不写真实口令）
- `OrmLoggingContractTest`（模块不传递 `slf4j-nop`，classpath 上是 `slf4j-api` 1.7.32。隔离加载器里没有绑定和换上应用自己的绑定，都能建起 Quill `MysqlJdbcContext`）
- `QueryApiRegressionTest`（Java 和 Scala companion 调用仍在）
- `QuillImportCompileTest`（`val ctx = QuillDB.ctx; import ctx._` 和 `postgresCtx` / 命名 PostgreSQL 入口能编译）
- `DataSourceManagerCloseTest`（同一个池只关一次；关闭失败仍清掉其余登记，并保留 primary 和 suppressed）

两个应用可以共用 `sf_compat`。隔离看的是不同的池、`EntityManager` 和后端连接号（MySQL 是 `connection_id`，PostgreSQL 是 `pg_backend_pid()`），以及关掉一边之后另一边还能读写。同一张表里能看见对方已提交的行，这是共享库，不是上下文串了。

扫描到的类如果父类字节码缺失，`ModelClass.isModelSubclass` 抛出 `EnhancementFailure`，类别 `ENHANCEMENT`，阶段 `hierarchy`，类名是目标，detail 里有父类名。父类链能解析且不是 `Model` 的普通类仍然返回 false，扫描会跳过它。
