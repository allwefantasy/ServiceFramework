# 多数据库升级独立验收记录（2026-09-26）

这是本轮 Mongo 驱动升级与 PostgreSQL 支持的最终独立验收摘要。验收对象是三个仓库**未提交的最终工作区代码**，由独立于实现者的 verifier 直接读代码、跑真实 loopback 数据库得出；不依赖实现者自测报告。配置与迁移细节仍见 [mongodb-upgrade.md](mongodb-upgrade.md)、[mongo-compatibility.md](mongo-compatibility.md)、[postgresql-support.md](postgresql-support.md) 和 [active-orm-maintenance.md](active-orm-maintenance.md)。

## 实现范围

- Mongo：`org.mongodb:mongodb-driver-legacy` **5.13.0**（随带 `mongodb-driver-core`/`mongodb-driver-sync`/`bson` 5.13.0），落在框架 `serviceframework-mongo` 与独立 `mongomongo` 两处；`com.mongodb.Mongo` 统一迁到 `MongoClient`；`bson-record-codec`（Java 17 Record 编解码器，class major 61）在两个仓库都被 exclude，legacy `DB`/`DBCollection`/Document API 保持可用并实测覆盖。
- PostgreSQL：`{mode}.datasources.primary=postgres`（别名 `postgresql`）新增的 ORM 主数据源选择器，`postgres.*` 配置组、`multi-postgres.<name>` 命名池、`postgres.disable`、schema 裸写/双引号与 `jdbc.currentSchema` 一致性规则。框架与独立 `active_orm` 各自实现、各自验收，没有共享 classpath，也没有发布新 artifact。
- MySQL 保持默认：不写 `primary` 仍是 `mysql`，既有 `{mode}.datasources.mysql.*` 语义不变。框架侧驱动仍是 `mysql:mysql-connector-java:5.1.6`；独立 `active_orm` 把 `csdn-common` 传递的旧 5.1.6 排除，换成 `com.mysql:mysql-connector-j:8.4.0`。

## 实测版本

| 项 | 版本 |
| --- | --- |
| MongoDB（单节点副本集 `sfcompat`，auth on，库 `sf_compat`） | 8.3.11，mongosh 2.12.0 |
| PostgreSQL（Homebrew，随机 loopback 端口，库 `sf_compat`） | 17.11 |
| MySQL（loopback，库 `sf_compat`） | 8.0.46 |
| Mongo 驱动 | mongodb-driver-legacy/core/sync/bson 5.13.0 |
| PostgreSQL JDBC | org.postgresql:postgresql 42.7.13（standalone 为 `provided`） |
| JDK | OpenJDK 8 `1.8.0_504` 与 OpenJDK 17 `17.0.20.1` |

## 框架矩阵（同一产物，双 JDK）

`dev/verify-jdk-compatibility.sh` 六模块（common、orm、mongo、web、dispatcher、jetty-9-server）整条链 `RESULT pass`：

| 步骤 | 结果 |
| --- | --- |
| JDK 17 一次 `mvn install` 到隔离 m2 | exit 0 |
| animal-sniffer java18 签名 | exit 0 |
| dependency-tree / dependency-audit | exit 0，`AUDIT_OK`：184705 个可见类，无重复 key class，无非豁免 major>52；`bson-record-codec` 零命中 |
| JDK 8 同产物 surefire+scalatest | executed=1234, skipped=1, failures=0, errors=0（43 份报告） |
| JDK 17 同产物同上 | executed=1234, skipped=1, failures=0, errors=0 |
| class/jar SHA256（895 项） | build 后、JDK8 测试后、JDK17 测试后三份完全一致（同产物） |

唯一的 skipped 是 `RemoteMysqlWebTest`（`SF_REMOTE_MYSQL` opt-in 的 RemoteService 用例，需 SSH 转发），两个 lane 各 1 次，按文档定义跳过。关键 lane 双 JDK 均执行且全绿：`OrmPostgresBusinessTest` 6/6（含混合大小写 schema 写读、引号配置、错引擎命名池拒绝——后一条还要 `SF_ORM_MYSQL` lane）、`ApplicationPostgresTest` 2/2（HTTP 请求结束后裸 JDBC 断言行已提交、关闭后端口拒连）、`JdbcEngineSelectionTest` 8/8、`OrmFrameworkExtensionTest` 4/4、`MongoEnhancementLiveTest` 20/20（URI 优先于冲突离散项、非法 URI 不回显口令）、`OrmMysqlBusinessTest` 6/6、`ApplicationDatabaseTest` 9/9、`OrmContextIsolationTest` 7/7、`OrmDiagnosticsLiveTest` 4/4、`JdbcEndpointTest` 3/3、`MongoDriverLoggingLiveTest` 2/2、`QuillImportCompileTest` 1/1。显式打开的 lane 在 env 缺失时 `fail()` 而非 skip。

框架外聚焦探针（真实库、最终产物 classpath）：全部 PASS，覆盖下划线 LIKE 通配隔离、混合大小写元数据隔离、PG 时间/类型精确回读、命名池双向定型与错引擎拒绝（含动态注册池）、Mongo 5.13.0 驱动对 8.3.11 的 DB API。另有 runner 重试语义的 25/25 断言：那是从 `dev/mongo-modern-services.sh` 抽出真实 `ensure_archive`、mock curl 与校验的控制流测试，覆盖 partial 清理/续传/上限 die 等分支，不计入上面的用例总数；真实归档下载与校验是另一路证据（8.3.11 归档本身经同一路径落地）。

## 独立仓库（分开构建、分开验收）

- `active_orm`：JDK 8 与 JDK 17 各一次 `package` 成功，jar 全部 class major 52。实库 lane 双 JDK 全过：`PostgresBusinessTest` 6 + `PostgresMixedSchemaTest` 5（PostgreSQL 17.11，含混合大小写 `SfvMixed`、显式 `jdbc.currentSchema`、UUID/numeric/bool/timestamp/bytea/DATE）、`MysqlRegressionTest` 4（MySQL 8.0.46，默认数据源兼容）。打包 jar 的仓库外探针（DATE 回读、混合大小写元数据隔离）在双 JDK 均过。
- `mongomongo`：JDK 8 与 JDK 17 各一次 `package` 成功，jar 全部 class major 52。`MongoStandaloneLiveTest` 6/6 在 MongoDB 8.3.11 上双 JDK 全过（CRUD、过滤/计数/分页/排序、关联与内嵌、URI 优先、认证与连接失败、disable、close/disconnect、重复 configure 拒绝）；打包 jar 的非空过滤探针双 JDK 均过。
- 两个 standalone 的 opt-in lane 在 env 缺失时都是失败退出，不是静默跳过。

## 未覆盖 / 不主张

- `RemoteMysqlWebTest` 未跑（`SF_REMOTE_MYSQL` opt-in，需要到 RemoteService 的 SSH 转发）。
- scala-2.11/2.12 profile、JPMS module-path、`deploy`/`performRelease` 未跑。
- 只验收了上面列出的本机版本与 loopback 拓扑；不主张覆盖其它 MongoDB/PG/MySQL 版本、云托管、TLS 或任意遗留 API 组合。
- 没有任何部署、没有发布新 artifact；验收结束后无残留 mysqld/mongod/postmaster，探针 schema 已 drop。

## 复现入口

私有 loopback runner 都是仓库内入口：`dev/compat-services.sh`（MySQL）、`dev/mongo-modern-services.sh`（MongoDB 8.3.11）、`dev/pg-compat-services.sh`（PostgreSQL 17）。整链矩阵是 `dev/verify-jdk-compatibility.sh`，运行期依赖审计是 `dev/audit-runtime-dependencies.py`。各 lane 的具体命令见上面链接的三篇文档；命令模板按你自己的 `JDK8_HOME`/`JDK17_HOME` 与 Maven settings 替换。
