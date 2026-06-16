# ServiceFramework 使用手册

本文面向正在维护或基于 ServiceFramework 开发后端服务的工程师。它覆盖框架结构、启动方式、配置、Controller、ActiveORM、MongoMongo、HTTP/RPC 客户端、动态字节码能力、测试与 JDK 兼容性。

## 1. 框架定位

ServiceFramework 是一个偏 Rails 风格的 Java/Scala 后端框架，核心目标是：

- 用约定减少样板代码。
- 通过 Javassist 在启动期增强 Model、Document、Controller。
- 让 Java 获得接近 Ruby on Rails ActiveRecord 的开发体验。
- 同时支持 HTTP、REST client、Thrift、Dubbo、MySQL、MongoDB、Velocity 模板、Guice IOC。

当前仓库是多模块 Maven 工程：

| 模块 | 作用 |
| --- | --- |
| `serviceframework-common` | 通用工具、配置、扫描、日志、路径、动态字节码公共工具 |
| `serviceframework-orm` | MySQL/JPA ActiveORM，Model 动态增强 |
| `serviceframework-mongo` | MongoMongo ODM，Document/Criteria 动态增强 |
| `serviceframework-web` | Controller、HTTP server、过滤器、测试基类、REST client |
| `serviceframework-dispatcher` | 策略分发、AB/linear 策略、组合器 |
| `serviceframework-jetty-9-server` | Jetty 9 server 封装 |

## 2. JDK 与 Scala 兼容性

### JDK

项目 Maven 编译配置保持：

```xml
<source>1.8</source>
<target>1.8</target>
```

因此生成字节码目标是 Java 8 classfile。代码中新增的动态字节码工具不使用 JDK 9+ API，保留 JDK8/JDK17 的源代码兼容性。

JDK17 运行时建议：

- 优先使用框架当前的 `CtClass.toClass(classLoader, protectionDomain)` 加载路径。
- 避免直接调用 Javassist 的无参 `toClass()`，它在 JDK9+ 模块系统下更容易遇到反射访问限制。
- 如果应用或第三方库在 JDK17 下出现 `InaccessibleObjectException`，先确认是否有旧依赖反射访问 JDK 内部包，再按运行环境最小化添加 `--add-opens`。
- 本次已在 JDK8 与 JDK17 本地执行全量测试；新增代码按 Java 8 API 编译，并能在 JDK17 运行测试。

构建层面已清理旧 Maven model/dependency warning：

- 模块 artifactId/name 使用固定的 `_2.13` 坐标，不再在 artifactId 中直接使用表达式。
- `maven-javadoc-plugin` 固定版本，避免 Maven 自动解析历史默认版本。
- Scala 编译插件显式绑定 `${scala.version}`，并关闭多 Scala 版本一致性误报。
- Druid 从 `0.2.26` 升级到 Java 8 兼容的 `1.2.20`，避免 JDK17 下解析旧 POM 时引用已不存在的 `tools.jar`、`jconsole.jar`。

### Scala

默认属性：

```xml
<scala.version>2.13.16</scala.version>
<scala.binary.version>2.13</scala.binary.version>
```

仓库仍保留 `scala-2.11`、`scala-2.12` profile。旧文档里常见命令是：

```bash
mvn install -Pscala-2.11
```

由于 Maven 不推荐在 `artifactId` 中使用表达式，模块坐标现在默认固定为 `_2.13`。如果需要切换并发布其他 Scala 二进制版本，先运行版本切换脚本同步 artifactId/name 与 `scala.binary.version`：

```bash
dev/change-scala-version.sh 2.12
mvn test -Pscala-2.12
```

当前默认构建可直接执行：

```bash
mvn test
```

## 3. 基本工程结构

应用通常包含：

```text
src/main/java
config/application.yml
config/logging.yml
sql/
test/
```

示例配置位于：

- `config/application.example.yml`
- `config/application.yml.development.example`
- `config/application.yml.production.example`
- `config/logging.yml`

## 4. 启动应用

IDE 中可运行：

```java
net.csdn.bootstrap.Application
```

也可以写自己的启动类继承或调用它。应用启动时会完成：

1. 读取 `config/application.yml` 与日志配置。
2. 扫描 controller/model/document/service/util 等包。
3. 通过 Javassist 增强类。
4. 加载增强后的类。
5. 创建 Guice injector。
6. 启动 HTTP/Thrift/Dubbo 等模块。

## 5. Controller 开发

Controller 继承 `ApplicationController`，用 `@At` 声明路由：

```java
public class HelloController extends ApplicationController {
    @At(path = "/hello", types = {RestRequest.Method.GET})
    public void hello() {
        render(200, "hello " + param("name"));
    }
}
```

常用能力：

- `param("name")` 读取请求参数。
- `params()` 读取所有参数。
- `render(status, content)` 输出响应。
- `render(status, map, ViewType.html)` 渲染 Velocity 页面。
- `beforeFilter`、`afterFilter`、`aroundFilter` 组织请求生命周期。
- `@NoAction` 排除非 action 方法。
- `@BasicInfo`、`@Action`、`@Parameter` 等生成 OpenAPI 描述。

过滤器示例：

```java
static {
    beforeFilter("validateLogin", WowCollections.map("only", WowCollections.list("create", "update")));
}

public void validateLogin() {
    if (currentUser() == null) {
        render(401, "unauthorized");
    }
}
```

Controller 增强器会把 `ApplicationController` 上的静态辅助方法复制到子类，保持类似 Rails controller helper 的调用体验。

## 6. ActiveORM 使用

Model 继承 `net.csdn.jpa.model.Model`：

```java
public class Tag extends Model {
    private String name;
    private Integer status;
}
```

启动时 ORM 增强器会：

- 自动添加 `@Entity`、`@Table`、`@Column`、`@Id` 等 JPA 注解。
- 根据数据库列补齐缺失字段。
- 为字段生成 getter/setter。
- 注入静态查询方法。
- 注入 Rails 风格动态 finder。

### 6.1 原有 ActiveRecord 风格 API

```java
long total = Tag.count();
Tag tag = (Tag) Tag.findById(1);
List<Tag> all = Tag.findAll();

List<Tag> javaTags = Tag.where("name", WowCollections.map("name", "java")).fetch();
List<Tag> page = Tag.where("status = ?1", WowCollections.list(1)).order("id desc").limit(20).fetch();

Tag created = (Tag) Tag.create(WowCollections.map("name", "java", "status", 1));
```

链式查询常用方法：

```java
Tag.where("status = ?1", params)
Tag.where(map)
Tag.in("id", list)
Tag.select("id,name")
Tag.joins("author")
Tag.order("id desc")
Tag.limit(20)
Tag.offset(40)
```

### 6.2 新增动态 finder

对每个普通实例字段，框架会在字节码阶段生成：

| 字段 | 生成方法 |
| --- | --- |
| `name` | `findByName(Object value)` |
| `name` | `findAllByName(Object value)` |
| `name` | `whereByName(Object value)` |
| `name` | `countByName(Object value)` |
| `name` | `deleteByName(Object value)` |

示例：

```java
Tag one = (Tag) Tag.findByName("java");
List<Tag> tags = Tag.findAllByStatus(1);
long count = Tag.countByOwnerId(1001);
int deleted = Tag.deleteByStatus(0);
Model.JPAQuery query = Tag.whereByName("scala");
```

这些方法不是反射调用，而是在启动期由 Javassist 注入到 classfile。调用方得到的是普通 Java 静态方法。

底层会复用 `JPQL.findByToJPQL` 的约定解析，例如：

```text
byName -> name = ?1
byOwnerId -> ownerId = ?1
```

### 6.3 实例方法

```java
Tag tag = new Tag();
tag.attr("name", "java");
String name = tag.attr("name", String.class);

tag.save();
tag.update();
tag.delete();
tag.valid();
```

校验可通过 `@Validate` 与 validator 扩展完成。

## 7. MongoMongo 使用

Document 继承 `net.csdn.mongo.Document`：

```java
public class Person extends Document {
    private String name;
    private Integer age;

    static {
        storeIn("persons");
    }
}
```

启动时 Mongo 增强器会：

- 复制 `Document` 静态元数据字段。
- 复制可用的静态类方法。
- 注入 `Criteria` 查询入口。
- 为字段生成 getter/setter，并让 setter 同步 `attributes`。
- 增强 association 与 embedded association。
- 注入 Rails 风格动态 finder。

### 7.1 Criteria API

```java
Person.create(WowCollections.map("name", "william", "age", 18));

Person.where(WowCollections.map("name", "william")).fetch();
Person.where(WowCollections.map("age", 18)).singleFetch();
Person.order(WowCollections.map("createdAt", -1)).limit(20).fetch();
Person.in(WowCollections.map("_id", ids)).fetch();
Person.count();
```

### 7.2 新增动态 finder

对普通实例字段生成：

| 字段 | 生成方法 |
| --- | --- |
| `name` | `findByName(Object value)` |
| `name` | `findAllByName(Object value)` |
| `name` | `whereByName(Object value)` |
| `name` | `countByName(Object value)` |

示例：

```java
Person one = (Person) Person.findByName("william");
List<Person> people = Person.findAllByAge(18);
Criteria criteria = Person.whereByName("william");
int count = Person.countByAge(18);
```

Mongo 动态 finder 内部使用 `Criteria(kclass).where(params)`，并通过 `Document.translateKeyForParams` 保持 alias 兼容。

## 8. HTTP/REST Client

框架支持把 HTTP API 声明成 Java/Scala interface，再像 RPC 一样调用。

```java
public interface TagClient {
    @At(path = "/say/hello", types = {RestRequest.Method.GET})
    HttpTransportService.SResponse sayHello(@Param("kitty") String kitty);
}
```

调用方可通过 `AggregateRestClient` 或相关 proxy 构建客户端。已有测试覆盖：

- GET query 参数转换。
- body 请求编码。
- form 参数提交。
- `@At` 方法声明校验。
- 多 host 代理构建与策略分发。

## 9. Dispatcher 使用

`serviceframework-dispatcher` 提供策略分发模型：

- `Processor` 执行业务处理。
- `Strategy` 决定请求命中哪些 processor/ref。
- `Compositor` 聚合输出。
- 默认支持 default、linear、AB 策略。

适合广告、推荐、实验、召回、排序这类需要组合策略的后端服务。

## 10. 动态字节码机制

新增公共工具：

```text
serviceframework-common/src/main/java/net/csdn/common/enhancer/DynamicBytecode.java
```

它集中处理以下能力：

- Rails 风格方法命名：`findByName`、`countByStatus` 等。
- JavaBean getter/setter 注入。
- 静态字段/方法复制。
- 方法重复检测，避免重复增强。
- Java 字符串字面量转义。
- ORM 动态 finder 源码生成。
- Mongo 动态 finder 源码生成。

### 10.1 为什么集中到 common

旧代码中 ORM、Mongo、Controller 都有类似的 Javassist 片段：

- 复制 `parent$_` 静态字段。
- 复制静态方法。
- 为字段补 getter/setter。
- 手写字符串拼接生成方法。

这些逻辑分散后很难测试，也容易在 JDK17 下出现行为差异。现在公共工具层让增强器只表达业务意图：

```java
DynamicBytecode.copyStaticFields(parent, child, DynamicBytecode.PARENT_STATIC_FIELD_FILTER);
DynamicBytecode.copyStaticMethods(parent, child, methodFilter);
DynamicBytecode.addBeanAccessors(ctClass, fieldFilter);
DynamicBytecode.addJpaDynamicFinders(ctClass, fieldFilter);
DynamicBytecode.addMongoDynamicFinders(ctClass, fieldFilter);
```

### 10.2 增强生命周期

典型启动流程：

1. 扫描 classpath 中的 Model/Document/Controller。
2. 用 `ClassPool.makeClassIfNew` 创建 `CtClass`。
3. 判断父类和注解。
4. 复制必要静态元数据。
5. 注入 getter/setter、查询方法、过滤器方法。
6. 用指定 `ClassLoader + ProtectionDomain` 加载增强后的类。

## 11. 测试

当前新增了三组参数化测试：

| 模块 | 测试类 | 用例数 |
| --- | --- | --- |
| common | `DynamicBytecodeConventionTest` | 500 |
| orm | `DynamicJpaFinderBytecodeTest` | 260 |
| mongo | `DynamicMongoFinderBytecodeTest` | 260 |

加上原有 ScalaTest：

- `RestClientProxySuite`：5 个。
- `APIDescACSuite`：1 个。
- `StrategyDispatcherSuite`：6 个。

总计：

```text
JUnit: 1020
ScalaTest: 12
Total: 1032
```

运行：

```bash
mvn test -DskipTests=false
```

最近一次本机验证：

```text
BUILD SUCCESS
Tests run: 1032
JDK8: 1.8.0_492
JDK17: 17.0.19
```

## 12. 维护建议

为了继续把动态字节码能力推到更深，建议按以下顺序演进：

1. 增加 JDK17 CI job，和 JDK8 同时跑 `mvn test`。
2. 升级或隔离 Javassist 版本验证，重点关注 JDK17 模块访问。
3. 为真实数据库集成测试单独提供 profile，例如 `-Pintegration-mysql`、`-Pintegration-mongo`。
4. 将 Model/Document 扫描结果输出成 debug 日志，方便定位增强了哪些类。
5. 给动态 finder 增加多字段组合，例如 `findByNameAndStatus`。
6. 给动态 finder 增加排序后缀，例如 `findAllByStatusOrderByCreatedAtDesc`。

这些建议都可以在不破坏当前 API 的前提下逐步推进。
