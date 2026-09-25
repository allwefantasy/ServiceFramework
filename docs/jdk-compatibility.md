# JDK 8 / JDK 17 构建和依赖

同一套 Java 8 字节码在 JDK 17 上编出来，再分别放到 JDK 8 和 JDK 17 上跑。类定义已经收口到 `ClassDefiner`，classpath 部署不需要 `--add-opens`。行为、迁移和 T01–T15 在 [serviceframework-bytecode-migration.md](serviceframework-bytecode-migration.md)。

Hibernate 5.3.7.Final、Jetty `9.2.16.v20160414` 和 Connector/J 5.1.6 没有为这次验收升级。Mongo Java 驱动是 **3.12.14**，说明在 [mongo-compatibility.md](mongo-compatibility.md)。独立 `active_orm` 仓库不在这次验收里。

## 本机 JDK

不要用 `/usr/libexec/java_home -v 1.8`。这台机器上它会退出码 0，但打印出 JDK 17 的目录。下面的路径是终验实际使用的本机位置，不是可移植的安装说明。

| 角色 | 路径 | 已核对版本 |
| --- | --- | --- |
| JDK 8 | `/usr/local/opt/openjdk@8` | `1.8.0_504` |
| JDK 17 | `/usr/local/Cellar/openjdk@17/17.0.20.1/libexec/openjdk.jdk/Contents/Home` | `17.0.20.1` |

验收脚本要求显式传入 `JDK8_HOME` 和 `JDK17_HOME`，并检查 `java` / `javac -version` 真的是 8 和 17。

## 构建

默认 Scala 是 `2.13.16`。Scala 源码仍在 `src/main/java`，和 Java 混在一起。父 POM 现在是：

| 插件 | 版本 | 作用 |
| --- | --- | --- |
| `maven-compiler-plugin` | 3.13.0 | Java `<release>8</release>`，等价于 `--release 8` |
| `scala-maven-plugin` | 4.9.2 | `net.alchim31.maven`。scalac 参数 `-release:8`，zinc 的 javac 也带 `release=8` |
| `maven-surefire-plugin` | 3.2.5 | 跑已编译的 JUnit 4；`useModulePath` 关闭 |
| `scalatest-maven-plugin` | 2.2.0 | 跑已编译的 ScalaTest |
| `animal-sniffer-maven-plugin` | 1.24 | 签名 `java18`。这个名字表示 **Java 8**，不是 Java 18 |

Scala 和 Java 互相引用，所以 scala 插件使用 `compileOrder=Mixed`、`sendJavaToScalac=true`，绑在 `process-resources`，早于 `maven-compiler-plugin`。之后 compiler 插件再按 `--release 8` 重写 Java class。Scala class 保留 scalac `-release:8` 的结果。

`scala-2.11` 和 `scala-2.12` profile 还在，标成 **未验证**。不要用默认 2.13 的结果推断它们。2.11.8 的 scalac 没有 `-release`，该 profile 只把参数换成 `-target:jvm-1.8`，这不是 Java 8 API 基线。JPMS module-path 也没有验证。

验收构建使用 `install -DskipTests -DperformRelease=false`，并关掉 javadoc。不调用 `deploy`。`performRelease=false` 是显式传入的：`release-sign-artifacts` 只在 `performRelease=true` 时激活，传 false 是为了让这个签名 profile 不生效，不是“没有设置这个属性”。测试是两次独立的 `surefire:test scalatest:test`（用插件全坐标调用，版本与 POM 一致），中间不 `clean`、也不再 compile。脚本会清掉 `MAVEN_OPTS`、`JAVA_TOOL_OPTIONS`、`_JAVA_OPTIONS`、`JDK_JAVA_OPTIONS`，避免环境里的 `--add-opens` 混进这次运行。

本机 `~/.m2/settings.xml` 把 `central` 指到 `https://maven.aliyun.com/repository/central`。验收脚本不改这个文件。终验使用 `-s '/tmp/sf maven central/settings.xml'`，里面的 mirror 指向 `https://repo.maven.apache.org/maven2`。

## 依赖

父 POM 的 `dependencyManagement` 只托管 Javassist、Guice、`guice-assistedinject`、`javax.persistence-api` 和 `mysql-connector-java`。这几项在模块里用属性。Mongo、JAXB 和 SLF4J 不在这份托管里，版本写在模块 POM 上：`serviceframework-mongo` 声明 `mongo-java-driver` 3.12.14 和 `slf4j-api` 1.7.32；`serviceframework-orm` 声明 `jakarta.xml.bind-api` 2.3.2、`jaxb-runtime` 2.3.2、`javax.activation-api` 1.2.0 和 `slf4j-api` 1.7.32。common、dispatcher、jetty-9-server 解析到的 `slf4j-api` 1.5.8 是传递依赖，不是父 POM 托管的版本。

### Javassist

默认 `javassist.version=3.33.0-GA`。对照运行可以加 `-Djavassist.version=3.30.2-GA`，不必改 POM。Hibernate 5.3.7.Final 自带的 3.23.1-GA 由 `dependencyManagement` 抬到这个属性。

选 3.33.0-GA 是因为主 JAR 的 class 都是 major 52，而且本仓库不用已经移出主 JAR 的 `javassist.tools`。3.30.2-GA 是更早的 common-only 对照，不是这次全框架矩阵的运行版本。类定义走 `ClassDefiner`：Java 8 用 `CtClass.toClass(loader, domain)`，JDK 17 用同包锚点的 neighbor 重载，并且拒绝命名模块。

终验解析到的 3.33.0-GA SHA-256 是 `1620478adc5f4d2eccd356e59513c270f1508bed53ce75deffb3107b7b43db2c`。

### Guice

JDK 17 上、不加 `--add-opens`，Guice 3.0 的 cglib 会在 `ClassLoader.defineClass` 上抛 `InaccessibleObjectException`。因此 Guice 是 **5.1.0**，`guice-assistedinject` 对齐 5.1.0。`guice-multibindings` 不再单独依赖，这些类已经在 core 里。`com.mycila.com.google.inject:guice:3.0-20100927` 已删除。

core 和 assistedinject 是两个归档。审计允许这样，只要类名不重复。终验里 `com/google/inject/` 的 `duplicate_key_classes` 是 0。

### JPA 和日志 API

删掉 `org.hibernate.javax.persistence:hibernate-jpa-2.1-api:1.0.2.Final`。Hibernate 5.3.7.Final 继续带 `javax.persistence:javax.persistence-api:2.2`。业务接口仍是 `javax.persistence`，没有改成 Jakarta Persistence。

SLF4J 只解析 API，不强制绑定。common、dispatcher 和 jetty-9-server 是 `slf4j-api` **1.5.8**。ORM、Mongo 和 Web 是 **1.7.32**。不能写成全仓都是 1.7.32。应用可以自己加一个绑定；审计失败的条件是同一个 class 出现两次，不是“classpath 上不允许有绑定”。

JDK 17 上 Hibernate 需要 `javax.xml.bind`。ORM 模块声明 `jakarta.xml.bind-api` 2.3.2 和 `org.glassfish.jaxb:jaxb-runtime` 2.3.2。前者是 API jar，`jaxb-runtime` 是实现，不是 API jar。两者的类仍在 `javax.xml.bind`，不是 Jakarta XML Binding 3 或 4。activation 是 API jar `javax.activation-api` 1.2.0，包名仍是 `javax.activation`。不能把这三个 jar 都叫成 API。禁用 ORM 不会把它们从 Web 的父 classpath 上拿掉。

### MySQL 和 Mongo

`mysql:mysql-connector-java:5.1.6` 仍是解析版本。终验用它连的是本机回环上的 MySQL **8.0.46**（端口 63714，库 `sf_compat`），不是 3306。这次没有改成 8.0.33。

`mongo-java-driver` 是 **3.12.14**。终验的服务器是 MongoDB **4.4.29**，端口 63811。2.11.4 不是当前解析结果。

### 没有升级的运行时

Hibernate 5.3.7.Final 和 Jetty `9.2.16.v20160414`（`javax.servlet`）。没有改成 Jakarta 命名空间，也没有上 Jetty 12。

## 整仓验收命令

实库矩阵要同时打开三个开关。只跑 `mvn test`、不设置它们时，对应的 MySQL、Mongo 和 Web 数据库用例会跳过。那种跳过不是验收通过。终验三个都设为 `true`，两个 JDK 都是 1212 个测试、0 跳过。

```bash
export JDK8_HOME=/usr/local/opt/openjdk@8
export JDK17_HOME=/usr/local/Cellar/openjdk@17/17.0.20.1/libexec/openjdk.jdk/Contents/Home
export SF_ORM_MYSQL=true
export SF_COMPAT_MONGO=true
export SF_COMPAT_WEB_DB=true

dev/compat-services.sh run -- dev/verify-jdk-compatibility.sh \
  --jdk8-home "$JDK8_HOME" \
  --jdk17-home "$JDK17_HOME" \
  --maven-settings "/tmp/sf maven central/settings.xml"
```

数据库用户和口令不要导出，也不要写进命令行。测试从 `SF_COMPAT_ENV_FILE` 指向的权限 600 文件里读。不要打印那个文件。

2026-09-25 终验的逐步命令、退出码和日志在 `target/bytecode-final-verification-20260925/`。驱动是 `commands/run-verification.sh`：先 `verify-jdk-compatibility.sh`，退出码 0 之后再 `dev/measure-framework-phases.sh --cold 3 --warmup 1 --repeated 5 --hot 20`。外层是构建锁加 `dev/compat-services.sh run`。测完服务已停止。

指定模块时，`dev/verify-jdk-compatibility.sh` 使用 `mvn -pl <模块> -am`。那是缩小范围的跑法，不能代替上面的全反应器计数。

`flatten-maven-plugin` 只写 `.flattened-pom.xml`，不回写源码 `pom.xml`。

## 依赖审计

`dev/audit-runtime-dependencies.py` 用和测试同一组 Maven 参数调用 `dependency:build-classpath`（test scope），然后原样扫描解析出的条目。模块自己的 `target/classes` 和 `target/test-classes` 也纳入扫描。

实际规则是：

- JDK 8 能看见的 class（不在 `META-INF/versions/`、也不是 `module-info.class`）major 必须 ≤ 52。multi-release 和 `module-info.class` 单独列出，不算失败。
- 这些前缀下的**每一个 class** 只能出现在一个归档里：`javassist/`、`com/google/inject/`、`javax/persistence/`、`javax/xml/bind/`、`javax/activation/`、`org/slf4j/`、`net/csdn/jpa/`。同一个前缀可以来自多个归档，只要类名不重复。Guice core 加 assistedinject、ORM 主类加测试类，都是这种合法情况。
- 不要求所有依赖零重复。`META-INF/services` 不是 class，不参与这项检查。
- 这项检查不禁止应用自己选一个 SLF4J 绑定。绑定若带来重复 class，才会失败。框架解析到的是 API jar。脚本说明里把“SLF4J 绑定和 API 放在一起就不能共享 classpath”写成了失败例子；实现只比较上面这些前缀下的重复 class。类名不重复的绑定可以通过。
- classpath 解析失败、文件为空、一个 class 都没扫到，都是失败。

终验报告里的规则行是 `key classes must be unique`。`key_archives com/google/inject/` 和 ORM 的 `net/csdn/jpa/` 都大于 1，同时 `duplicate_key_classes` 为 0。

不要把独立 `active_orm` 的 jar 和 `serviceframework-orm` 放进同一个 classpath。
