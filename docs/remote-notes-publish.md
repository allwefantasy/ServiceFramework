# 把 remote-notes 发布到 RemoteService

日期：2026-09-25。这份说明对应已经在服务器上跑通的那条路：在本仓库编出应用和 ServiceFramework，送到 RemoteService，本机用 HTTP 验收，之后可以只换应用、只换框架，或者把 `current` 指回上一份发布。

当次测到的接口、ActiveORM 能力和 MySQL 读数在 [remote-notes-report-2026-09-25.md](remote-notes-report-2026-09-25.md)。不要把 [remote-mysql-web-case.md](remote-mysql-web-case.md) 当成部署说明，那一页是 Mac 上临时起 HTTP，经 SSH 转发去访问同一套库。

## 服务器上现在是什么

| 项 | 值 |
| --- | --- |
| SSH | `remoteservice`，`192.168.110.116`，主机名 `williampc-MR-X5` |
| 安装目录 | `/home/william-pc/softwares/serviceframework-remote-notes` |
| 当前发布 | `current` → `releases/20260925112110` |
| HTTP | `http://192.168.110.116:19110`，进程监听 `*:19110` |
| JDK | `/home/william-pc/softwares/infinity-sql-spark412-2.4.10-codex/jdk17/bin/java`，Temurin 17.0.19 |
| MySQL | 服务器本机 `127.0.0.1:3306`，库 `sf_serviceframework_e2e`，账号 `sf_e2e`，`mysql_native_password` |
| 表 | `sf_remote_tag`、`sf_remote_note` |

这台 Mac 上的 Java 直接连 `192.168.110.116:3306` 会 `No route to host`。部署好的服务跑在服务器上，它自己连本机 MySQL，Mac 只访问 19110。不要为了这个服务去改 `root` 的认证插件，也不要使用 `notebook`。

口令不进仓库、不进命令行、不进文档。本机沿用 `SF_COMPAT_ENV_FILE`，默认 `/tmp/sf-remote-mysql.env`，权限必须是 `600`，而且里面的库和账号只能是上面这两个名字。发布脚本把它写成服务器上的 `mysql.env`，权限同样是 `600`。渲染出来的 `config/application.yml` 也是 `600`。

## 仓库里各放了什么

应用是 Maven 模块 `apps/remote-notes`，已经加进根 `pom.xml`。模型和控制器在 `net.csdn.remotenotes`。版本号写在 `AppVersion`：`VALUE` 是 `GET /health` 里的 `appVersion`，`MARKER` 是同一次响应里的 `marker`。换应用对外行为时改这两个常量。

| 路径 | 作用 |
| --- | --- |
| `dev/remote-notes/publish.sh` | 在本机编译，打成一份发布目录，rsync 到服务器并重启 |
| `dev/remote-notes/remote-notes.sh` | 放在每一份发布的 `bin/` 里，负责渲染配置、建表、启停 |
| `dev/remote-notes/exercise.sh` | 从本机打 17 个请求 |
| `apps/remote-notes/src/main/resources/schema.sql` | 只建那两张表，已有表不会删 |
| `apps/remote-notes/src/main/resources/config/application.yml.template` | 口令位置是 `@PASSWORD@`，启动时才替换 |

`publish.sh` 的第一个参数：

| 参数 | 做什么 |
| --- | --- |
| `all` | 安装框架和应用，给四个 `serviceframework-*.jar` 盖上本次 buildId，整包发布 |
| `app` | 只重编 `apps/remote-notes`。框架 jar 用上一次 `all` 或 `framework` 留在 `dev/remote-notes/.publish/framework-lib` 里的那一份 |
| `framework` | 重新 `mvn -pl apps/remote-notes -am install`，盖上新的 buildId，应用源码保持工作区里的现状 |

没有做过 `all` 或 `framework` 时，`app` 会停下来，因为它没有可复用的框架 jar。

本机编译用 JDK 17。未设置 `JAVA_HOME` 时，脚本自己找 17。产物字节码是 Java 8，所以服务器上的 JDK 17 能跑。不要用系统里那个找不到的 `java`；服务器 PATH 上没有 Java，脚本用上面的绝对路径，也可以用 `SF_REMOTE_JAVA` 换掉。

## 第一次发布

在仓库根目录：

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export SF_COMPAT_ENV_FILE=/tmp/sf-remote-mysql.env
dev/remote-notes/publish.sh all
dev/remote-notes/exercise.sh
```

`exercise.sh` 的地址默认是 `http://192.168.110.116:19110`。要换端口或主机时设置 `SF_REMOTE_NOTES_URL`。17 项都打印 `PASS`，最后一行是 `RESULT 17/17`。有失败时脚本退出码不是 0。

发布脚本做的事，按顺序是：

1. 检查 env 文件权限，以及库名、账号是不是 `sf_serviceframework_e2e` 和 `sf_e2e`。
2. 按参数编译。
3. 在 `apps/remote-notes/target/remote-notes-dist` 装好 jar、依赖、配置模板、建表 SQL 和控制脚本。`all` 和 `framework` 会把 `META-INF/sf-framework-build.txt` 写进框架 jar，内容是这次的发布号。
4. rsync 到 `releases/<发布号>/`。发布号是本机 `date +%Y%m%d%H%M%S`。
5. 更新服务器上的 `mysql.env`，把 `current` 指到这份发布。
6. 停掉旧进程，在服务器上执行建表，再启动。

启动时工作目录就是那份发布。`config/application.yml` 由模板和 `mysql.env` 生成。Java 命令是：

```text
java -Xms256m -Xmx512m -Dfile.encoding=UTF-8 \
  -cp <release>/lib/*:<release>/remote-notes.jar \
  net.csdn.remotenotes.RemoteNotes
```

classpath 上的 `lib/*` 由 JVM 展开，不要让 shell 先展开。日志在安装目录的 `logs/console.log`，PID 在安装目录的 `remote-notes.pid`。健康检查通过之后，脚本才打印 `started`。

`GET /health` 会带回应用版本、发布号、实际加载的应用 jar / ORM jar / Web jar 的文件名和 SHA-256，以及 MySQL 的库、用户、版本和主机名。ORM 和 Web 的 `buildId` 来自各自 jar 里的那份文本，不是启动参数。应用 jar 没有这个 buildId。

## 只更新应用

改 `AppVersion`，或者改控制器、模型。然后：

```bash
dev/remote-notes/publish.sh app
dev/remote-notes/exercise.sh
```

这次不会重编框架模块，上传的 `serviceframework-*.jar` 来自 `.publish/framework-lib`。更新完成后，`/health` 里的 `appVersion` 和 `marker` 变成新值，`framework.orm.buildId` 和 ORM 的 SHA-256 保持不变。2026-09-25 从 `1.0.1` 到 `1.1.0` 就是这样：应用 jar 变成 `c6d05e57c07752b07827b82b01eda06beac7633e238082fc3485d4398921385c`，框架 buildId 仍是 `20260925111759`。

已经写入的行还在。建表是 `CREATE TABLE IF NOT EXISTS`，发布不 `DROP`。可以用更新前记下的 id 再 `GET /notes/<id>`。

## 只更新框架

框架源码有改动，或者就是要把当前仓库里的框架 jar 再送上去：

```bash
dev/remote-notes/publish.sh framework
dev/remote-notes/exercise.sh
```

这会重装 `serviceframework-common`、`serviceframework-orm`、`serviceframework-mongo`、`serviceframework-jetty-9-server`、`serviceframework-web` 和 `remote-notes`。运行时用到的是 common、orm、jetty、web。mongo 依赖没有打进这份服务。四个框架 jar 盖上新的发布号。应用版本仍是工作区里的 `AppVersion`；如果应用源码没改，应用 jar 的 SHA-256 可以和上一份相同。

2026-09-25 这一次，应用停在 `1.1.0` / `republished`，应用 jar 的 SHA-256 没变。框架 buildId 从 `20260925111759` 变成 `20260925112110`。ORM jar 从 `ecbacb81…6445e` 变成 `62aacbb2…371d3`，Web jar 从 `5ba0a76d…769a` 变成 `f5b6a103…b4f8`。笔记 `5` 还在，17 项请求再次通过。

## 回滚

每一份发布都留在 `releases/`。`current` 只是符号链接。控制脚本用 `pwd -P` 解析自己的位置，所以下面这样走 `current/bin` 是安全的。不要用逻辑路径一层层 `..` 去猜安装目录，否则 PID 文件会指到安装目录的上一级，停不掉进程。

在服务器上，把 `<旧发布号>` 换成 `ls releases` 里看到的目录名：

```bash
ROOT=/home/william-pc/softwares/serviceframework-remote-notes
export SF_REMOTE_JAVA=/home/william-pc/softwares/infinity-sql-spark412-2.4.10-codex/jdk17/bin/java
bash "$ROOT/current/bin/remote-notes.sh" stop
ln -sfn "$ROOT/releases/<旧发布号>" "$ROOT/current"
bash "$ROOT/current/bin/remote-notes.sh" start
```

再从 Mac 看 `GET /health`。`releaseId` 应是旧发布号；如果那份发布用的是另一套框架 jar，`framework.orm.buildId` 和 SHA-256 也会回到那一套。数据仍在同一张表里。

2026-09-25 把 `current` 从 `20260925112110` 改到 `20260925112043`，健康检查回到框架 buildId `20260925111759`，ORM SHA-256 前缀 `ecbacb81fdae`。再按同样的步骤指回 `20260925112110`，buildId 回到 `20260925112110`。

从 Mac 上操作时，把上面的命令交给 `ssh remoteservice`。不要把 `mysql.env` 或 `application.yml` 打出来。

## 自己看一眼

```bash
curl -sS http://192.168.110.116:19110/health
curl -sS http://192.168.110.116:19110/notes/5
```

服务器上确认进程和端口：

```bash
ssh remoteservice 'ss -lnt | grep 19110; readlink -f /home/william-pc/softwares/serviceframework-remote-notes/current'
```

`mysql.env` 和当前发布里的 `config/application.yml` 都应该是 `600`。`stat -c %a` 只看权限，不要 `cat` 这两个文件。

## 不要做的事

- 不要把口令写进 git、Maven 参数、文档或聊天记录。`dev/remote-notes/.publish/` 里有一份本地 `mysql.env` 和框架 jar 缓存，已在 `.gitignore` 里。
- 不要对 `root` 改认证插件，不要使用 `notebook`。
- 不要只对 `serviceframework-web` 跑 `surefire:test` 来代替这次部署。那是另一条测试路径，而且会拿到已安装的旧 jar。
- 不要把独立仓库 `active_orm` 的 jar 和这里的 `serviceframework-orm` 放进同一个 classpath。
- 19110 如果已经被占用，启动会失败并留下日志。先确认 `ss`，再改模板里的 `http.port` 和 `remote-notes.sh` 里的 `PORT`，两处要一起改。
