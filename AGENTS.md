# ServiceFramework

## 关联项目

- Projects 目录下的 ActiveORM（常被叫作 `active-ORM`）与本项目是关联项目。本机目录是 `~/projects/active_orm`，远程仓库是 `https://github.com/allwefantasy/active_orm`。
- 本仓库里的权威实现是 `serviceframework-orm`。独立仓库是同源的 ActiveORM，集成方式是启动时字节码增强，不是运行时远程调用。跨项目排查时先确认 `~/projects/active_orm` 存在，再读对应源码。
- 不要把独立 `active_orm` 的 jar 和 `serviceframework-orm` 放进同一个 classpath。独立仓库现已是 Java 8 编译目标，并在 JDK 8 / JDK 17 上各自独立构建与实库验收；但它仍是自己那条更老的 Hibernate 线和 JVM 级单一全局 `JPA` 上下文，不是框架的 per-context 模型，两边的验收结论也不能互相借用。维护边界见 `docs/active-orm-maintenance.md`。

## RemoteService MySQL 端到端

把 HTTP 服务拉起来并访问 RemoteService 上 MySQL 的用例见 [docs/remote-mysql-web-case.md](docs/remote-mysql-web-case.md)。

- 测试类是 `RemoteMysqlWebTest`。只有 `SF_REMOTE_MYSQL=true` 才执行；没开这个开关时的跳过，不算这条链路通过。
- 只用库 `sf_serviceframework_e2e` 和账号 `sf_e2e`（`mysql_native_password`）。不要写 `notebook`，不要改 `root` 的认证插件。口令只放在权限 `600` 的 env 文件里。
- 这台 Mac 的 Java 不能直接连 `192.168.110.116:3306`。按文档走 SSH 转发；`127.0.0.1` 只是转发入口，MySQL 仍在 RemoteService 上。
- 跑的时候用 `mvn -pl serviceframework-web -am test`，不要单独对 web 模块调 `surefire:test`，否则会用到已安装的旧 jar。
- 这一节是 Mac 上临时起 HTTP，经 SSH 转发访问 RemoteService 的 MySQL。服务本身部署在 RemoteService 上、再从本机发 HTTP 请求，不是这条用例。

## RemoteService 上的 remote-notes

笔记服务模块是 `apps/remote-notes`。它用当前框架和 ActiveORM，在 RemoteService 本机连 MySQL `sf_serviceframework_e2e` / `sf_e2e`，HTTP 是 `http://192.168.110.116:19110`。

- 服务是干什么的、测了哪些 HTTP 和 ActiveORM 能力、MySQL 读数、应用与框架如何分别更新：见 [docs/remote-notes-report-2026-09-25.md](docs/remote-notes-report-2026-09-25.md)。
- 第一次发布、只更新应用、只更新框架、回滚：见 [docs/remote-notes-publish.md](docs/remote-notes-publish.md)。命令是仓库根目录的 `dev/remote-notes/publish.sh all|app|framework`，验收是 `dev/remote-notes/exercise.sh`。
- 安装目录是 `/home/william-pc/softwares/serviceframework-remote-notes`。`current` 指向 `releases/<发布号>`。口令只在权限 `600` 的 env 文件里，不进仓库。
- 不要把这次部署和上面的 `RemoteMysqlWebTest` 混成一次验收。部署不代替 1212 项矩阵，也不代替本机 `sf_compat`。
