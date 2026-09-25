# ActiveORM 维护主线

日期：2026-09-24。

ServiceFramework 里的 `serviceframework-orm` 是 ORM 的权威实现。独立仓库 `active_orm` 不在这次改动里，也没有发布新的 artifact。外部仓库当前仍是 Java 6 编译目标和更老的 Hibernate 线，不能把它写成已经兼容 JDK 8 / JDK 17。

两边要对齐的是同一份回归契约，而不是把外部仓库里的旧类拷回框架：

- 模型注册以二进制名为主键。`@Entity(name)` 和简单名只是别名。简单名只有唯一命中时才能解析，多个命中要明确失败，不能取第一个。
- `models.values()` 只包含每个模型类一次，不把别名再算成一条。
- 增强按 `entity-mapping` → `orm-query` / `association` 的依赖执行。规则只改 `CtClass`，类定义走应用包里的 `ServiceFrameworkPackageAnchor` 和 `ClassDefiner`。
- 物理表名来自显式 `@Table`，否则是简单名的下划线形式。二进制名不能当表名。
- 继承、关联、事务回滚、伴生查询分页和同简单名跨包解析，用同一组断言验收。框架侧的入口是 `OrmMysqlBusinessTest`，环境变量 `SF_ORM_MYSQL=true`，并读取 `SF_COMPAT_ENV_FILE`。没有这个开关时测试会跳过；跳过不是验收通过。
- 一个应用的 `EntityManagerFactory`、Druid 池和 Quill 上下文属于它自己的 `EnhancementContext`。关掉一个应用不能关掉另一个，也不能让禁用的应用借走别人的池。

类是否重复，不在 ORM 代码里再做一遍扫描。`dev/audit-runtime-dependencies.py` 检查这些前缀下的每个 class 只出现一次：`javassist/`、`com/google/inject/`、`javax/persistence/`、`javax/xml/bind/`、`javax/activation/`、`org/slf4j/`、`net/csdn/jpa/`。同一个前缀可以来自多个归档。Guice core 与 assistedinject、ORM 主类与测试类可以同时在 classpath 上，只要类名不重复。应用自己选一个 SLF4J 绑定也允许，条件同样是没有重复 class。框架解析到的 JPA、JAXB API、activation 和 SLF4J 是 API jar，不强制绑定。细节在 [jdk-compatibility.md](jdk-compatibility.md)。

ORM 这条边界是 `serviceframework-orm` 加上它依赖的 `serviceframework-common`。不要把独立 `active_orm` 的 jar 加进同一个 classpath。那个仓库这次没有改，也没有测。

同步外部仓库时，按一次明确的发布版本带走上述契约和对应测试，而不是在框架里悄悄换坐标。
