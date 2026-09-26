# Mongo 增强接入

日期：2026-09-26 更新到 `mongodb-driver-legacy` **5.13.0**；本机隔离 MongoDB **8.3.11** 上的最终独立验收已完成，结论见 [multi-database-verification-2026-09-26.md](multi-database-verification-2026-09-26.md)。2026-09-25 的整仓矩阵属于历史结果；下文仍然把它写成历史覆盖，不把后来的代码差异说成已经独立复验。本文仍只描述 `serviceframework-mongo`。启动接线在 [framework-extensions.md](framework-extensions.md)，验收范围在 [serviceframework-bytecode-migration.md](serviceframework-bytecode-migration.md)。

## 驱动

依赖是 `org.mongodb:mongodb-driver-legacy` **5.13.0**。5.x 把实现拆成 `mongodb-driver-legacy`、`mongodb-driver-core`、`mongodb-driver-sync` 和 `bson` 四个构件；不要只复制 legacy jar。legacy API 保留本模块暴露的 `DB`、`DBCollection`、`DBCursor`、`BasicDBObject` 和 `MongoClient`，类文件主版本仍是 **52**（Java 8），因此同一产物可由 JDK 8 和 JDK 17 加载。

`mongodb-driver-core` 会以 runtime 传递 `org.mongodb:bson-record-codec:5.13.0`，它是 Java 17 Record 编解码器（class major 61）。本模块 exclude 了它：legacy Document API 不分发这个扩展，driver-core 的 `com.mongodb.Jep395RecordCodecProvider` 反射探测不到时捕获 `ClassNotFoundException`/`UnsupportedClassVersionError` 回退，JDK 8 classpath 因此不带 major 61 字节码。

`com.mongodb.Mongo` 在 5.x 里已经不存在，公开/子类入口统一迁移为 `MongoClient`：`MongoMongo.mongo()`、`CSDNMongoConfiguration.client()`、`closeMongoClient(MongoClient)`，以及 `DB.getMongoClient()`。连接字符串由 `MongoClientURI` 解析；离散账号路径仍是 `MongoCredential.createScramSha256Credential`。`DBCollection.ensureIndex` 仍用 `createIndex(DBObject, DBObject)`。

版本依据：MongoDB 当前稳定系列按官方发布说明 <https://www.mongodb.com/docs/manual/release-notes/> 定为 **8.3.x**（本机 runner 固定 8.3.11）；驱动 **5.13.0** 按 Maven Central 元数据 <https://repo.maven.apache.org/maven2/org/mongodb/mongodb-driver-legacy/maven-metadata.xml> 选定。

### 日志

中央仓库 `mongodb-driver-legacy-5.13.0.pom` 仍把 `org.slf4j:slf4j-api` **1.7.6** 标成 `optional`。Maven 不会因为它而带上 API，更不会带上绑定。

驱动的 `com.mongodb.diagnostics.logging.Loggers`（`org.bson.diagnostics.Loggers` 同样）只做 `Class.forName("org.slf4j.Logger")`。找不到类就用 `java.util.logging`，客户端照样能连。这不是初始化失败。

本模块显式依赖 `slf4j-api` **1.7.32**（类文件主版本 **49**，Java 5；中央仓库 jar SHA-1 `cdcff33940d9f2de763bc41ea05a0be5941176c3`）。1.7.32 的 `LoggerFactory` 在没有 `org.slf4j.impl.StaticLoggerBinder` 时打印 `Failed to load class "org.slf4j.impl.StaticLoggerBinder"`，然后自己落到 NOP，**不抛异常**。因此不需要、也不声明 `slf4j-nop`。绑定由应用放上 classpath；本模块不替应用选一个会吞掉日志的绑定。

## 给 Web 接的 API

`MongoMongo.configure(CSDNMongoConfiguration)` 仍然是 `void`，四个参数的构造器也还在：`(mode, settings, Class marker, ClassPool pool)`。传进来的 `ClassPool` **不参与增强**。每个配置使用自己的 `EnhancementContext` 和它的 pool。`getClassPool()` 在配置成功后返回这个 context 的 pool；配置前是 null。

禁用：`{mode}.datasources.mongodb.disable=true` 时直接返回，状态 `DISABLED`，不建客户端，不扫描，不进入 `OPEN`。缺省按 **false**（会连接）。Bootstrap 自己仍然先判断 disable 再调用 `configure`。

启用后的键：

| 键 | 作用 |
| --- | --- |
| `{mode}.datasources.mongodb.uri` | 非空时优先；连接主机、账号、口令和 `authSource` 都从 URI 来 |
| `{mode}.datasources.mongodb.host` | 默认 `127.0.0.1`；只在未配置 URI 时读取 |
| `{mode}.datasources.mongodb.port` | 默认 `27017`；只在未配置 URI 时读取 |
| `{mode}.datasources.mongodb.database` | 默认 `csdn_data_center`；URI 有数据库路径时让位，没有路径时作 fallback |
| `{mode}.datasources.mongodb.username` | 空则不认证；只在未配置 URI 时读取 |
| `{mode}.datasources.mongodb.password` | 离散用户名存在时必填；只在未配置 URI 时校验，异常文本里不放口令 |
| `{mode}.datasources.mongodb.authenticationDatabase` | 没有则读 `authdb`，再默认 `admin`；只在未配置 URI 时读取 |
| `{mode}.datasources.mongodb.replicaSet` | 非空时作为 `requiredReplicaSetName` 默认值；URI 自带的 `replicaSet` 选项优先 |
| `application.document` | 要扫描的包，启用时必填 |

URI 优先级不是文档口号：配置了有效 URI 时，离散的 host/port/username/password/authenticationDatabase 不会再被解析或校验，因此残留的离散用户名、缺失离散口令或非法离散端口不会挡住 URI 连接。URI 自身 malformed 时抛 `mongodb uri is invalid`，不把 URI 或驱动解析原文放进异常。URI 数据库路径存在时覆盖 `{mode}.datasources.mongodb.database`；URI 无路径时才使用离散 database。

认证走 SCRAM-SHA-256（URI 则由连接串自己的 credential/authSource 表示），然后对目标库 `ping`。失败是 `EnhancementFailure`，`rule=mongo-document`，`phase=connect`，`getCause()` 是驱动异常。状态保持 `FAILED`（调用方再 `close()` 才变成 `CLOSED`）。context 会关掉，已经建出的客户端会 `close()`。

其它入口：

- `configuration.enhancementContext(EnhancementContext)`：复用调用方的 context，加载器必须就是 marker 的加载器。配置成功后，这个 context 被关掉时会走和 `configuration.close()` 同一条结束路径：客户端 `close()` 一次、从 `OPEN` 拿掉、状态改为 `CLOSED`。closer 不调用 `context.getAttribute`（common 先把 context 标成 closed 再跑 closer）。`configuration.close()` 不关这个外来 context。
- `configuration.registerRule(EnhancementRule)`：加在 `mongo-document` 之后，可以 `requires` 它。
- `configuration.registerAnchor(Class)`：显式登记 anchor。marker 的简单类名如果就是 `ServiceFrameworkPackageAnchor`，也会自动登记。
- `configuration.configure()`：返回 `MongoMongo`。`MongoMongo.configure` 不返回值。
- `configuration.close()`：可重复调用。客户端先关掉，并且只关一次；拥有的 context 在放下配置锁之后再关，避免和 context 的锁交叉。客户端 `close` 抛异常时，仍然继续关拥有的 context。先发生的那个异常原样抛出，另一个用 `addSuppressed` 挂上。调用方自己的 scope 还在时，`EnhancementContext.close` 会拒绝，这次配置**不会**变成 `CLOSED`，拥有的 context 留着，scope 结束后再次 `close()` 才把它关掉。客户端和拥有的 context 都已经结束之后，再次调用什么都不做。外来 context 仍然不关。
- `closeMongoClient(MongoClient)`：真正调用驱动 `close` 的地方。默认把异常返回而不是抛出。子类可以包一层，但必须自己关掉这个客户端，或者调用原来的实现。返回的异常不会跳过后面的清理，也不会让下一次再关一次客户端。
- `configuration.state()`：`NEW`、`DISABLED`、`CONFIGURED`、`FAILED`、`CLOSED`。
- `mongo.activate()`：在**当前线程**压入该 context 的 scope。异步线程必须自己拿到这个 `MongoMongo` 再 `activate()`，不能读别的线程的 ThreadLocal。
- `MongoMongo.current()`：当前 scope 里的客户端对象；没有 scope，或者 scope 里没有挂上 Mongo，就是 null。不检查客户端是否已经关掉。
- `MongoMongo.resolve()` / `Document.mongo()`：当前线程有 scope 时只用这个 scope。scope 里没有可用客户端（没挂上、已经 `close`、或配置已是 `CLOSED`）抛 `phase=resolve`，文案是 `the active enhancement context has no mongo client`，**不会**去用另一个应用。没有 scope 时，只有一个未关闭的客户端才用那一个；零个抛 `no mongo context is active`；多个抛 `more than one`。已经关闭的配置不算这一个。
- `MongoMongo.getMongoConfiguration()`：选择规则和 `resolve()` 相同。没有可用客户端时返回 null，不抛。
- `MongoMongo.settings()` / `injector()` / `mode()`：走上面的选择。当前 scope 没有 Mongo 时抛 `the active enhancement context has no mongo client`，不返回另一个应用的配置。
- `Document.collection()`：当前线程有 scope 时，向这个 scope 的客户端要 `parent$_collectionName` 对应的集合；scope 没有可用客户端就抛，不用类初始化时记下的集合。没有 scope 时才用那份已经记下的集合。
- `Criteria.collection()` 每次重读。模型类直接调用模型的 `collection()`，异常会原样抛出，不再被反射吞掉后改走别的客户端。只有表名的 `nativeQuery` 走 `resolve()`。

`Document.mongoMongo` 还在，**configure 不再给它赋值**。两个应用不能靠改这个静态字段换库。

同一个配置对象再 `configure`：`phase=configure`，`configuration is already configured`。已经 `close` 或 `FAILED` 的对象也不能再配。同一个加载器里一旦 `define` 过模型，再次配置报 `hot reload is not supported`，即使 context 已经关掉。这条记录按加载器的**引用身份**记，不看 `equals` / `hashCode`：两个 `equals` 为真、但是不同实例的加载器各自配置，互不挡。同一个还活着的加载器仍然拒绝。加载器被回收后条目随之消失，不会把 ClassLoader 钉在静态集合里。JVM 里的类撤不掉，所以不能靠 `close()` 把记录删掉再对同一个加载器重配。

每个会被定义的模型包都要有一份**事先编译**的 `ServiceFrameworkPackageAnchor`，和模型同一个加载器、同一个包、同一个 `ProtectionDomain`。不要把业务类当 anchor，也不要 `--add-opens`。扫描只收 `Document` 的子类，基础 `Document`、anchor、以及不是 Document 的类不增强、不定义。父类先增强再定义，然后才初始化子类。关联和 embedded 方法如果是继承来的，只在目标类上复制后改方法体，不改父类的 `CtMethod`。`final` / `private` / `static` 的继承方法复制不了，配置阶段 `phase=enhance`、`CONFLICT`，带目标类名和方法名。

子类自己的 `<clinit>` 一开始会把父类的 `parent$_associations` 和 `parent$_associations_embedded` **放进一份新的 Map**（`putAll`，不共享那张表），然后再执行子类自己的 `hasMany` / `hasManyEmbedded` 等。子类可以追加或替换名字，替换只写子类这份表。初始化结束时，每个关联访问器都要在自己的表里有对应条目，类型还得对得上；否则 `phase=initialize`、`CONFLICT`，类名和方法名都在异常里，不会拖到第一次调用才空指针。

静态块里的 `storeIn` / `alias` / `hasMany` 在 javac 时绑的是 `Document`。增强时会把这些 `invokestatic` 改到模型自己的副本上，这样每个模型的 `parent$_` 才是自己的。`<clinit>` 本身不复制。

## 实库测试怎么打开

当前 modern lane 是 `dev/mongo-modern-services.sh`：MongoDB **8.3.11**、`mongosh` **2.12.0**，官方归档按 SHA-1 和 SHA-256 校验后使用；首次下载用 HTTP/1.1、有界重试和 `-C -` 断点续传，校验通过前不会启用归档。实例只监听 `127.0.0.1`，单节点副本集 `sfcompat`，库只使用 `sf_compat`，认证打开。账号、口令和 authDB 写在实例目录 mode `600` 的 env 文件里，不打印。集合名只用 `sf_it_` 前缀。外部 `SF_COMPAT_ENV_FILE` 会被合并，只有 `SF_COMPAT_MONGO_*` 由本 runner 覆盖。

旧的 `dev/compat-services.sh` MongoDB **4.4.29** 只保留为历史/回归基线；它能证明旧支持面，不再当作“latest MongoDB”的证明。

实库用例在下面任一成立时才运行，否则跳过，也不会去连本机 27017：

- 系统属性 `-Dsf.compat.mongo=true`
- 环境变量 `SF_COMPAT_MONGO=true`（不是 `SF_COMPAT_MONGO_HOST`）

两个可以同时设。标志开了但没有 `SF_COMPAT_ENV_FILE`，或者文件里的库不是 `sf_compat`，用例失败，不算跳过。`JDK8_HOME`/`JDK17_HOME` 指向你自己的 JDK；`mvn` 用默认 settings，需要自定义 mirror 时单独加 `-s <your-settings.xml>`：

```bash
cd "$HOME/projects/ServiceFramework"
JDK8_HOME=/path/to/your/jdk8
JDK17_HOME=/path/to/your/jdk17

# 已缓存归档的 runner 自检：ping、读写、事务回滚、版本 8.3.11
dev/mongo-modern-services.sh run -- dev/mongo-modern-services.sh verify

# 整个 Mongo 模块实库 lane（不要改用 surefire:test 打已安装旧 jar）
dev/mongo-modern-services.sh run -- \
  env SF_COMPAT_MONGO=true JAVA_HOME="$JDK17_HOME" PATH="$JDK17_HOME/bin:$PATH" \
  mvn -pl serviceframework-mongo -am \
  -Dtest=MongoEnhancementLiveTest -Dsurefire.failIfNoSpecifiedTests=false test

# URI 优先级的聚焦回归；同一命令换 JAVA_HOME/PATH 到 JDK8 也可跑
dev/mongo-modern-services.sh run -- \
  env SF_COMPAT_MONGO=true JAVA_HOME="$JDK17_HOME" PATH="$JDK17_HOME/bin:$PATH" \
  mvn -pl serviceframework-mongo -am \
  -Dtest=MongoEnhancementLiveTest#uriConnectsHonorsUriDatabaseAndIgnoresDiscreteSettings \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

最终独立验收：六模块同一产物矩阵在 JDK 8 / JDK 17 各 **1234 executed + 1 skipped（`RemoteMysqlWebTest` opt-in）、0 failure / 0 error**，其中 `MongoEnhancementLiveTest` 在 MongoDB 8.3.11 上双 lane 各 **20/20**，另有最终产物 classpath 的实库探针通过；汇总见 [multi-database-verification-2026-09-26.md](multi-database-verification-2026-09-26.md)。更早的实现阶段自测（8.3.11 上 `serviceframework-mongo` 全量 284 tests、URI 聚焦 1 test）只作历史保留。

## 诊断

默认关闭。不调用下面的方法时，`configure` 仍然用 `EnhancementContext.open(loader)`。这条 context 没有观察器，诊断不哈希、不读类字节、不写报告，也不计算模型摘要。若传入的 context 上已有 `EnhancementObserver` 而诊断仍关闭，原始类字节会读给观察器，诊断哈希、计数、报告和模型摘要仍然不做。`{mode}.datasources.mongodb.disable=true` 时同样不算摘要、不打开 context。这里不接受 `Settings` 当诊断参数。

两种打开方式，用的是同一个 `EnhancementDiagnostics` 实例：

```java
EnhancementDiagnostics diagnostics = EnhancementDiagnostics.enabled(reportDir);
// 源码和 class 转储各自再开，默认都关，本模块不会替调用方打开：
// diagnostics.setEmitGeneratedSource(true);
// diagnostics.setEmitClassFiles(true);

// 配置自己拥有 context
new MongoMongo.CSDNMongoConfiguration(mode, settings, anchor)
        .enhancementDiagnostics(diagnostics)
        .configure();

// 或者调用方已经打开了 context。两个入口若同时给，必须是这同一个 diagnostics。
EnhancementContext shared = EnhancementContext.open(anchor.getClassLoader(), diagnostics);
new MongoMongo.CSDNMongoConfiguration(mode, settings, anchor)
        .enhancementContext(shared)
        .enhancementDiagnostics(diagnostics)
        .configure();
```

只给 context、不给 diagnostics 时，用 context 上已经绑好的那份。只给 diagnostics 时，这份配置创建并拥有 context，`close()` 会关掉它并 `flush()`。两边都给但不是同一个对象，`configure` 在连接之前抛 `CONFIGURATION`，文案是 `enhancement context and diagnostics disagree`，状态保持 `NEW`。`enhancementDiagnostics(null)` 直接拒绝。已经离开 `NEW` 之后不能再改 diagnostics。

版本令牌是 `mongo-1`（`MongoModelSchema.VERSION`）。摘要是已扫描文档类的**声明模型** SHA-256，不是 MongoDB 服务器上的 collection 或 schema 检查。参与摘要的只有排序后的类名、非 static 且非 synthetic 的声明字段、字段类型，以及字段上的 RUNTIME 注解（含 `Transient`、`Validate`）。注解成员值若像口令、`jdbc:` 或 `mongodb://`，哈希前换成 `[redacted]`。不放账号、口令、原始 `Settings`、JDBC 或连接串。回调方法上的注解、关联方法名单，都不在这份摘要里。诊断关闭时不走字段、不算这枚摘要；`modelSchemaDigestComputations()` 保持 0。

`mongo-1`（`MongoModelSchema.VERSION`）是上面这份声明模型的格式，不是应用配置修订。事件在写入时复制当时的 `configVersion` 和 `schemaDigest`。Mongo 在自己的 `apply` / `define` 之前写入模型摘要；如果调用方已经给了修订，事件保留那个修订，只有调用方什么都没给时才写 `mongo-1`。增强一结束，如果进来之前已经有修订，就连同原来的摘要写回去。更早的 ORM 事件不会被改掉；共享 context 上随后的 Controller 事件也不会一直带着 Mongo 的摘要。编排方仍然在每个模块边界自己调用 `noteSafeMetadata`。common 没有按模块分仓的元数据存储，这里也不加。

方法从哪来，看诊断事件，不看另一份缓存。`Record` 上一次真实增强的例子：

| 签名 | 结果 | 规则 |
| --- | --- | --- |
| `findById(Ljava/lang/Object;)Ljava/lang/Object;` | `added` | `mongo-document` version `1` |
| `hasMany(Ljava/lang/String;Lnet/csdn/mongo/association/Options;)Lnet/csdn/mongo/association/HasManyAssociation;` | `added`，从 `Document` 复制的静态方法 | 同上 |
| `setTitle(Ljava/lang/String;)V` | `rewritten`，精确 setter 被包了一层 | 同上 |
| `setTitle(Ljava/lang/Object;)V` | 不在变更列表里，用户重载保持原样 | |
| `getTitle()Ljava/lang/String;` | 不在变更列表里，自定义 getter 保持原样 | |

`apply` 的 `reason` 是：`mongo-document copies static helpers and adds finders; an exact user setter is wrapped, a custom getter or other overload is left unchanged`。`Level3.extras()Lnet/csdn/mongo/association/Association;` 是类上声明的访问器，记为 `rewritten`。父类继承来的 `notes()` 复制到 `Level3` 上，记为 `added`。`getLeaf()Ljava/lang/String;` 不在变更列表里。这些事件的 `originalSha256` 是第一次改字节之前的类文件摘要。

`LockedChild` 继承 `final` 的 `notes()` 时，配置仍以 `CONFLICT`、`phase=enhance` 失败。诊断事件的 `phase` 是 `apply`（计划记下失败的阶段），`class` 是 `LockedChild`，`rule` 是 `mongo-document`。失败文本里保留原来的 `phase=enhance`。context 和客户端都会清掉。报告里没有口令和连接串。源码目录 `sources/`、类文件目录 `original/` 和 `enhanced/` 只有调用方单独打开对应开关才出现。

没有增强结果缓存。报告是诊断对象 `flush` 写出的 `report.txt`，不是另一份可以拿来跳过增强的产物。
