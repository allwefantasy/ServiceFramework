# MongoDB 5.x 驱动 / 8.3 服务升级记录

日期：2026-09-26。范围是 `serviceframework-mongo` 和独立 `mongomongo` jar；不要把独立 jar 放进框架模块 classpath。官方依据是 MongoDB release notes <https://www.mongodb.com/docs/manual/release-notes/>（当前稳定 8.3.x，本机 runner 固定 8.3.11）和 Maven Central `mongodb-driver-legacy` 元数据 <https://repo.maven.apache.org/maven2/org/mongodb/mongodb-driver-legacy/maven-metadata.xml>（5.13.0）。

## 迁移点

| 旧项 | 新项 |
| --- | --- |
| `mongo-java-driver` 3.12.x / 2.11.x | `org.mongodb:mongodb-driver-legacy:5.13.0` |
| 单 driver jar | `mongodb-driver-legacy` + `mongodb-driver-core` + `mongodb-driver-sync` + `bson` |
| `com.mongodb.Mongo` | `com.mongodb.MongoClient`（`mongo()`、`client()`、`closeMongoClient(MongoClient)`） |
| `DB.getMongo()` | `DB.getMongoClient()` |
| `DBCollection.ensureIndex` | `DBCollection.createIndex(DBObject, DBObject)` |
| 旧 `javassist:javassist` | standalone 排除后改用 `org.javassist:javassist:3.33.0-GA` |
| `mongodb-driver-core` 传递的 `bson-record-codec` | 两个仓库都 exclude：它是 Java 17 Record 编解码器（class major 61），legacy Document API 不分发这个扩展 |

Java 最低版本：框架 `serviceframework-mongo` 仍是 **8**（驱动和项目输出都是 class major 52，同一 artifact 用 JDK 8 / JDK 17 跑）；独立 `mongomongo` 本轮把最低版本从 6 提到 **8**。JDK 9+ 上增强类通过同包 `ServiceFrameworkPackageAnchor`（或文档包内的 marker）定义，不需要 `--add-opens`。`com.mongodb.Mongo` 在 5.x 已删除：对着旧 driver API 编译的代码要改源码并**重新编译**，旧 jar 与新驱动不二进制兼容。

`bson-record-codec` 的排除是有意放弃 Java 17 Record 编解码支持：`mongodb-driver-core` 用 `com.mongodb.Jep395RecordCodecProvider` 反射探测 Record 支持，捕获 `ClassNotFoundException` 和 `UnsupportedClassVersionError` 后回退；legacy 的 `DB`/`DBCollection`/Document 路径本身不需要 Java 17 `java.lang.Record` 编解码，这条已用真实 MongoDB 8.3.11 覆盖验证。排除后 JDK 8 classpath 不再出现 major 61 字节码；需要 Record codec 的调用方要自己把该构件加回来并接受 Java 17 要求。

## 配置

`{mode}.datasources.mongodb.uri` 非空时优先：host、credential、`authSource` 和 URI options 从连接串读取；离散 host/port/username/password/authenticationDatabase 不再解析。URI 数据库路径覆盖 `{mode}.datasources.mongodb.database`；URI 无路径时才用离散 database。非法 URI 报错不回显 URI 或口令。

离散配置仍支持：

```yaml
mode: development
development:
  datasources:
    mongodb:
      host: ${SF_COMPAT_MONGO_HOST}
      port: ${SF_COMPAT_MONGO_PORT}
      database: sf_compat
      username: ${SF_COMPAT_MONGO_USER}
      password: ${SF_COMPAT_MONGO_PASSWORD}
      authenticationDatabase: ${SF_COMPAT_MONGO_AUTH_DB}
      replicaSet: ${SF_COMPAT_MONGO_REPLSET}
      disable: false
```

`disable=true` 不连接、不扫描。框架模块仍保留 `EnhancementContext` 隔离；standalone 只有一个注册配置：active client 存在时再次 `configure`（包括 disabled configure）会被拒绝，先 `disconnect()`。`new MongoMongo(config)` 是调用方持有的直连对象，不注册为 shared current，也不由 `disconnect()` 关闭。

## 验证命令

runner 只起 loopback `sf_compat`，凭据在实例内 mode-600 env，不打印。假设 ServiceFramework 与 standalone 是 `$HOME/projects` 下的同级 checkout（不是的话改 `SF_ROOT`），`JDK8_HOME`/`JDK17_HOME` 指向你自己的 JDK 8 / JDK 17。下面的 `mvn` 用默认 settings；需要自定义 mirror 时单独加 `-s <your-settings.xml>`。

```bash
SF_ROOT="$HOME/projects/ServiceFramework"
JDK8_HOME=/path/to/your/jdk8
JDK17_HOME=/path/to/your/jdk17

# ServiceFramework：先 runner verify（run 会自己起实例），再跑模块 lane
cd "$SF_ROOT"
dev/mongo-modern-services.sh run -- dev/mongo-modern-services.sh verify
dev/mongo-modern-services.sh run -- \
  env SF_COMPAT_MONGO=true JAVA_HOME="$JDK17_HOME" PATH="$JDK17_HOME/bin:$PATH" \
  mvn -pl serviceframework-mongo -am \
  -Dtest=MongoEnhancementLiveTest -Dsurefire.failIfNoSpecifiedTests=false test

# standalone：必须从 mongomongo cwd 调 SF_ROOT 下的绝对 runner 路径
cd "$HOME/projects/mongomongo"
"$SF_ROOT/dev/mongo-modern-services.sh" run -- \
  env SF_COMPAT_MONGO=true JAVA_HOME="$JDK17_HOME" PATH="$JDK17_HOME/bin:$PATH" \
  mvn -Dtest=MongoStandaloneLiveTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

JDK8 验收把 `JAVA_HOME` 与 `PATH` 换到 `$JDK8_HOME`。最终独立验收结果：框架侧 `MongoEnhancementLiveTest` 在 8.3.11 上双 JDK 各 20/20（含在六模块同一产物矩阵内，矩阵 1234 executed + 1 skipped / lane），standalone `MongoStandaloneLiveTest` 在 8.3.11 上双 JDK 各 6/6，另有打包 jar 的仓库外探针通过；汇总见 [multi-database-verification-2026-09-26.md](multi-database-verification-2026-09-26.md)。历史上实现阶段记录过的 worker 自测（框架 Mongo 284 tests、standalone 早期 5 tests）只作历史保留，不代表最终结论。
