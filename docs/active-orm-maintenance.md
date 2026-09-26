# ActiveORM 维护主线

## 2026-09-26 当前状态

ServiceFramework 里的 `serviceframework-orm`（含它依赖的 `serviceframework-common`）仍是 ORM 的权威实现。2026-09-26 这一轮改动同时落在框架与独立仓库 `active_orm`（`~/projects/active_orm`，远程 `allwefantasy/active_orm`）：两边各自独立实现、各自跑自己的测试，没有共享 classpath，也没有发布新的 standalone artifact。独立验收已完成：框架侧六模块同一产物双 JDK 矩阵通过，standalone 在 JDK 8 / JDK 17 上分别构建并跑通自己的实库 lane，汇总见 [multi-database-verification-2026-09-26.md](multi-database-verification-2026-09-26.md)。

PostgreSQL 主数据源是本轮（2026-09-26）在**两个仓库各自新增**的能力，不是历史特性，两边是各自的实现和各自的验收，不能互相借用结论：

- 框架侧：`{mode}.datasources.primary=postgres`（别名 `postgresql`）、`{mode}.datasources.postgres.*` 组和 `multi-postgres.<name>` 命名池。聚焦入口是 `OrmPostgresBusinessTest`（`SF_ORM_PG=true`，需 `SF_COMPAT_PG_ENV_FILE`；其中错引擎命名池用例还要求 `SF_ORM_MYSQL=true` 和 `SF_COMPAT_ENV_FILE`）与 `ApplicationPostgresTest`（`SF_WEB_PG=true`）。详见 [postgresql-support.md](postgresql-support.md)。
- standalone 侧：同样的 `primary` / `postgres.*` / `multi-postgres.*` 形态，但跑在自己那条更老的 Hibernate 4.1 线（框架是 5.3.7.Final）和 JVM 级单一全局 `JPA` 上下文上，不是框架的 per-context 模型，不要写成对等。入口是 `PostgresBusinessTest` / `PostgresMixedSchemaTest` / `MysqlRegressionTest`（`SF_ORM_PG=true` / `SF_ORM_MYSQL=true`，env 缺失时失败而非跳过）。细节见 [../../active_orm/docs/postgresql-support.md](../../active_orm/docs/postgresql-support.md)。

独立仓库本轮的其它实际变化：

- 编译目标从 Java 6 升到 Java 8（模型类要求 Java-8 字节码，JDK 9+ 需要包内 `ActiveOrmPackageAnchor` 辅助定义）。独立验收在 OpenJDK 8 与 17 上各跑了一遍 `package` 与实库 lane，产出的 jar 全是 class major 52。
- MySQL 仍是默认主数据源：不写 `primary` 时 API 与 `{mode}.datasources.mysql.*` 配置语义保持兼容。变化的是驱动坐标——`csdn-common` 传递的 `mysql:mysql-connector-java:5.1.6` 被排除，换成 `com.mysql:mysql-connector-j:8.4.0`（5.1.6 使用的 `tx_isolation` 在 MySQL 8 已改名 `transaction_isolation`）。框架侧的 MySQL 驱动仍是原有的 `mysql:mysql-connector-java:5.1.6`，不跟着改。
- schema 配置同时接受裸名与双引号写法，JDBC 元数据按精确名匹配，`hibernate.default_schema` 与 JDBC `currentSchema`/`search_path` 按同一规则渲染标识符（普通小写名原样、其余加双引号），混合大小写 schema 下增强模型写路径可用；这一点两边各自在真实 PostgreSQL 17.11 上验过。

公开 API 与上下文模型不同，不要写成对等：

- 框架侧是每个应用一套 `EnhancementContext`/`OrmSession`：各自的配置、`EntityManagerFactory`、Druid 池和 Quill 上下文，关掉一个应用不影响另一个，禁用应用不能借别人的池。
- standalone 是 JVM 级单一全局 `JPA.configure`/`JPAConfig`：一次只有一个配置，`JPA.shutdown` 关闭全部已注册上下文、EMF 与命名池。没有 per-request 上下文，也不与框架共享公共 API。

## 2026-09-24 边界（历史记录，保留）

当时的约定：ServiceFramework 里的 `serviceframework-orm` 是 ORM 的权威实现；独立 `active_orm` 不在那次改动里，也没有发布新的 artifact，当时仍是 Java 6 编译目标和更老的 Hibernate 线，不能写成已兼容 JDK 8 / JDK 17。这些判断只描述 2026-09-24 时点，现状以 2026-09-26 一节为准。

两边要对齐的是同一份回归契约，而不是把外部仓库里的旧类拷回框架：

- 模型注册以二进制名为主键。`@Entity(name)` 和简单名只是别名。简单名只有唯一命中时才能解析，多个命中要明确失败，不能取第一个。
- `models.values()` 只包含每个模型类一次，不把别名再算成一条。
- 增强按 `entity-mapping` → `orm-query` / `association` 的依赖执行。规则只改 `CtClass`，类定义走应用包里的 `ServiceFrameworkPackageAnchor` 和 `ClassDefiner`。
- 物理表名来自显式 `@Table`，否则是简单名的下划线形式。二进制名不能当表名。
- 继承、关联、事务回滚、伴生查询分页和同简单名跨包解析，用同一组断言验收。框架侧的 MySQL 入口是 `OrmMysqlBusinessTest`，环境变量 `SF_ORM_MYSQL=true`，并读取 `SF_COMPAT_ENV_FILE`。没有这个开关时测试会跳过；跳过不是验收通过。
- 一个应用的 `EntityManagerFactory`、Druid 池和 Quill 上下文属于它自己的 `EnhancementContext`。关掉一个应用不能关掉另一个，也不能让禁用的应用借走别人的池。

类是否重复，不在 ORM 代码里再做一遍扫描。`dev/audit-runtime-dependencies.py` 检查这些前缀下的每个 class 只出现一次：`javassist/`、`com/google/inject/`、`javax/persistence/`、`javax/xml/bind/`、`javax/activation/`、`org/slf4j/`、`net/csdn/jpa/`。同一个前缀可以来自多个归档。Guice core 与 assistedinject、ORM 主类与测试类可以同时在 classpath 上，只要类名不重复。应用自己选一个 SLF4J 绑定也允许，条件同样是没有重复 class。框架解析到的 JPA、JAXB API、activation 和 SLF4J 是 API jar，不强制绑定。细节在 [jdk-compatibility.md](jdk-compatibility.md)。

ORM 这条边界是 `serviceframework-orm` 加上它依赖的 `serviceframework-common`。不要把独立 `active_orm` 的 jar 加进同一个 classpath；两个仓库的同名类来自不同代增强器，混在一起会冲突。

同步外部仓库时，按一次明确的发布版本带走上述契约和对应测试，而不是在框架里悄悄换坐标。
