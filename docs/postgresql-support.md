# PostgreSQL 数据源支持

本文只描述本仓库的 `serviceframework-common`、`serviceframework-orm` 和 `serviceframework-web`。独立仓库 `active_orm` 不是这份文档的验收对象，也不能据此认为它已经同步了 PostgreSQL 实现；同步边界仍见 [active-orm-maintenance.md](active-orm-maintenance.md)。

## 选择器和 MySQL 兼容

`{mode}.datasources.primary` 选择 ORM 主数据源：

- 不写该键时仍是 `mysql`，已有 `{mode}.datasources.mysql.*` 配置保持原语义。
- 写 `postgres` 时使用 `{mode}.datasources.postgres.*`；选择器也接受 `postgresql` 作为别名。
- `{mode}.datasources.mysql.disable` 仍然是旧的 MySQL 开关。它不是新的主数据源选择器，`primary=postgres` 时不会让 PostgreSQL 被当成 MySQL 禁用。
- `{mode}.datasources.postgres.disable=true` 只关闭 PostgreSQL 主数据源。

扩展能力按选择结果发布：ORM 总是提供 `datasource.orm`；MySQL 主数据源时提供 `datasource.mysql`，PostgreSQL 主数据源时提供 `datasource.postgres`。`OrmFrameworkExtension.CAPABILITY` 保持旧值 `datasource.mysql` 供已有扩展声明依赖，但 PG 模式下不会把 `datasource.mysql` 提供给依赖图。

## PostgreSQL 配置

最小应用配置：

```yaml
development:
  datasources:
    primary: postgres
    postgres:
      host: <POSTGRES_HOST>
      port: <POSTGRES_PORT>
      database: <POSTGRES_DATABASE>
      username: <POSTGRES_USER>
      password: <POSTGRES_PASSWORD>
      schema: public
      show_sql: false
      initialSize: 1
      minIdle: 0
      maxActive: 8
      jdbc:
        connectTimeout: "5"   # 单位是秒
        sslmode: "disable"
```

占位值要由部署环境或自己的配置模板替换；不要把真实口令写进仓库或日志。`schema` 可省略，缺省是 `public`。如果写 `engine` 或 `type`，它必须仍是 `postgres`。`jdbc.*` 原样进入 JDBC URL；`schema` 与 `jdbc.currentSchema` 同时存在时必须一致。pgjdbc 的 `connectTimeout` 单位是**秒**，和 MySQL 驱动里毫秒含义不同，写一个小的秒数值即可；MySQL 侧的 `jdbc.connectTimeout` 语义不变。

`schema` 配置接受裸名或包一层双引号（`"SfvMixed"`）的写法，元数据查询都按剥掉引号的精确名匹配。不是安全小写标识符的 schema 名（混合大小写等），`hibernate.default_schema` 与 URL 里的 `currentSchema` 会按同一规则加双引号渲染，指向同一个物理 schema；显式写的 `jdbc.currentSchema` 即使不带引号也走同一条规范化，不会绕过加引号。

驱动是 `org.postgresql:postgresql:42.7.13`，由根 POM 管理，`serviceframework-common` 声明依赖，ORM/Web 通过该模块拿到它。默认 Hibernate 方言是 `org.hibernate.dialect.PostgreSQL95Dialect`；默认 `DBType` 是 `net.csdn.jpa.type.impl.PostgresType`。自定义 `type_mapping` 仍可用，但必须是 `DBType` 实现。

MySQL 默认配置不变：

```yaml
development:
  datasources:
    mysql:
      host: <MYSQL_HOST>
      port: <MYSQL_PORT>
      database: <MYSQL_DATABASE>
      username: <MYSQL_USER>
      password: <MYSQL_PASSWORD>
      disable: false
```

## 类型、原生 SQL 和 Quill

`PostgresType` 覆盖常用的 `UUID`、`NUMERIC` / `DECIMAL`、`TIMESTAMP` / `TIMESTAMPTZ`、`DATE`、`BYTEA`、布尔、整数和文本类型。`DBInfo` 只读取选中的 schema，并把 PostgreSQL 的驱动类型名规范化后交给它。标识符引用在 PG 下使用双引号。

原生 SQL 主入口仍是 `Model.nativeSqlClient()`；`JPABase.mysqlClient` 保留 MySQL 专用语义，在 `primary=postgres` 下会明确失败。PG 主数据源可用 `JPABase.postgresClient()` 或 `OrmSession.current().postgresClient()`。

Quill 的 PG 入口是有类型的：

```scala
val ctx = QuillDB.postgresCtx
import ctx._
```

`QuillDB.ctx` 继续只用于 MySQL 主数据源。命名 PG 数据源放在 `{mode}.datasources.multi-postgres.<name>`：

```yaml
development:
  datasources:
    multi-postgres:
      report:
        host: <POSTGRES_HOST>
        port: <POSTGRES_PORT>
        database: <POSTGRES_DATABASE>
        username: <POSTGRES_USER>
        password: <POSTGRES_PASSWORD>
        schema: <REPORT_SCHEMA>
```

之后可用 `Model.nativeSqlClient("report")`，或建 `QuillDB.createNewPostgresCtxByNameFromYml("report")`。字符串入口是 `createNewPostgresCtxByNameFromStr(name, snippet)`。命名池表是跨引擎扁平的：每个注册的池都记录自己的 engine，定型入口在包 context 之前核对，`createNewCtxByNameFromYml` 取到 PostgreSQL 池或 `createNewPostgresCtxByNameFromYml` 取到 MySQL 池都会以 `CONFIGURATION`（expected/actual engine）拒绝；`Model.nativeSqlClient(name)` 仍是不定型的通用路由。主池、命名池、`EntityManagerFactory` 和 Quill context 都归当前 `EnhancementContext` / `OrmSession`；应用关闭时按逆序释放，不会借用另一个应用的池。

## 隔离自测

PostgreSQL runner 是 `dev/pg-compat-services.sh`。它使用本机 PostgreSQL bin（默认 `/usr/local/opt/postgresql@17/bin`，可用 `SF_COMPAT_PG_BIN` 改），在用户缓存下建私有实例、随机回环端口、模式 `600` 的 `postgres.env`，`run` 结束时只停止自己记录的 postmaster，不使用 `5432`。

`verify` 需要一个活着的私有实例，所以用 `run` 把它包起来（`run` 会自己起实例、注入 `SF_COMPAT_PG_ENV_FILE`、结束时停掉）：

```bash
cd "$HOME/projects/ServiceFramework"
dev/pg-compat-services.sh run -- dev/pg-compat-services.sh verify
```

聚焦跑 ORM 与 Web PG 路径。`JDK17_HOME` 指向你自己的 JDK 17（JDK 8 lane 换成 `JDK8_HOME`）；`mvn` 用默认 settings，需要自定义 mirror 时单独加 `-s <your-settings.xml>`：

```bash
JDK17_HOME=/path/to/your/jdk17
dev/pg-compat-services.sh run -- env \
  JAVA_HOME="$JDK17_HOME" PATH="$JDK17_HOME/bin:$PATH" \
  SF_ORM_PG=true SF_WEB_PG=true \
  mvn -pl serviceframework-web -am test \
  -Dtest='JdbcEngineSelectionTest,OrmPostgresBusinessTest,QuillImportCompileTest,JdbcEndpointTest,OrmFrameworkExtensionTest,ApplicationPostgresTest' \
  -Dsurefire.failIfNoSpecifiedTests=false
```

`SF_ORM_PG=true` 或 `SF_WEB_PG=true` 打开后，`SF_COMPAT_PG_ENV_FILE` 缺失就是失败；没打开时的跳过不算 PG 验收。`OrmPostgresBusinessTest.wrongEngineNamedQuillLookupIsRejected` 是混合引擎用例，还要同时打开 `SF_ORM_MYSQL=true` 并由 `dev/compat-services.sh run` 注入 `SF_COMPAT_ENV_FILE`（两个 runner 嵌套：外层起 MySQL，内层起 PG）；MySQL 开关没开时只有这一条用例跳过，同类的其它 PG 用例照常执行。MySQL 回归仍由 `dev/compat-services.sh run` 注入 `SF_COMPAT_ENV_FILE`，并使用 `SF_ORM_MYSQL=true` / `SF_COMPAT_WEB_DB=true`；PG 通过不替代 MySQL 通过。

最终独立验收已完成：上述 PG lane 在 PostgreSQL 17.11 上双 JDK（8 / 17）全绿（`OrmPostgresBusinessTest` 6/6、`ApplicationPostgresTest` 2/2、`JdbcEngineSelectionTest` 8/8、`OrmFrameworkExtensionTest` 4/4），含在六模块同一产物矩阵内（矩阵 1234 executed + 1 skipped / lane）；汇总见 [multi-database-verification-2026-09-26.md](multi-database-verification-2026-09-26.md)。
