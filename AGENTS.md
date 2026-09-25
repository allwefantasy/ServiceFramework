# ServiceFramework

## 关联项目

- Projects 目录下的 ActiveORM（常被叫作 `active-ORM`）与本项目是关联项目。本机目录是 `~/projects/active_orm`，远程仓库是 `https://github.com/allwefantasy/active_orm`。
- 本仓库里的权威实现是 `serviceframework-orm`。独立仓库是同源的 ActiveORM，集成方式是启动时字节码增强，不是运行时远程调用。跨项目排查时先确认 `~/projects/active_orm` 存在，再读对应源码。
- 不要把独立 `active_orm` 的 jar 和 `serviceframework-orm` 放进同一个 classpath。外部仓库仍是更老的 Java / Hibernate 线，不能当成已经和本仓库的 JDK 8 / JDK 17 构建对齐。维护边界见 `docs/active-orm-maintenance.md`。

## RemoteService MySQL 端到端

把 HTTP 服务拉起来并访问 RemoteService 上 MySQL 的用例见 [docs/remote-mysql-web-case.md](docs/remote-mysql-web-case.md)。

- 测试类是 `RemoteMysqlWebTest`。只有 `SF_REMOTE_MYSQL=true` 才执行；没开这个开关时的跳过，不算这条链路通过。
- 只用库 `sf_serviceframework_e2e` 和账号 `sf_e2e`（`mysql_native_password`）。不要写 `notebook`，不要改 `root` 的认证插件。口令只放在权限 `600` 的 env 文件里。
- 这台 Mac 的 Java 不能直接连 `192.168.110.116:3306`。按文档走 SSH 转发；`127.0.0.1` 只是转发入口，MySQL 仍在 RemoteService 上。
- 跑的时候用 `mvn -pl serviceframework-web -am test`，不要单独对 web 模块调 `surefire:test`，否则会用到已安装的旧 jar。
