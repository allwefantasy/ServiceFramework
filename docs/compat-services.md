# 本机 MySQL / MongoDB 兼容测试服务

日期：2026-09-24 记录把本机 MySQL / MongoDB 拉起来。这一页当时没有跑 Maven，也不能单独当成产品验收。2026-09-25 的整仓矩阵后来用了同一套 MySQL 8.0.46、MongoDB 4.4.29、回环和库名 `sf_compat`，见 [serviceframework-bytecode-migration.md](serviceframework-bytecode-migration.md)。

服务只监听 `127.0.0.1` 的自动空闲端口，数据目录在用户缓存里的独立实例中。账号和口令只写在该实例的 `env` 文件（权限 `600`）。本文和 `status` 输出都不包含这些值。

## 命令

在仓库根目录执行：

```bash
dev/compat-services.sh start
dev/compat-services.sh status
dev/compat-services.sh verify
dev/compat-services.sh mysql-cli -- --batch -e "SELECT 1"
printf '%s\n' 'print("COMPAT_OK")' | dev/compat-services.sh mongo-eval
dev/compat-services.sh stop
dev/compat-services.sh run -- <command>
```

`start` 会复用已有实例并在退出后保持服务运行。`run` 会先启动，再执行命令，然后只停止本次记录的进程。`stop` 不按进程名结束进程：它只向 pid 文件旁的记录核对过的进程发信号。核对内容是可执行文件路径、数据目录和启动时间。对不上就不发信号。

不要把 `env` 的内容打印到终端、日志或对话里，也不要打开 `set -x`。`mysql-cli` 和 `mongo-eval` 会自己读取凭据，不把口令放进命令行。MySQL 客户端历史被设到 `/dev/null`。Mongo shell 的 home 被指到实例私有目录，用完删除 `.dbshell`。

## 位置

| 用途 | 路径 |
| --- | --- |
| 二进制缓存 | `~/Library/Caches/serviceframework-compat-services/dist` |
| 官方归档 | `~/Library/Caches/serviceframework-compat-services/downloads` |
| 当前实例指针 | `~/Library/Caches/serviceframework-compat-services/active-instance` |
| 本次实例 | `~/Library/Caches/serviceframework-compat-services/instances/20260924T182153-8942` |
| 无凭据 manifest | 实例目录下的 `manifest.json` |
| 凭据文件 | 实例目录下的 `env` |

`status` 会打印 manifest 和 env 的路径、端口以及是否在运行。MySQL Unix socket 放在 `/tmp/sfcs-<实例 id>/mysql.sock`。实例目录的完整路径超过 macOS socket 路径上限（103 字节），所以 socket 不能放在数据目录旁边。`/tmp` 下这个目录权限是 `700`，并且脚本拒绝把符号链接当成 socket 目录。

本次实际端口是 MySQL `63714`、MongoDB `63811`。脚本拒绝 `3306`、`27017`、`33060` 和 `33062`。副本集已经按 `127.0.0.1:<端口>` 初始化，之后不要改 MongoDB 端口。

## 验证用的环境变量名

`env` 里有这些名字：`SF_COMPAT_MYSQL_HOST`、`SF_COMPAT_MYSQL_PORT`、`SF_COMPAT_MYSQL_USER`、`SF_COMPAT_MYSQL_PASSWORD`、`SF_COMPAT_MYSQL_ADMIN_PASSWORD`、`SF_COMPAT_MYSQL_DATABASE`、`SF_COMPAT_MYSQL_SOCKET`、`SF_COMPAT_MYSQL_CLIENT_CNF`、`SF_COMPAT_MYSQL_ADMIN_CNF`、`SF_COMPAT_MONGO_HOST`、`SF_COMPAT_MONGO_PORT`、`SF_COMPAT_MONGO_USER`、`SF_COMPAT_MONGO_PASSWORD`、`SF_COMPAT_MONGO_AUTH_DB`、`SF_COMPAT_MONGO_DATABASE`、`SF_COMPAT_MONGO_REPLSET`。

数据库名是 `sf_compat`，不是示例配置里的 `wow`。没有导入 `sql/wow.sql`，也没有连接任何已有开发库。MongoDB 认证库是 `admin`，副本集名是 `sfcompat`。这是单节点副本集，这样可以做多文档事务。开启授权时，MongoDB 4.4 要求副本集同时提供 keyFile；key 在实例目录的 `mongo.key`，权限 `600`，不是客户端口令。

`run` 只向子进程导出路径和端口：`SF_COMPAT_ENV_FILE`、`SF_COMPAT_MANIFEST`、主机、端口、库名和副本集名。不导出口令。

manifest 只有版本、程序路径、`127.0.0.1`、端口、数据目录、pid 文件、env 路径和副本集名。

## 选用的官方版本

机器是 macOS 26.5，x86_64。没有使用 Docker、podman、colima 或 `brew services`，也没有改系统服务配置。

MySQL 用的是 Community Server 8.0.46 的 macOS 15 x86_64 压缩包，不是 8.4 或 9.0。8.0.46 仍带 `mysql_native_password`；8.4 默认关掉它，9.0 去掉它。当前仓库的 Connector/J 5.1.6 不能使用 `caching_sha2_password`。终验没有把驱动改成 8.0.33，用的就是 5.1.6 连这台 8.0.46。下载页当时选中的 GA 版本是 8.0.46，平台是 macOS 15（x86, 64-bit），文件是 `mysql-8.0.46-macos15-x86_64.tar.gz`。

来源：<https://dev.mysql.com/downloads/mysql/8.0.html>，直链 <https://dev.mysql.com/get/Downloads/MySQL-8.0/mysql-8.0.46-macos15-x86_64.tar.gz>。

| 校验 | 值 |
| --- | --- |
| 页面公布的 MD5 | `a86ee80b624c8574d56ebf0e5e75f8c3` |
| 下载后计算的 SHA256 | `8590bc6c3203fe17f51f757dc9132fb755d1b73cbdad9d12a67f85025a3c3b8f` |

页面没有单独给出 SHA256。脚本同时检查 MD5 和这份本地 SHA256。服务器配置把 `default-authentication-plugin` 和 `authentication-policy` 都设成 `mysql_native_password`。启动后查询这两个变量，结果都是 `mysql_native_password`。8.0.46 会提示该插件已弃用，但这次启动没有拒绝它。

MongoDB 用的是 4.4.29 的 macOS x86_64 社区版。终验解析到的 Java 驱动是 `mongo-java-driver` **3.12.14**，连的就是这台 4.4.29。更早曾用 2.11.4 和它的 `OP_QUERY` 来解释为什么不选 6.x；那不是现在的驱动。6.x / 7.x 没有进入这次矩阵。官方下载页当天的 4.4 条目是 4.4.31，列出的平台只有 Linux 和 Windows，没有 macOS。`fastdl.mongodb.org` 上仍有 4.4.29 的 macOS x86_64 归档，而且官方旁边就有校验文件。这个二进制在本机可以执行。

来源：<https://fastdl.mongodb.org/osx/mongodb-macos-x86_64-4.4.29.tgz>，以及同目录的 `.md5` 和 `.sha256`。

| 校验 | 值 |
| --- | --- |
| MD5 | `711f0d25e5ec95e17085ddb9176510e4` |
| SHA256 | `bf4ab974dec29e70a8b4049260375fc6c671e676f139b55678e5a41c9851aa50` |

两个校验值都和官方 sidecar 一致。本机没有 OpenSSL 1.1；这两个官方包不需要它。MySQL 使用归档自带的 `libssl.3`，MongoDB 4.4.29 链接系统的 `libcurl`、`libSystem`、CoreFoundation 和 Security。

## 本轮实际执行

`mysqld --version` 退出码 0：`Ver 8.0.46 for macos15 on x86_64 (MySQL Community Server - GPL)`。

`mongod --version` 退出码 0：`db version v4.4.29`。

`dev/compat-services.sh verify` 退出码 0。MySQL 客户端退出码 0，读到版本 `8.0.46`，`SELECT 1` 级别的 ping、写入后读回、以及事务回滚后原值仍在，三项都成功。Mongo shell 退出码 0，ping、写入后读回、以及单节点副本集上的事务回滚都成功，版本是 `4.4.29`。`lsof` 只看到 `127.0.0.1:63714` 和 `127.0.0.1:63811`。

随后 `stop` 退出码 0。再次 `start` 后 `verify` 仍是退出码 0。最后一次 `stop` 之后，上述端口已关闭，进程列表里没有这两个发行包里的 `mysqld` 或 `mongod`。二进制缓存、实例数据、脚本和这份说明都保留着。实例还在的话，直接 `dev/compat-services.sh start`。

这一节证明的是 2026-09-24 的官方服务器进程、官方客户端、回环端口和一次最小读写/回滚。当时没有运行 ServiceFramework 的 JDK 8/17 业务测试。产品验收是后来的整仓矩阵，使用了同一组服务器版本，不能把这一节本身写成那个矩阵。
