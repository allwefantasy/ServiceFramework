# 公共增强诊断和扫描峰值

公共诊断、批量原始摘要和扫描回调在 `serviceframework-common`。ORM、Mongo 和 Web 已经接上，细节在 [orm-compatibility.md](orm-compatibility.md)、[mongo-compatibility.md](mongo-compatibility.md) 和 [framework-extensions.md](framework-extensions.md)。没有增强结果缓存。同一份 Java 8 字节码在 JDK 8 和 JDK 17 上使用这套 API。当前整仓耗时在 [serviceframework-bytecode-migration.md](serviceframework-bytecode-migration.md)；更早的夹具数字在 [bytecode-performance.md](bytecode-performance.md)。

## 诊断开关

```java
EnhancementDiagnostics off = EnhancementDiagnostics.create(false, reportDir);
EnhancementDiagnostics on = EnhancementDiagnostics.enabled(reportDir);
on.noteSafeMetadata("orm-2", schemaSha256Hex); // 可选，64 个十六进制字符（256 位 SHA-256）
on.setEmitGeneratedSource(true);               // 默认 false
on.setEmitClassFiles(true);                    // 默认 false，须在 apply/define 之前打开
```

`noteSafeMetadata` 只接受两段显式文本：配置版本令牌（`[A-Za-z0-9._+-]{1,64}`，且不能含 password、passwd、secret、credential、jdbc）和可选的 schema 摘要。摘要必须是 64 个十六进制字符，也就是 256 位 SHA-256，不是 64 位整数。没有 `Settings`、`Map` 或原始配置参数。不合格的值会抛 `CONFIGURATION`，异常文本不回显被拒绝的内容。诊断关闭时这个调用直接返回，不校验、不保存。

零捕获基线是诊断关闭，并且 context 上没有 `EnhancementObserver`。这时 `apply` 和 `define` 都不会为了诊断或观察器去 `toBytecode`，不会做方法签名对比，不会算 SHA-256，不会写磁盘，也不调用 `affectedClasses`、`applyReason`、`skipReason`。`hashComputations()`、`bytecodeReads()`、`methodInspections()`、`diskWrites()` 保持 0。规则本身的 `matches` 和 `apply` 仍会执行。

诊断关闭但已经注册观察器时，`apply` 仍会读取原始类字节并交给观察器。诊断哈希、上面四个计数、方法对比和报告保持关闭。`applyReason` 和 `skipReason` 也仍然只在诊断打开时调用。`define` 会不会再读结果字节只看诊断开关，不看观察器。

打开诊断时，每个类在**第一次被修改之前**固定原始类字节的 SHA-256。随后按规则记录：

| 字段 | 含义 |
| --- | --- |
| `originalSha256` / `resultSha256` | 修改前类文件、define 前结果类文件 |
| `rule` / `version` | 规则 id 和 `version()` |
| `methods` | `added`、`rewritten`、`removed`，签名是 `name + JVM descriptor`，例如 `created()V`、`value()I` |
| `reason` | `applyReason` 或 `skipReason`，默认 `matched` / `not matched` |
| `failure` | 阶段失败文本；`jdbc:` URL 和 `password=` 这类赋值会先替换成 `[redacted]` |
| `loader` | `loaderClass@identityHash`，不是加载器对象 |
| `jdk` / `java8Target` | `java.specification.version`，以及目标主版本 52 |
| `configVersion` / `schemaDigest` | 上面的显式元数据，按事件记录当时的值 |

`originOf(className, signature)` 返回该方法最后一次新增、改写或移除的规则。`methodHistory` 保留更早的记录。报告是 `report.txt`，第一行 `# enhancement-diagnostics v1`，之后每个事件一行，键顺序固定。自由文本（failure、reason、methods）里的空格和 `=` 会百分号编码，签名字符 `()[];` 保持原样。

`rewritten` 表示同签名方法的修饰符、异常表或 Code 属性字节变了。只改注解、不动方法字节时不会出现在方法列表里。

类文件只有 `setEmitClassFiles(true)` 且诊断打开时才留在内存里，并在 `flush` 时写到 `original/<类名>.class` 和 `enhanced/<类名>.class`。这是修改前和 define 前的真实类字节，不是反编译。

`setEmitGeneratedSource(true)` 不会自己还原源码，也不会给复制进来的字节码补一份 Java 文本。`sources/` 里出现文件，只有规则作者在诊断打开时调用了 `emitSource(className, source)`。内置的 ORM、Mongo 和 Controller 规则没有调用它。两个开关默认都关。诊断关闭时即使把开关设上也不写。

`configVersion` 是调用方给出的应用配置修订，例如 Bootstrap 在诊断打开且调用方还没写修订时放入的 `app-1`。`orm-1`、`mongo-1`、`web-1` 是各模块的 schema 或类名摘要格式，不是应用修订。模块只在调用方没有修订时，才把这个格式令牌写进当时的事件。`schemaDigest` 始终是该模块自己的摘要。模块结束后，如果进来之前已经有修订，连同原来的摘要写回去。

## flush

`flush()` 成功后才会把 `flushed()` 置为 true。写失败时事件还在内存里，可以再调一次；目标文件是覆盖，不是追加，所以不会多出一行。写报告用临时文件再改名。写出失败和关闭流失败时，关闭错误挂在主 `IOException` 的 suppressed 上，再包成 `EnhancementFailure`。

`EnhancementContext.close()` 会调用 `flush()`。上下文关闭后自己的 diagnostics 字段会被清空，但调用方手里的那个对象仍可重试。`close()` 里资源关闭和 flush 若都失败，先发生的是主异常，其余用 `addSuppressed` 留下。

## 批量规则

`EnhancementPlan` 仍是稳定拓扑，不保存执行状态。同一个 context 里同一个类名的第二次 `apply` 仍是 `CONFLICT`。同一个 plan 可以给另一个 context 用。`executions()` 仍是「这次 `apply` 的入口类」上每个匹配规则一条，不会因为受影响类变多而多记。

新增的默认方法，旧规则不用实现：

```java
default List<CtClass> affectedClasses(CtClass type, EnhancementContext context) {
    return Collections.singletonList(type);
}
default String applyReason(CtClass type, EnhancementContext context) { return "matched"; }
default String skipReason(CtClass type, EnhancementContext context) { return "not matched"; }
```

诊断打开，或者 context 上注册了 `EnhancementObserver` 时，计划在规则循环之前就对入口类读取原始字节。`matches` 为 true 之后、`apply` 之前再调用 `affectedClasses`，并对返回的每个类（一定包含入口类）再读一次。已经读过的类不重复读。

诊断打开时，这次读取计入 `bytecodeReads`，并固定原始 SHA-256，然后才调用 `apply`。只有观察器、诊断关闭时，同样会 `toBytecode`，把字节交给观察器的 `beforeFirstMutation`，但不计算、不保存 hash，也不增加诊断计数、不写报告。字节数组只在回调期间有效。`applyReason` 和 `skipReason` 只在诊断打开时调用。`affectedClasses` 和两个 reason 方法不许改类。返回 null 是 `CONFIGURATION`，规则的 `apply` 不会跑。

诊断关闭且没有观察器时，不调用这三个方法，也不读字节。这是零捕获基线。

观察器要在第一次 `apply` 之前 `addObserver`。它不是诊断开关，也不会自己打开 hash。没有观察器、诊断又关着，就不会为了它去读类。有观察器而诊断关着，只交付原始字节。

## ORM 已经接上的受影响类

`EntityMappingRule.apply` 在第一棵匹配的类上调用 `new EntityEnhancer(JPA.settings()).enhance(session.roots())`，`EntityEnhancer` 会改整棵 `ModelClass` 树。后面的类看到 `entityMappingDone()` 就直接返回。规则已经声明受影响类，公共层不用再改 `EnhancementPlan`：

```java
@Override
public List<CtClass> affectedClasses(CtClass type, EnhancementContext context) {
    OrmSession session = OrmSession.current();
    if (session.entityMappingDone()) {
        return Collections.singletonList(type);
    }
    LinkedHashMap<String, CtClass> types = new LinkedHashMap<String, CtClass>();
    types.put(type.getName(), type);
    List<ModelClass> roots = session.roots();
    for (int i = 0; i < roots.size(); i++) {
        List<ModelClass> hierarchy = roots.get(i).hierarchy();
        for (int j = 0; j < hierarchy.size(); j++) {
            CtClass origin = hierarchy.get(j).originClass;
            types.put(origin.getName(), origin);
        }
    }
    return new ArrayList<CtClass>(types.values());
}
```

`OrmEnhancer.enhance` 的父类优先循环不用改。第一类匹配时 `entityMappingDone` 还是 false。诊断打开时，计划会在 `enhance(session.roots())` 之前把树里每个 `originClass` 的原始 hash 固定下来，并把方法差异记在 `entity-mapping` 上。只有观察器、诊断关闭时，同样会先读这些类的原始字节交给观察器，但不写 hash，也不记方法差异。之后各类自己的 `apply` 不再覆盖已经固定的 hash。`OrmQueryRule` 和 `AssociationRule` 只改当前 `CtClass`，继续用默认的 `affectedClasses` 即可，它们的方法差异记在各自的规则 id 上。

配置版本不要把 `JPA.settings()` 塞进诊断。应用修订由调用方 `noteSafeMetadata` 事先写上。ORM 只补数据库快照的 SHA-256，不把 `orm-1` 盖过已经写上的修订。

## Mongo 和 Web

`MongoDocumentRule` 把 `Document` 上的静态字段和方法复制到当前模型，超类方法体不动。`ControllerFilterRule` 同样只改当前控制器。这两条规则用默认「只含入口类」。Mongo 的摘要是声明字段和 RUNTIME 注解，Web 的摘要是排序后的控制器类名。两边都保留调用方已经写上的应用修订，只在没有修订时才使用 `mongo-1` 或 `web-1`。

Web 的 context 由 `ApplicationContext` 持有。诊断目录由调用方传入，common 不决定应用目录。`Bootstrap.configureSystem(settings, marker, diagnostics)` 会在诊断打开且还没有修订时写入 `app-1`。调用方如果已经写过修订，ORM、Mongo 和控制器都留着那一个，不用模块自己的 `orm-1`、`mongo-1` 或 `web-1` 去盖。集成冒烟在真正的 Bootstrap 里先写了 `config-20260924`，再同时拉起 ORM、Mongo 和控制器；两个 JDK 的事件和 `report.txt` 都留着这个修订。单独的 ORM、Mongo 测试仍然在没有调用方修订时使用 `orm-1` 和 `mongo-1`，Web 的生命周期测试仍然覆盖默认的 `app-1`。

## 扫描

`scanArchives(String)` 和 `scanArchives(URL...)` 仍一次打开并返回全部流，调用方负责关闭。这个所有权没有改。

`scanArchives(String, callback)` 改为每次只打开一个资源，回调返回后（`scanClass` 会关闭它）再打开下一个。回调失败时只关闭已经打开的那一个，其余资源还没打开。目录、JAR、带空格路径仍走原来的 URL 列表 API。

`peakOpenStreams()` / `currentOpenStreams()` 统计的是逻辑 `InputStream` 数量，不是操作系统文件描述符。JAR 列表 API 会把条目拷进 `ByteArrayInputStream` 后关掉 JAR，此时逻辑流数和 FD 不是一回事。列表 API 不观察调用方的 `close()`，所以峰值停在返回的流数量上。回调 API 在每个资源之间回到 0，峰值是 1。

旧回调体等价于先 `scanArchives(String)` 再 `scanClass(List, callback)`。基准用这条路径当基线，再用新的单资源回调对比峰值。耗时分布只作记录，不把它说成整个框架启动变快，也不把它说成操作系统文件描述符下降。全框架冷启动、重复启动和热调用已经测过，数字在 `docs/bytecode-performance.md` 的最后一节，原始输出在 `/tmp/sf-t15-final-phases/`。入口仍是 `dev/measure-framework-phases.sh`。

可复跑入口是 `dev/measure-enhancement-costs.sh`。它不 install、不写 `~/.m2`。产物是输出目录里的 `enhancement-costs.json` 和 `enhancement-costs.txt`。

## 这次没有做的缓存

诊断事件里已经有原始类 hash、规则 id/version、配置版本、schema 摘要、JDK 和 Java 8 目标。这些是以后按应用 context 做缓存键的材料。本次没有缓存增强结果，因此也不存在拿旧产物去跑新 schema 的路径。
