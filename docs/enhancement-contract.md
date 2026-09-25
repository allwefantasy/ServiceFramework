# ServiceFramework common 增强契约

本文是 `serviceframework-common` 的公共增强入口。ORM、Mongo 和 Web 已经按这个入口接入，见 [serviceframework-bytecode-migration.md](serviceframework-bytecode-migration.md)。Javassist 运行时是 **3.33.0-GA**。

## Javassist

`3.23.1-GA` 的 `toClass(loader, domain)` 在 JDK 17 上要打开 `java.base/java.lang`，不适合作为双 JDK 入口。父 POM 的 `javassist.version` 是 `3.33.0-GA`。`ClassDefiner` 在 Java 8 上调用 `CtClass.toClass(loader, domain)`，在其它运行时调用 `ClassPool.toClass(type, neighbor, loader, domain)`。两条路径之前都要有同包锚点，并且拒绝命名模块。classpath 上的无名模块不需要 `--add-opens`。公共代码不假设 Javassist 新建类的默认主版本是 52，定义前会把主版本写成 52。

## 类定义

`net.csdn.common.enhancer.ClassDefiner` 属于一个 `EnhancementContext`，不要做成全局单例。

- `registerAnchor(Class<?>)`：按**包名 + 加载器身份**登记。同一个 anchor 再登记是空操作；同一包、同一加载器上的另一个 anchor 直接冲突。
- `define(CtClass, ClassLoader, ProtectionDomain)`：返回实际定义出来的 `Class`。
- 未登记时查找 `{package}.ServiceFrameworkPackageAnchor`，常量是 `ClassDefiner.ANCHOR_SIMPLE_NAME`。调用是 `Class.forName(name, false, loader)`，不初始化。
- anchor 的加载器、包、`ProtectionDomain` 必须与请求**同一身份**（`==`）。对不上、没有 anchor、或这个 context 已经定义过该类，都在 `defineClass` 之前失败。
- 不要先加载待增强的目标类来充当 anchor，也不要临时生成 anchor，也不要 `--add-opens`。
- `ClassDefiner.isNamedModule(Class)` 用 Java 8 能编译的反射判断。JDK 8 没有模块 API，普通类和 `String.class` 都不是命名模块。JDK 17 上 classpath 类是无名模块，`java.lang.String` 才是命名模块。
- 从资源读入且主版本大于 52 的类，在定义前失败。`makeClass` 即使被 Javassist 标成更高版本，也会被改成 52。
- JDK 8 只执行 `CtClass.toClass(loader, domain)`。JDK 9+ 才执行 `ClassPool.toClass(type, neighbor, loader, domain)`。`ClassDefiner` 自己的常量池不引用 `java.lang.Module` 或 `MethodHandles`。
- anchor 契约对 JDK 8 和 JDK 17 是统一的：**两个 JDK 上每个目标包都要有一个已编译的 `ServiceFrameworkPackageAnchor`**（显式 `registerAnchor` 或按约定放在目标包里）。早期"JDK 8 可以不要 anchor"的用法属于兼容迁移点——迁到这套公共层时，为每个会被 `define` 的包补一个空 anchor 类即可。
- define 阶段的失败（找不到 anchor、loader/domain/包不一致、重复 define、主版本过高、`defineClass` 本身失败）都会进 `diagnostics().recordFailure`，报错文本带类名、阶段、`loader=` 身份、anchor 的 `anchorLoader=`/`anchorCodeSource=`、请求方的 `requestedCodeSource=` 和 `jdk=` 版本，方便定位是哪个加载器/哪个 jar；不会打印环境变量或业务 class 字节。

`EnhancementContext.define(CtClass)` 是快捷方法：目标加载器是 context 的加载器，保护域取已登记 anchor，否则取约定 anchor。

## 失败

`EnhancementFailure` 是 `RuntimeException`。字段是 `category`、`className`、`ruleId`、`phase`，原因在 `getCause()`。`getMessage()` 会带上这些位置，例如：

```text
[enhancement CONFLICT] phase=enhance class=demo.Order rule=bean-accessor inherited final method ...
```

类别：`CONFIGURATION`、`CONFLICT`、`DEFINITION`、`DEPENDENCY`、`SCAN`、`ENHANCEMENT`、`LIFECYCLE`、`UNSUPPORTED`。

不要捕获后只打印再返回 null。定义阶段失败不能假装类已经恢复，JVM 里已经定义成功的类也撤不掉。

## 规则和计划

```java
public interface EnhancementRule {
    String id();
    default int version() { return 1; }
    default List<String> requires() { return Collections.emptyList(); }
    default List<String> before() { return Collections.emptyList(); }
    boolean matches(CtClass type, EnhancementContext context);
    void apply(CtClass type, EnhancementContext context);
}
```

规则只改 `CtClass`，不调用 `toClass()` / `define`。`requires` 表示对方必须先执行；`before` 表示自己必须先于对方。`EnhancementPlan.compile(List)` 在改类之前检查重复 id、未知依赖和环，并用输入顺序做稳定拓扑排序。

`EnhancementPlan` 是不可变的拓扑结果，只持有排好序的规则，不持有 ClassLoader、CtClass 或任何执行状态。执行记录归 `EnhancementContext`：

- `apply(CtClass, context)` 先把类名记进 context 的增强记录再跑规则；**同一 context 里同一个类名的第二次 apply 一律 `CONFLICT`**——不管用的是不是同一个 plan、也不管上一次是成功了还是中途失败（CtClass 可能已被改过）。
- 反过来，同一个 plan 对象可以安全地给**另一个 context** 的同名类用；两个 context 各自的 `executions()` 互不影响。
- 执行报告读 `context.executions()`，是 `EnhancementPlan.Execution`（className/ruleId/version）的只读快照，context 关闭后仍可读。
- 未匹配的规则在 diagnostics 打开时记一条 `phase=skip` 事件；diagnostics 关闭时 skip 不产生任何 hash/dump 开销。

调用方在 `apply` 成功之后自己 `define` 一次。

登记方式是显式的 `EnhancementRules`，不是 ServiceLoader，避免为了发现实现类而提前加载业务类：

```java
EnhancementRules rules = new EnhancementRules();
rules.register(entityMapping);   // id entity-mapping
rules.register(ormQuery);        // requires entity-mapping
rules.register(association);     // requires entity-mapping
EnhancementPlan plan = rules.compile();
```

预定 id 在 `EnhancementRuleIds`：`entity-mapping`、`orm-query`、`association`、`mongo-document`、`controller-filter`。common 不注册这些规则，也不依赖 ORM、Mongo、Web。ORM 查询和关联都依赖 `entity-mapping`；Mongo、Web 各一条、没有 requires。

## 上下文

```java
try (EnhancementContext context = EnhancementContext.open(targetLoader)) {
    context.classDefiner().registerAnchor(OrderPackageAnchor.class);
    try (EnhancementContext.Scope scope = context.activate()) {
        EnhancementContext current = EnhancementContext.currentOrNull();
        CtClass model = current.get("demo.Order");
        plan.apply(model, current);
        Class<?> defined = current.define(model);
    }
}
```

- `open(ClassLoader)` / `open(ClassLoader, EnhancementDiagnostics)`。加载器不能是 null。每个 context 有自己的 `ClassPool`（系统路径 + `LoaderClassPath`）、`ClassDefiner`、属性表。
- `setAttribute` / `getAttribute` 只在这个 context 里。没有静态全局缓存，common 也不提供“当前应用”单例。Web 的 `ApplicationContext` 保存应用，请求代码走 `ServiceFramwork.currentInjector()`；没有 scope 时才回到默认应用。
- `activate()` 把 context 压进**当前线程**的栈，返回 `Scope`。嵌套关闭恢复上一层；最外层关闭会 `ThreadLocal.remove()`。跨线程关闭、或者外层比内层先关闭，都会失败。异步线程必须拿到这个 context 再自己 `activate()`，不能读调用线程的 ThreadLocal。
- 线程安全边界：`activate()`、`Scope.close()` 的登记和 `context.close()` 在同一把锁上串行化，context 一旦关闭就不存在还能开出新 scope 的窗口；scope 内具体的增强操作限定在持有线程上，不做内部同步。属性、track 等初始化调用假定在启动/持有线程完成。
- `track(Closeable)` 和 `makeClass` / `get` 跟踪的 `CtClass` 在 `close()` 时按登记的反序关闭。`CtClass.detach()` 只丢掉 Javassist 的类文件缓存，**不是**类卸载。已经 `define` 进目标加载器的类仍然留在那个加载器里，直到加载器自己可以被回收。
- `close()` 还会摘掉 `ClassPool` 上的 `LoaderClassPath`、清掉 `ClassDefiner` 的 anchor/defined 记录，并释放 context 对 pool、definer、loader 的引用；要完全回收仍需调用方放下对 context 本身的引用。多个清理步骤失败时，第一个异常抛出、其余以 `addSuppressed` 保留，不会只留第一个丢其他。
- `close()` 可重复调用。还有 Scope 没关时拒绝关闭（此时 context 保持可用）。关闭之后再使用、再 `activate()` 都会失败。ApplicationContext 持有 context 时应**先在各自持有线程关掉所有 scope，再 close context**。

## 诊断

```java
EnhancementDiagnostics off = EnhancementDiagnostics.create(false, reportDir);
EnhancementDiagnostics on = EnhancementDiagnostics.enabled(reportDir);
on.setEmitGeneratedSource(true); // 默认 false
```

打开时，`apply` 记录原始字节的 SHA-256、规则 id、版本、声明方法数量变化、阶段 `apply` 和耗时；`define` 记录结果 SHA-256、加载器身份、阶段 `define` 和耗时；规则未匹配记 `phase=skip` 事件。失败（包括 define 的预验证失败）会记阶段和消息。`close()` 调用 `flush()`，只把已经算好的文本写到调用方目录里的 `report.txt`，**不会在关闭时再做 hash 或导出 class 字节**。诊断关闭时不 hash、不写目录、skip 也不留痕。`emitSource(className, source)` 只有显式打开生成源码开关才落盘，默认不把业务常量写出去。

## DynamicBytecode

`copyStaticFields` 用 `new CtField(source, target)`，属性进目标常量池。会保留编译期常量（`ConstantValue`）、泛型签名和注解。**<clinit> 和非常量静态初始化不会复制**。`public static final String CODE = "A"` 能复制出 `"A"`；`static { label = "run" + "time"; }` 复制后仍是默认值 null / 0。

`copyStaticMethods` 会把已复制到目标类上的 `parent$_` 静态字段读写改到子类自己的字段。其它类引用不动。因此每个子类的 `parent$_map()` 懒初始化是各自的 `Map`，不会和父类或兄弟类共用。调用顺序是先复制字段，再复制方法。

生成 getter/setter 之前会看完整父类链和接口：静态方法、final 方法、对不上的返回类型（不是协变返回）都是 `EnhancementFailure`，规则 id 为 `bean-accessor`。继承来的 final 同签名方法不会静默继续用父类实现，因为那会读到被遮蔽的父类字段。

`SetterBody.beforeAssignment` 返回的源码读取局部变量 `value`，不要在旧方法体里做字符串换名。

- 没有精确 setter：先 `this.field = value`，再把 `value` 设成字段的最终值，然后执行 snippet。
- 已有精确 setter 且 `replaceSetter=false`，或者 snippet 为空：原方法保留，其它重载也不动。
- 已有精确 setter 且 `replaceSetter=true` 且 snippet 非空：**原方法保持原名、原修饰符（synchronized/final/访问级别）、throws 和注解不动**，snippet 被编译成一个私有 synthetic 的 `setX$sfHook(Type value)` 辅助方法，再用 `insertAfter(asFinally=false)` 在原方法正常返回前调用 `helper(this.field)`。因此：业务副作用原样执行；成功后 hook 拿到的是字段最终值（Mongo 的 `attributes.put(translateFromAlias(...), value)` 写到的是转换之后的值）；原方法抛异常时 helper 不执行；`synchronized` 方法上 helper 仍在 monitor 内运行。abstract/native 的精确 setter 无体可插，按 `CONFLICT` 报出。
- 同一个 setter 再插一次是冲突（原方法带内部标记属性），不会叠第二份 hook。

## 扫描

`DefaultScanService.scanArchives(String)` 不再调用自己。它用 common-utils `ClassPath.getTopLevelClassesRecursive` 找到类，按资源名排序，再用该加载器的资源名或 `URL.openStream` 打开。`scanArchives(URL...)` 支持：

- `file:` 指向 `.class`、目录、普通 JAR（路径里的空格按 URI 解码，`%20` 可用）
- `jar:` 指向单个 `.class` 条目

非 `.class` 跳过。目录或 JAR 里的嵌套 JAR **不**打开。回调返回 null 表示未匹配，不会放进结果。回调抛出的异常保留为 `EnhancementFailure` 的 cause，阶段是 `scan`，已经打开的流都会关闭。某一个 URL 打开失败时，前面已经打开的流也会关闭。

## 已经接入的模块

ORM、Mongo 和 Web 调用原来的 `DynamicBytecode` 方法。setter 替换的时机在 common 内部，Mongo 的 snippet 继续引用 `value`。类定义在 context 上 `define`，规则里不调用 `toClass()`。启动失败带出 `EnhancementFailure` 的类名、规则、阶段和 cause，并 `close()` context。

当前整仓计数是每个 JDK 1212 个测试、0 跳过，记录在迁移说明里。更早一次只跑 common、不跑 Maven 的 `OK (539 tests)` 是历史片段，不是这个数字。
