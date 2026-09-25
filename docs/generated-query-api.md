# ServiceFramework 伴生查询 API

这是 ORM 的一组**显式声明**查询，不是动态 finder 的替代品，也不覆盖 DTO、连接或任意 JPQL。普通 Java / Scala 源码调用生成出来的 `模型名Queries` 类；模型类本身不被插入新方法。

旧的 `findByX` / `where` 动态入口还在。整体集成已经改过 `JPA`、`JPQL` 和增强器：实体名、应用上下文和类定义都走现在的 ORM 会话。处理器自己不改模型字节码，也不修改 `serviceframework-common`。加载和上下文见 [orm-compatibility.md](orm-compatibility.md)，整仓范围见 [serviceframework-bytecode-migration.md](serviceframework-bytecode-migration.md)。

## 类型

| 类型 | 作用 |
| --- | --- |
| `net.csdn.jpa.query.GenerateQueries` | `@QueryMethod` 的容器注解，`RetentionPolicy.RUNTIME` |
| `net.csdn.jpa.query.QueryMethod` | 一条查询声明，可重复，同样是运行时可见 |
| `net.csdn.jpa.query.ServiceFrameworkQueryProcessor` | 注解处理器，生成同包伴生类和包锚点 |
| `net.csdn.jpa.query.QueryMetadata` | 一个模型上全部已校验方法的不可变描述 |
| `net.csdn.jpa.query.QueryDescriptor` | 一个方法的不可变描述 |
| `net.csdn.jpa.query.RuntimeQueryMetadata` | 增强完成之后，用注解和反射重新读出 `QueryMetadata` |
| `net.csdn.jpa.query.GeneratedQueryExecutor` | 执行伴生方法，绑定命名参数 |
| `net.csdn.jpa.query.QueryLimits` | 方法数、字段数、生成体积和分页上限 |

处理器和执行器共用 `QuerySchemas` 做字段、类型、运算符和排序校验，共用 `QueryJpql` 渲染 JPQL。没有第二套字符串拼接规则。处理器只看 `javax.lang.model`，不会 `Class.forName` 目标模型。

## 声明

```java
@GenerateQueries({
    @QueryMethod(
        name = "findByStatusAndTenant",
        fields = {"status", "tenantId"},
        orderBy = {"id"}
    )
})
public class OrderEntity extends OrderEntityBase {
    private Long id;
    private String status;
    private Long tenantId;
}
```

也可以直接重复 `@QueryMethod`，由编译器包进容器。只支持顶层类，并且必须在具名包里。

`fields` 是 AND 相等条件，顺序就是生成方法的参数顺序。`orderBy` 的每一项只能是下面三种之一：

- `field`
- `field asc`
- `field desc`

方向大小写不敏感。不写 `orderBy` 就不生成 `ORDER BY`。每个生成方法固定再加 `int offset, int limit`。没有隐式的“查全部”。

生成结果在同包，例如 `net.csdn.jpa.query.fixture.OrderEntityQueries`。每个声明生成两个重载：一个不带 EntityManager（运行时取 `JPA.getJPAConfig().getJPAContext().em()`），一个把调用方自己的 `EntityManager` 作为第一个参数：

```java
public static java.util.List<net.csdn.jpa.query.fixture.OrderEntity>
findByStatusAndTenant(java.lang.String p0, java.lang.Long p1, int offset, int limit)

public static java.util.List<net.csdn.jpa.query.fixture.OrderEntity>
findByStatusAndTenant(javax.persistence.EntityManager entityManager,
                      java.lang.String p0, java.lang.Long p1, int offset, int limit)
```

生成方法的形参固定叫 `p0`、`p1`……和绑定到 JPQL 的命名参数同名，不取字段名。字段叫 `offset`、`limit` 或 `entityManager` 也不会撞名。调用方写普通 Java 或 Scala 即可。方法体只把参数交给 `GeneratedQueryExecutor`，不把参数值写进源码，也没有共享或全局的 EntityManager 状态。

## 字段约束和继承遮蔽

一个字段要同时满足两层条件。

第一层与现有 JPA 动态 finder 的字段过滤一致：实例字段、非 `static`、非 `final`、名称不含 `$`、不是 Java `transient`，也没有 `javax.persistence.Transient`。动态 finder 仍然只给**该类自己声明**的字段生成 `findByX`；伴生查询不枚举组合，只接受声明里点名的字段。

第二层才是本语法的类型限制。通过过滤之后，字段还必须是下面的标量，或者是枚举：

- 基本类型及其包装类型：`boolean` `byte` `short` `int` `long` `char` `float` `double`
- `String`
- `java.util.Date`、`java.sql.Date`、`java.sql.Time`、`java.sql.Timestamp`
- `BigDecimal`、`BigInteger`、`UUID`

不支持 `java.time`、数组、集合、嵌入对象、关联（`ManyToOne`、`OneToOne`、`OneToMany`、`ManyToMany`、`Embedded`、`EmbeddedId`、`ElementCollection`）、DTO，以及任意 JPQL / SQL。有 JPQL 保留字的字段名（例如 `order`、`select`、`from`）也不能用。`offset`、`limit`、`entityManager` 这类字段名是允许的，因为生成方法的形参是 `p0`、`p1`……而不是字段名。

继承从声明类型往上走，在 `net.csdn.jpa.model.Model`、`net.csdn.jpa.model.JPABase` 和 `java.lang.Object` 之前停住，因此框架基类上的字段不会变成查询字段。子类同名字段遮蔽父类字段，**即使子类字段不合格也不会退回去用父类字段**。共享 fixture 里父类的 `status` 是 `Integer`，子类的 `status` 是 `String`，生成参数类型是 `String`。父类的 `warehouse` 没有被遮蔽，所以可以在子类声明里使用。

## 参数、null 和实体名

用户值只通过 `Query.setParameter(名字, 值)` 绑定。名字是 `p0`、`p1` 这种生成出来的标识，不是用户字符串。引用类型的 `null` 渲染成 `e.field IS NULL`，并且不为这个位置调用 `setParameter`。基本类型不接受 null。错误的运行时类型会在创建查询之前拒绝。

这和旧 finder 不完全相同：旧实现把单个值，包括 null，绑到位置参数。新语法把 null 定义成 `IS NULL`，避免 `= null` 选不中 SQL NULL。字段能不能查，两边用的是同一组结构性约束。

实体名在执行时取：

```text
entityManager.getMetamodel().entity(modelClass).getName()
```

不使用 `simpleName`，因此同名类不会静默撞车。取到的名字必须是点分 Java 标识，才会进入 `SELECT e FROM 实体名 e`。JPQL 形态只有相等 AND、可选 ORDER BY，以及 `setFirstResult` / `setMaxResults`。

`offset` 必须 `>= 0`。`limit` 必须是 `1` 到 `QueryLimits.MAX_PAGE_SIZE`（500）。

## 上限

| 限制 | 值 |
| --- | --- |
| 每个模型的查询方法 | 8 |
| 每个方法的相等字段 | 4 |
| 每个方法的排序字段 | 3 |
| 方法名长度 | 160 |
| 字段名长度 | 80 |
| 生成伴生源码字符数 | 4500 |

超限、未知字段、重复方法、重复字段、不合格字段，都在编译期报错。诊断里带有 model、method、field。运行期会用反射再走一遍 `QuerySchemas`；绕过处理器写上的坏注解不会直接变成 JPQL。

## 处理器怎么挂上

ORM 模块编译时，处理器源码和业务源码在同一次编译里，处理器类还不存在。因此**不提供** `META-INF/services/javax.annotation.processing.Processor`。若放上这个文件，模块编译会按服务发现去加载一个尚未编译出来的处理器。

消费方用 `-processor` 显式挂处理器。伴生源码在注解处理后续轮次进入同一次 `javac` 编译，所以**调用方可以和模型放在同一次编译里**，同包或跨包都行；也可以分两阶段，先编模型再编调用方：

```text
# 单阶段：模型和调用方一起编（调用方可以在模型同包或其他包）
javac --release 8 -encoding UTF-8 \
  -classpath serviceframework-orm.jar:javax.persistence-api-2.2.jar \
  -processor net.csdn.jpa.query.ServiceFrameworkQueryProcessor \
  -processorpath serviceframework-orm.jar \
  -d out/classes -s out/generated \
  src/main/java/com/example/OrderEntityBase.java \
  src/main/java/com/example/OrderEntity.java \
  src/main/java/com/example/client/OrderQueryCaller.java

# 两阶段：第二次编译调用方，不再跑处理器
javac --release 8 -encoding UTF-8 \
  -classpath out/classes:serviceframework-orm.jar:javax.persistence-api-2.2.jar \
  -proc:none \
  -d out/caller-classes \
  src/main/java/com/example/client/OrderQueryCaller.java
```

这是最小夹具的示意。声明里的 `OrderEntity` 继承 `OrderEntityBase`，第一次 javac 必须带上基类源文件。下游业务 classpath 还要包含模型超类以及这些类型自己的依赖。上面两个 jar 只够这个最小夹具，不够一份真实业务模型。命令按 JDK 17 的 `javac --release 8` 来写。JDK 8 的 javac 没有 `--release`，用 `-source 8 -target 8`。

模型简单名是 `Order` 时，伴生类才叫 `OrderQueries`。上面的 `OrderEntity` 生成 `OrderEntityQueries`。跨包调用方 import 那个伴生类。

`-processorpath` 里要有处理器和注解类。模型编译的 classpath 里也要有这些注解，以及 `GeneratedQueryExecutor` 和 JPA API，因为生成的伴生类会调用执行器。父 POM 把 Java 固定在 `--release 8`，但没有替下游应用注册这个处理器，也没有为它单开一个模块。消费方的接法就是上面的显式 `-processor`。

IDE 里要补全伴生类，做这三件事：

1. 注解处理不要指望服务发现。处理器名填 `net.csdn.jpa.query.ServiceFrameworkQueryProcessor`，处理器路径指向已经编译好的 `serviceframework-orm` jar。
2. 把 javac 的 `-s` 目录标成 generated sources。`OrderEntityQueries` 和包锚点写在这里，IDE 才能索引。
3. 模型工程的 classpath 上保留注解、执行器和 `javax.persistence-api` 2.2。

调用示例。Java 可以传自己的 `EntityManager`，也可以让运行时取当前 ORM 上下文：

```java
OrderEntityQueries.findByStatusAndTenant(entityManager, status, tenantId, 0, 50);
OrderEntityQueries.findByStatusAndTenant(status, tenantId, 0, 50);
```

Scala 同样直接调用生成方法：

```scala
OrderEntityQueries.findByStatusAndTenant(em, status, tenantId, offset, limit)
```

`QueryApiRegressionTest` 用这两类调用方做编译和执行。增强完成之前不要用类字面量加载目标模型。

参与生成的每个包会得到一份 `ServiceFrameworkPackageAnchor`。JDK 8 和 JDK 17 定义同包类时都要有它：Java 8 用它核对加载器和保护域，JDK 17 还把它当作 neighbor。它不是查询入口。包里已经有这个类型时不覆盖：已有类保留，并记一条 NOTE；已有类型不是类（例如接口）则编译失败。`RuntimeQueryMetadata` 拒绝读取这个锚点类，也不给 ClassDefiner 做锚点查找。

`RuntimeQueryMetadata.read(modelClass)` 只应在应用增强完成之后调用。它读的是运行时可见注解加上反射字段，不负责定义类。

不带 EntityManager 的重载走生产入口：

```text
JPA.getJPAConfig().getJPAContext().em()
```

然后 `createQuery(jpql, modelClass)`。带 EntityManager 的重载完全使用调用方传入的实例，不碰 JPA 上下文，也不读任何共享状态；集成测试、多数据源或自定义上下文场景直接传自己打开的 `EntityManager`，例如 `OrderEntityQueries.findByStatusAndTenant(entityManager, status, tenantId, 0, 50)`。两个并发调用各自使用各自的 EntityManager，互不干扰。

## 接到真实 ORM

模型先按 `JPA.configure` 扫描并增强，再由应用加载器定义。这一步完成之前不要用类字面量碰到目标模型，否则 JVM 会先装入未增强的字节码，后面的 `define` 会冲突。

实体名以 `EntityManager.getMetamodel().entity(模型类).getName()` 为准。框架现在会给没有显式 `@Entity(name)` 的模型写成二进制名，所以同简单名的两个类不会共用一个 JPQL 名字。调用生成方法时用普通 Java 或 Scala 即可，例如在增强完成之后：

```java
OrderQueries.findByStatusAndRegion("open", "east", 1, 1);
```

不带 `EntityManager` 的重载使用当前 ORM 上下文里的事务。分页参数就是方法最后的 `offset` 和 `limit`。显式 `EntityManager` 重载不读取 `JPA` 的静态上下文。物理表名仍然来自 `@Table` 或简单名下划线，不会改成二进制名；伴生查询只负责按实体名查询，不建表。

实库回归复用这套调用方式，夹具在 `serviceframework-orm/src/test/resources/net/csdn/jpa/ormfixture/`，由测试进程自己 `javac -processor net.csdn.jpa.query.ServiceFrameworkQueryProcessor` 编译，不放进会被提前加载的 `src/test/java`。

## 共享 fixture

后续实库测试可以复用这两份声明，它们本身不会连接数据库：

- `serviceframework-orm/src/test/resources/net/csdn/jpa/query/fixtures/OrderEntity.java`
- `serviceframework-orm/src/test/resources/net/csdn/jpa/query/fixtures/OrderEntityBase.java`

包名是 `net.csdn.jpa.query.fixture`。三条声明是 `findByStatusAndTenant`、`findByRegion`、`findByWarehouse`。

## 不在这套语法里的东西

- 不修改模型字节码，不生成 `Like`、`Between`、`Or`、连接、子查询、更新或删除
- 不枚举全部字段组合
- 不映射 DTO
- 不把 EntityManager 测试替身当成 MySQL 或 Hibernate 已通过

## 回归测试

回归测试是 `serviceframework-orm/src/test/java/net/csdn/jpa/query/QueryApiRegressionTest.java`，JUnit4 风格，Surefire 按 `*Test` 自动发现。它不依赖本机目录布局：query 主类位置、`javax.persistence` API jar、Scala 三件套都从各字节的 protection domain / CodeSource 解析；fixture 从测试 classpath 读；所有编译产物落在 `TemporaryFolder`，跑完即删。javac 在 JDK 9+ 用 `--release 8`，在 JDK 8 用 `-source 8 -target 8`。

测试覆盖：同一次 `javac` 内同包 + 跨包调用方编译生成 API；两阶段编译；显式 EntityManager 重载的真实编译后 Java/Scala 调用；两个并发 EntityManager 各自返回各自结果与绑定参数；`offset`/`limit`/`entityManager` 命名字段照常编译；null 走 `IS NULL`、类型不符在 `createQuery` 前拒绝、`offset`/`limit` 边界、全部声明错误编译失败、锚点不覆盖与冲突失败、绕过处理器的运行期复核。

2026-09-25 的默认矩阵里，这个类在 JDK 8 和 JDK 17 上都是 11 个测试、0 跳过。本机 JDK 路径在 [jdk-compatibility.md](jdk-compatibility.md)。`javap` 先找 `java.home/bin/javap`，找不到再找 JDK 目录的 `bin/javap`。JDK 8 的 `java.home` 指向 `jre`，`javap` 在上一级的 `bin`。Scala 默认版本是仓库声明的 2.13.16。不带 `EntityManager` 的生成重载进入 `JPA.getJPAConfig().getJPAContext().em()`。实库行为在 [orm-compatibility.md](orm-compatibility.md)。
