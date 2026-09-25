# 公共阶段的扫描和诊断成本

下面「扫描峰值」和「诊断开和关」只测了 `serviceframework-common` 里的合成夹具。那次峰值从 25 降到 1，耗时重叠，不能当成整个框架启动变快，也不是操作系统文件描述符下降。没有做增强结果缓存。2026-09-25 的全框架冷启动、重复启动和热调用在 [serviceframework-bytecode-migration.md](serviceframework-bytecode-migration.md)，原始汇总是 `target/bytecode-final-verification-20260925/phases/framework-phases-aggregate.txt`。本文最后一节是更早一次全框架测量，数字已经被那次终验替代。旧的 JDK 17 加载器起不来，所以不存在一份可对照的历史全框架 JDK 17 启动时间。扫描 25→1 没有在终验里重测。

测量用的是同一次 JDK 17 编出来的 Java 8 产物（类主版本 52）。基准入口是 `dev/measure-enhancement-costs.sh`。下面这次的输出在 `/tmp/sf-t12-bench-verified/enhancement-costs.json` 和同目录的 `enhancement-costs.txt`。

运行参数：fixture 25 个类（24 个 `BenchN` 加 `BenchAnchor`），warmup 3，正式迭代 11。跑基准的 JDK 是 17.0.20.1（specification 17）。流数是逻辑 `InputStream` 数，不是操作系统文件描述符。JAR 扫描会把条目拷进内存流再关掉 JAR，这两种数本来就不是一回事。

## 扫描峰值

旧回调等价于先 `scanArchives(String)` 把包里的类流全部打开，再 `scanClass(List, callback)`。新回调是 `scanArchives(String, callback)`，打开一个、回调、关掉，再打开下一个。两边用同一个回调，把流读完后返回 null。

| 路径 | 峰值逻辑流 | 访问类数 | 耗时 min / p50 / p95 / max / mean（纳秒） | 这段迭代的 GC |
| --- | --- | --- | --- | --- |
| 一次打开全部再回调 | 25 | 25 | 12370323 / 15088882 / 19837987 / 19837987 / 15618649 | 1 次，3 ms |
| 每次一个资源的回调 | 1 | 25 | 12935816 / 14138833 / 14679856 / 14679856 / 13995909 | 0 次 |

峰值从 25 降到 1，和 fixture 类数一致。耗时都在十几毫秒，两次分布叠在一起，不能从这 11 次得出启动加速。GC 和 `usedBytesDelta` 同样只是这一小段的噪声：回调那次堆占用差反而更大，不能当成省内存。

另外做了一次不进计时的路径检查：带空格的目录打开 2 个逻辑流，带空格的 JAR 打开 2 个类条目（跳过了非 class），URL 里有 `%20`。目录、JAR、空格路径和失败时关闭流的回归在 `DefaultScanServiceTest` 里。

列表 API 仍然一次返回全部流，调用方自己关。上面的「一次打开全部」就是这条所有权，没有改。

## 诊断开和关

每个样本是一个新的 context：`makeClass`、一条给类加 `marked()V` 的规则、`apply`、`close`。没有 `define`，也没有 `EnhancementObserver`。打开诊断时会把报告写到目录里；关闭诊断时输出目录即使传了也不写。另有一次打开 emit 的单独调用，不放进上面的耗时循环。关闭一行的 0 是诊断关闭且没有观察器的零捕获，不是“有观察器也不会读类字节”。

| 模式 | hash | 类字节读取 | 方法扫描 | 磁盘写入 | 事件 | 耗时 min / p50 / p95 / max / mean（纳秒） |
| --- | --- | --- | --- | --- | --- | --- |
| 关闭 | 0 | 0 | 0 | 0 | 0 | 140438 / 174123 / 284685 / 284685 / 199205 |
| 打开 | 1 | 1 | 2 | 3 | 1 | 992059 / 1175648 / 3356248 / 3356248 / 1349332 |

打开时这一次只有原始类 hash（没有 define，所以没有结果 hash），方法扫描是改之前和改之后各一次。磁盘写入 3 来自单独的 emit 调用：`report.txt`、生成源码、原始 class 文件。因为没有 define，这次基准没有写出 enhanced class。单元测试里打开 `setEmitClassFiles` 并且 `define` 之后，`original/` 和 `enhanced/` 都会落盘；默认两个 emit 开关都是关的。

关闭诊断的 p50 大约 0.17 毫秒，打开大约 1.2 毫秒，差在 hash、方法对比和写报告。这是单个合成类，不是业务启动。

## 更早的 common 双 JDK 测试

这一节只证明当时的 `serviceframework-common`。它不是 2026-09-25 的全反应器 1212。证明用的是私有快照 `/tmp/sf-t12-snapshot`（`git archive HEAD` 后叠当时的父 POM、common 的源码和 POM，以及 `dev/verify-jdk-compatibility.sh`）。本地仓库是 `/tmp/sf-t12-verify/m2`，从 `/tmp/sf-head-snapshot/target/jdk-compatibility/m2` 用 `cp -cR` 克隆，没有改原来的缓存，也没有用 `~/.m2` 做 install。

在快照目录里执行：

```text
JDK8_HOME=/usr/local/opt/openjdk@8 \
JDK17_HOME=/usr/local/Cellar/openjdk@17/17.0.20.1/libexec/openjdk.jdk/Contents/Home \
./dev/verify-jdk-compatibility.sh \
  --report-dir /tmp/sf-t12-verify \
  --maven-settings '/tmp/sf maven central/settings.xml' \
  serviceframework-common
```

JDK 17.0.20.1 做一次 `install -DskipTests -DperformRelease=false -Dmaven.javadoc.skip=true`，Animal Sniffer 和依赖审计通过。然后同一份产物分别用 JDK 1.8.0_504 和 JDK 17.0.20.1 跑 Surefire，中间不重新编译。两次都是 552 个测试、0 个跳过，退出码 0。188 个 class/jar 的 SHA-256 在构建后、JDK 8 测试后、JDK 17 测试后一致。

`serviceframework-common_2.13-2.0.9.jar` 的 SHA-256 是 `e76cb4c345c781526a2f9d9cbfe0730b8cae058385f1b363f13e62dcf5f2b78d`。`EnhancementDiagnostics.class` 和 `DefaultScanService.class` 的主版本都是 52。

日志在 `/tmp/sf-t12-verify/`：`build-jdk17.log`、`test-jdk8.log`、`test-jdk17.log`、`summary.txt`。以前 `/tmp/sf-common-fix-jdk8.log` 里的 539 只是旧基线，不是这次的结果。552 比 539 多出来的是这次的诊断和扫描测试。

脚本自己不 `cd` 到仓库根。第一次误在 ServiceFramework 工作区目录里调用了它，Maven 写到了工作区的 `target/`。上面通过的那次是在快照目录里重新跑的。

## 更早的一次全框架测量

这一节的数字来自 `/tmp/sf-t15-final-phases/`，**不是** 2026-09-25 终验。终验的冷启动 p50 是 JDK 8 的 4360502596 纳秒和 JDK 17 的 3835738867 纳秒，记录在 [serviceframework-bytecode-migration.md](serviceframework-bytecode-migration.md)。不要把下面的表当成当前结果。两次都是热的操作系统和热的数据库，n 很小，也都没有增强结果缓存，都不能说整个启动变快了。

当时测的是同一次 JDK 17 编出来的 Java 8 产物（类主版本 52），分别用 JDK 1.8.0_504 和 JDK 17.0.20.1 跑。没有对照旧的整框架 JDK 17 启动时间，因为旧加载器在 JDK 17 上起不来。样本很小：冷启动每个 JDK 3 个新 JVM，重复启动是同一个 JVM 里丢掉 1 次预热后再测 5 次新的应用加载器，热调用是同一个 context 上 20 次 `GET /db/both`。MySQL 8.0.46 和 MongoDB 4.4.29 已经在回环上跑着，操作系统页缓存也不是冷的。服务包和 util 包是空的，所以没有 `scan.service` 和 `scan.util`。这不是大应用的扫描测量。

夹具是预编译的 `ApplicationDatabaseTest` 那套 ORM、Mongo 和 `/db/both`。Web 模块把 ORM 和 Mongo 标成 provided，基准不再用 `includeScope=runtime`，也不再用 `-am` 让每个模块覆写同一份 classpath。它只解析 `serviceframework-web` 的 test scope（compile、runtime、provided、test），再把六个模块的 `target/classes` 和 Web 的预编译测试类放在前面。生产 POM 的 scope 没有为了基准去改。`mongo-java-driver` 3.12.14 和 `mysql-connector-java` 5.1.6 都在这条 classpath 上。六个模块的 class 和已安装 JAR 逐个 class 的 SHA-256 相同；跑完两个 JDK 之后，708 个 class（含 53 个预编译夹具）加 122 个实际用到的 JAR，一共 830 行哈希没有变化。哈希清单本身的 SHA-256 是 `bce77b0792c51fc9cbafebae48649983448264c0ad959c95c8650383c071fbf5`。这只证明这两次运行用的是同一批 class 和 JAR，不是在说 javac 的源码输入哈希也核过。

`exclusiveNanos` 是 `nested=false` 的阶段样本之和，再加上 `DBInfo` 构造时那一次 JDBC 元数据刷新。这次刷新发生在 `scan.orm` 之前，不在任何一个阶段样本里面，所以没有算两遍。`getTables` 和 `getColumns` 各 1 次，看到 2 张表；夹具自己只建 `sf_web_lifecycle_record`，同一轮前面的 ORM 和 Web 数据库测试也用了这个 `sf_compat` 库，所以这 2 张不都是本次夹具新建的。`residualNanos` 是墙钟减去 exclusive。这次夹具里每一条阶段的 `nested` 都是 false：扫描帧在规则 apply 和 `define` 之前已经关掉，`mongo.connect` 也不包住 `scan.mongo`。如果以后有内层样本，它们仍然会列出来，但不会再加进 exclusive。`entity-mapping` 这次只有 1 条，就是这一个实体的整棵树，没有第二条 no-op。`define` 有 3 条，都算进 exclusive。

冷启动墙钟的 p50，JDK 8 是 4653043864 纳秒，JDK 17 是 3756343993 纳秒。重复启动的 p50 分别是 700766640 和 785124718 纳秒。热调用的 p50 分别是 10783490 和 16481093 纳秒。n 这么小，JDK 之间的差别只作记录，不当成优化收益。最大的一段是 `jpa.emf`，冷启动 p50 在 JDK 8 上是 1829438656 纳秒，JDK 17 上是 1339513472 纳秒。

| 组 | JDK 8 min / p50 / p95 / max / mean（纳秒） | JDK 17 min / p50 / p95 / max / mean（纳秒） |
| --- | --- | --- |
| 冷启动墙钟，n=3 | 4596023311 / 4653043864 / 4777443161 / 4777443161 / 4675503445 | 3743218572 / 3756343993 / 3758117236 / 3758117236 / 3752559933 |
| 冷启动 exclusive | 3672286633 / 3745991055 / 3905370036 / 3905370036 / 3774549241 | 3050168278 / 3075336908 / 3083324613 / 3083324613 / 3069609933 |
| 冷启动 residual | 872073125 / 907052809 / 923736678 / 923736678 / 900954204 | 673019380 / 682780328 / 693050294 / 693050294 / 682950000 |
| 冷启动 JDBC 元数据 | 27898927 / 28575141 / 29717753 / 29717753 / 28730607 | 26624371 / 27960471 / 29622061 / 29622061 / 28068967 |
| 冷启动后第一次业务调用 | 828501768 / 833648133 / 838190949 / 838190949 / 833446950 | 671972984 / 679928073 / 700978668 / 700978668 / 684293241 |
| 重复启动墙钟，n=5 | 666953902 / 700766640 / 792635160 / 792635160 / 711087423 | 716411343 / 785124718 / 894600966 / 894600966 / 789617261 |
| 热调用，n=20 | 9450434 / 10783490 / 14630969 / 39772537 / 12567440 | 11003000 / 16481093 / 55183674 / 66525855 / 25055754 |

冷启动各阶段 p50，单位纳秒。`define` 是 3 次定义的合计。

| 阶段 | JDK 8 p50 | JDK 17 p50 |
| --- | --- | --- |
| scan.orm | 491986981 | 421682146 |
| rule.entity-mapping | 70768736 | 68241265 |
| rule.orm-query | 40248814 | 48513814 |
| rule.association | 13272024 | 16018522 |
| define | 30612888 | 15809120 |
| mongo.connect | 99608394 | 65598013 |
| scan.mongo | 232871342 | 271631812 |
| rule.mongo-document | 31837145 | 35926438 |
| scan.controller | 227306035 | 210373380 |
| controller.makeClass | 153472 | 186696 |
| rule.controller-filter | 1914782 | 2631235 |
| guice | 581379124 | 509209573 |
| jpa.emf | 1829438656 | 1339513472 |
| http.bind | 51615739 | 31025070 |

关闭之后的内存是 `measureStart` 返回以后才调用 `System.gc()` 再读的。那个方法里的 `ApplicationContext` 和子加载器局部变量已经不在观察这一帧上。`System.gc` 只是一个提示。JDK 8 冷启动的堆 p50 从关闭前的 36654480 字节到提示之后的 15773184 字节；元空间 p50 从 47383304 到 47473640，没有下降。GC 次数从每次 10 次变成 12 次。JDK 17 冷启动的堆 p50 从 65217392 到 19323072，元空间 p50 从 45417976 到 45510368，同样没有下降；GC 次数是 8、8、7 然后变成 9、9、8。close 不会卸载子加载器里已经定义的类，这组数字也不能当成卸载证据。重复启动的元空间在五个样本里还在往上走（JDK 8 上从 47797128 到 48819008），因为父加载器继续留着框架类。

诊断冒烟不进这组启动基线。调用方在 `Bootstrap.configureSystem` 之前写了 `config-20260924`。ORM、Mongo 和控制器事件都留着这个修订，没有被 `orm-1`、`mongo-1`、`web-1` 或 Bootstrap 的默认 `app-1` 盖掉。调用方没写修订时，Bootstrap 仍然写 `app-1`，模块单独启动时仍然用自己的 `orm-1` 或 `mongo-1`。显式 `schemaDigest()` 在 configure 里只算了 1 次。`ALTER TABLE` 加上 `extra_note` 再 `refresh` 之后，摘要从 `8a7dc706263c98a936ec6255dcd4929b7b53e1e3fbe71a25a75449eee88d4c7b` 变成 `5c68d4af815134731a0e8425846390f7c43cc33e9f9c1661fcc96aabb78daba9`。打开了 class 文件输出：`WebRecord` 的原始 class 是 518 字节，增强后是 6267 字节，两份 SHA-256 不同。没有 `emitSource`，所以没有 sources 目录。两个 JDK 的日志文件和进程标准输出里都有 `sf-phase-measure-marker`。

汇总在 `/tmp/sf-t15-final-phases/framework-phases-aggregate.json` 和同目录的 `framework-phases-aggregate.txt`。每个 JVM 的原始 JSON、日志和 JDK 8 冒烟的 class dump 也在这个目录。JDK 17 的 install 日志是 `/tmp/sf-t15-final-install.log`，聚焦测试日志是 `/tmp/sf-t15-final-tests.log`。私有仓库是 `/tmp/sf-t15-m2`，源码快照是 `/tmp/sf-t15-final-snapshot`。聚焦测试在两个 JDK 上都没有跳过：`StartupPhaseTraceTest` 2、`EnhancementPlanTest` 10、`OrmDiagnosticsLiveTest` 4、Mongo 那 3 个诊断用例、`ApplicationLifecycleTest` 18、`ApplicationDatabaseTest` 9。
