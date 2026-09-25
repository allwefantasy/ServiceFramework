# ServiceFramework

## 关联项目

- Projects 目录下的 ActiveORM（常被叫作 `active-ORM`）与本项目是关联项目。本机目录是 `~/projects/active_orm`，远程仓库是 `https://github.com/allwefantasy/active_orm`。
- 本仓库里的权威实现是 `serviceframework-orm`。独立仓库是同源的 ActiveORM，集成方式是启动时字节码增强，不是运行时远程调用。跨项目排查时先确认 `~/projects/active_orm` 存在，再读对应源码。
- 不要把独立 `active_orm` 的 jar 和 `serviceframework-orm` 放进同一个 classpath。外部仓库仍是更老的 Java / Hibernate 线，不能当成已经和本仓库的 JDK 8 / JDK 17 构建对齐。维护边界见 `docs/active-orm-maintenance.md`。
