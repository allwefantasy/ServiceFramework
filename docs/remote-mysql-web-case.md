# RemoteService MySQL 上的 Web 端到端用例

日期：2026-09-25。这一页记的是把 ServiceFramework 的 HTTP 服务拉起来，向 RemoteService 上的 MySQL 写入一行，再从库里读回来。它不替代 [compat-services.md](compat-services.md) 里的本机隔离库，也不在 2026-09-25 那次 1212 项矩阵里。那次矩阵用的是本机 `sf_compat`。

测试类是 `serviceframework-web/src/test/java/net/csdn/bootstrap/lifecycle/RemoteMysqlWebTest.java`。没有 `SF_REMOTE_MYSQL=true` 时它会跳过。跳过不是这次验收通过。

## 服务器

| 项 | 值 |
| --- | --- |
| SSH 别名 | `remoteservice` |
| 地址 | `192.168.110.116` |
| 主机名 | `williampc-MR-X5` |
| MySQL | 8.0.46，端口 `3306` |
| 库 | `sf_serviceframework_e2e` |
| 账号 | `sf_e2e` |
| 认证插件 | `mysql_native_password` |
| 驱动 | Connector/J 5.1.6 |

不要使用 `notebook`，也不要把 `root` 的远程账号改成别的认证插件。`root`@`%` 是 `caching_sha2_password`，5.1.6 连不上。`sf_e2e` 需要同时有 `@%`、`@127.0.0.1` 和 `@localhost`，权限只给 `sf_serviceframework_e2e`。口令只放在权限 `600` 的 env 文件里，不进仓库、不进日志、不进命令行。

这台 Mac 上的 Java 直接连 `192.168.110.116:3306` 会得到 `No route to host`。同一时刻 `nc` 和 Python 可以从 `192.168.110.45` 连上。所以 Java 走 SSH 本地转发，MySQL 看到的是服务器本机上的 `sf_e2e@127.0.0.1`。`127.0.0.1` 只表示这条转发，不是又开了一份本机 MySQL。

## env 文件

`SF_COMPAT_ENV_FILE` 指向这个文件。测试拒绝其它库名、其它账号，也拒绝除 `192.168.110.116` 和 `127.0.0.1` 以外的主机。

```text
SF_COMPAT_MYSQL_HOST=127.0.0.1
SF_COMPAT_MYSQL_PORT=13306
SF_COMPAT_MYSQL_USER=sf_e2e
SF_COMPAT_MYSQL_PASSWORD=<不写入本文>
SF_COMPAT_MYSQL_DATABASE=sf_serviceframework_e2e
```

直连服务器时把主机改成 `192.168.110.116`、端口改成 `3306`。当前这台 Mac 的 Java 做不到，要用转发：

```bash
ssh -f -N -M -S /tmp/sf-mysql-tunnel.sock -o ExitOnForwardFailure=yes \
  -L 127.0.0.1:13306:127.0.0.1:3306 remoteservice
```

用完执行 `ssh -S /tmp/sf-mysql-tunnel.sock -O exit remoteservice`。

## 用例在做什么

`httpSaveIsVisibleInRemoteMysql` 在 `sf_serviceframework_e2e` 里建表 `sf_web_lifecycle_record`（`id` 自增，`label` 最长 128）。然后用 `Bootstrap.configureSystem` 启动 HTTP，只加载 `net.csdn.bootstrap.lifecycle.web.db.orm` 里的控制器和模型，Mongo 关闭。JDBC 带 `useSSL=false`。

请求是 `GET /db/orm?q=<label>`。`OrmController` 把 `label` 存进去，再按 id 查出来。期望状态 `200`，正文是 `<label>:remote-mysql`。测试接着用同一份连接执行 `SELECT label`，必须等于刚才写入的值。结束时停掉 HTTP，并 `DROP TABLE`。停掉之后再 `SHOW TABLES`，结果应是 0。

## 怎么跑

在仓库根目录、转发已经就绪时执行。必须带 `-am`，让 Surefire 用这次 reactor 编出来的模块。只对 `serviceframework-web` 调 `surefire:test` 会拿到已安装的旧 jar，曾经出现 `EnhancementContext.completeClose` 找不到，以及日志配置异常。

```bash
export SF_REMOTE_MYSQL=true
export SF_COMPAT_ENV_FILE=/tmp/sf-remote-mysql.env
mvn -pl serviceframework-web -am test \
  -Dtest=RemoteMysqlWebTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

`-Dsurefire.failIfNoSpecifiedTests=false` 是因为 reactor 里其它模块没有这个类。不要把口令写进 Maven 参数。

## 2026-09-25 这一次

JDK `17.0.20.1`。转发端口 `13306`。驱动探到的库是 `sf_serviceframework_e2e`，当前用户 `sf_e2e@127.0.0.1`，版本 `8.0.46-0ubuntu0.22.04.4`，主机名 `williampc-MR-X5`。

`RemoteMysqlWebTest`：**1** 项，失败 **0**，错误 **0**，耗时 6.4 秒。日志里的连接是 `jdbc:mysql://127.0.0.1:13306/sf_serviceframework_e2e`，没有口令。Hibernate 使用 `MySQLDialect`，Druid 数据源完成初始化。同一次 Maven 里 web 模块的 ScalaTest 另有 **6** 项通过，那不是这个 MySQL 用例。

结束后表数量是 0，SSH 转发已关闭。
