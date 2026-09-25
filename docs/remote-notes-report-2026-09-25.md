# remote-notes 部署测试报告

日期：2026-09-25。服务已经跑在 RemoteService 上。这台 Mac 直接请求 `http://192.168.110.116:19110`，没有再为 HTTP 做 SSH 转发。MySQL 仍是服务器本机的 `sf_serviceframework_e2e`，账号 `sf_e2e`。口令只在权限 `600` 的 env 文件里，本文不记录口令。

怎么重复发布、更新和回滚，见 [remote-notes-publish.md](remote-notes-publish.md)。这台 Mac 上临时拉起 HTTP、再经 SSH 转发去碰同一套库的旧用例，仍是 [remote-mysql-web-case.md](remote-mysql-web-case.md)，那不是这次的部署。

## 这个服务是干什么的

`remote-notes` 是用当前 ServiceFramework 写的一个笔记服务，模块在 `apps/remote-notes`。它启动 Jetty，扫描自己的控制器和 ActiveORM 模型，把标签和笔记写进 RemoteService 上的 MySQL。

一台笔记有标题和正文，可以挂到一个标签上。标签和笔记是一对多。服务用来把「框架打成可运行的包、放到 RemoteService、本机发请求、再单独换应用或换框架」这条路走通。它不是业务产品，表也只用 `sf_remote_tag` 和 `sf_remote_note`，不碰 `notebook`，也不改 `root` 的认证插件。

当前对外版本是应用 `1.1.0`，标记 `republished`。进程在服务器上，PID 在最后一次拉起时是 `1610994`，用的 JDK 是服务器上的 Temurin `17.0.19+10`。字节码仍是 Java 8。安装目录是 `/home/william-pc/softwares/serviceframework-remote-notes`，`current` 指向 `releases/20260925112110`。

## 对接的是 MySQL

服务跑在 RemoteService 上，JDBC 连的是这台机器自己的 `127.0.0.1:3306`。`127.0.0.1` 在这里是服务器本机，不是 Mac 上的转发。驱动仍是 Connector/J 5.1.6，`useSSL=false`。

`GET /health` 用 ActiveORM 的 `Model.findBySql` 执行：

```sql
SELECT DATABASE() AS db_name, CURRENT_USER() AS db_user, @@version AS db_version, @@hostname AS db_host
```

2026-09-25 最后一次读到的结果是：库 `sf_serviceframework_e2e`，用户 `sf_e2e@127.0.0.1`，版本 `8.0.46-0ubuntu0.22.04.4`，主机名 `williampc-MR-X5`。Hibernate 方言是 `MySQLDialect`，连接池是 Druid。建表语句只做 `CREATE TABLE IF NOT EXISTS`，更新时不删已有行。

## 测了哪些能力

下面每项都是从这台 Mac 对 `http://192.168.110.116:19110` 发的真实请求。应用 `1.0.1`、`1.1.0` 和随后的框架更新之后，同一套 `dev/remote-notes/exercise.sh` 都是 17 项通过、0 项失败。

| 请求 | 期望 | 结果 |
| --- | --- | --- |
| `GET /health` | 200，库名是 `sf_serviceframework_e2e` | 通过 |
| 健康检查里的框架身份 | ORM 和 Web 的 jar 名、64 位 SHA-256、同一份 buildId | 通过 |
| `POST /tags`，空名字 | 400，presence 校验失败 | 通过 |
| `POST /tags`，新名字 | 200，返回 id | 通过 |
| 再 `POST` 同一个名字 | 409，uniqueness 校验失败 | 通过 |
| `GET /tags/{id}` | 200，并带出这个标签下的笔记数 | 通过 |
| `POST /notes`，空标题 | 400 | 通过 |
| `POST /notes`，带 `tagId` | 200，笔记带回标签 id 和名字 | 通过 |
| 再建一条笔记 | 200 | 通过 |
| `GET /notes?tagId=&limit=1&offset=0` | `matched=2`，只返回 id 较小的那条 | 通过 |
| `GET /tags/{id}/notes` | 关联取出 2 条 | 通过 |
| `POST /notes/{id}` 改标题和正文 | 200，改完还能按 id 读到 | 通过 |
| `GET /notes/{id}` | 读到刚写进去的标题和正文 | 通过 |
| `POST /notes/{id}`，标题改成空 | 400，原来的标题还在 | 通过 |
| `DELETE /notes/{id}` | 200 | 通过 |
| 再 `GET` 被删的 id | 404 | 通过 |
| `GET` 没删的那条 | 200，标题仍是更新后的值 | 通过 |

应用从 `1.0.1` 换成 `1.1.0` 之后，更新前写下的笔记还在。笔记 `5` 的标题仍是 `edited-20260925112007`，正文是 `updated`，标签 id 是 `2`，标签名是 `lan-20260925112007`。框架 jar 换过一轮、又把 `current` 从旧发布切走再切回来，这条笔记还是这一份。

第一版 `1.0.0`（发布号 `20260925111759`）有一个请求时序问题：Jetty 先把响应写回客户端，请求结束时才提交事务。紧接着的下一次请求有时还看不见刚写的行；校验失败时，已经改过的托管对象还可能在提交时把空标题写进库。`1.0.1` 起，写成功会在返回前提交，校验失败会在返回前回滚。上面的 17 项是在这个修正之后跑的。

## 测了 ActiveORM 的哪些能力

模型是 `Tag` 和 `Note`，分别对应表 `sf_remote_tag`、`sf_remote_note`。启动时按 `application.model` 扫描，Javassist 增强叶子模型，主键是 `GenerationType.IDENTITY`。`@ManyToOne` 的外键列是 `tag_id`。每个被增强的包里都有已经编译好的 `ServiceFrameworkPackageAnchor`，JDK 17 上靠它定义增强后的类。

这次请求实际打到的 ActiveORM 能力：

- `Tag.create` / `Note.create`，再用 `save()` 插入。
- `presence`：空的标签名、空的笔记标题，`save()` 或 `update()` 返回失败，HTTP 400。
- `uniqueness`：重复标签名，HTTP 409。
- `findById`：按主键读标签和笔记，不存在是 404。
- `where` 加命名参数：按标签、按标题过滤。
- `order`、`limit`、`offset`、`fetch`：笔记列表按 id 分页。
- `count` 和 `count_fetch`：全表条数，以及当前过滤条件下的条数。
- `update`：改标题和正文后再 `findById`，读到的是新值。
- `delete`：删除后同一条 id 变成 404，别的行还在。
- `ManyToOne`：`note.attr("tag", tag)` 后 `save()`，读出来同时有 `tagId` 和 `tagName`。
- `OneToMany`：`tag.notes().count()` 和 `tag.notes().fetch()`，`GET /tags/{id}/notes` 的条数和按 `tagId` 过滤的列表一致。
- `Model.findBySql`：健康检查读 `DATABASE()`、`CURRENT_USER()`、`@@version`、`@@hostname`。

静态的 `where`、`findById`、`create`、`count` 是增强时加到叶子模型上的。控制器如果直接写 `Tag.where(...)`，javac 会绑到 `Model` 上那个抛 `AutoGeneration` 的方法，所以控制器经 `OrmCalls` 调增强后的类。`save`、`update`、`delete`、`attr` 走 `JPABase`，不经过这一层反射。

Mongo 在这份部署里关掉了。没有测 Mongo、Thrift、Dubbo。

## 应用和框架都换过

三次发布的健康检查如下。应用 jar 的 SHA-256 在 `1.1.0` 确定之后没变；框架 jar 只在 `publish.sh framework` 时变。

| 发布号 | 命令 | 应用 | 框架 buildId | ORM jar SHA-256 |
| --- | --- | --- | --- | --- |
| `20260925111759` | `publish.sh all` | `1.0.0` / `baseline` | `20260925111759` | 这一版后来被换掉 |
| `20260925112007` | `publish.sh app` | `1.0.1` / `committed` | 仍是 `20260925111759` | 未换框架 |
| `20260925112043` | `publish.sh app` | `1.1.0` / `republished` | 仍是 `20260925111759` | `ecbacb81fdae3de9637c175f136ace19111a1513f86ce8aabe94311e726c445e` |
| `20260925112110` | `publish.sh framework` | 仍是 `1.1.0` / `republished` | `20260925112110` | `62aacbb26b2d521026376d4b67a4c4f8db6b6e9f83f0e6691eb06b4fee7371d3` |

`1.1.0` 那次应用更新之后，Web 模块 jar 的 SHA-256 是 `5ba0a76d818dc9a386d63e01cbe76b662ec3854ec8e1c5f755180af65605769a`。框架更新之后变成 `f5b6a10369e3e134d621a01332b4cadac2bbb8a7b126e16e8d3e7185a752b4f8`。应用 jar `remote-notes.jar` 在这两次里都是 `c6d05e57c07752b07827b82b01eda06beac7633e238082fc3485d4398921385c`。健康检查读的是进程实际加载的 jar，不是发布目录里另放的一份清单。

框架更新重新执行了 `mvn -pl apps/remote-notes -am install`，并把 `META-INF/sf-framework-build.txt` 写进 `serviceframework-common`、`serviceframework-orm`、`serviceframework-web`、`serviceframework-jetty-9-server` 这四个 jar。ORM 和 Web 的 buildId 都变成 `20260925112110`，然后 17 项请求再次通过。

回滚也做了一次。`current` 从 `20260925112110` 改到 `20260925112043` 再启动，健康检查回到应用 `1.1.0`、框架 buildId `20260925111759`、ORM SHA-256 前缀 `ecbacb81fdae`。再切回 `20260925112110`，buildId 回到 `20260925112110`，笔记 `5` 仍在。现在线上是这次切回来的进程。
