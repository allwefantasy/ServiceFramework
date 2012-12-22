#ServiceFramework Wiki



##  创建一个新的ServiceFramework 项目


###ServiceFramework 适合你吗？

ServcieFramework 定位在 **移动互联网后端** 领域。
所以ServcieFramework非常强调开发的高效性，其开发效率可以比肩Rails(不相信？可以体验一下哦)。

1. 拥有Java界最简单，非常高效，且真正的富Model层
2. Controller层含有便利的函数库，简洁高效的验证器，过滤器
3. 简单但实用的View层，天然支持JSON,XMl格式输出

框架提供了对mysql([ActiveORM](https://github.com/allwefantasy/active_orm)),mongodb([MongoMongo](https://github.com/allwefantasy/mongomongo))的支持.
对象缓存正在开发中。

如果你面对的是一个遗留项目或者遗留的数据库，那么ServiceFramework不适合你。我们倾向于在一个全新的项目中使用它。
相信你会为Java也能做到如此的简洁而惊讶，如此高效的开发而窃喜。

现在让我们了解下 ServiceFramework 吧。



### 搭起来，跑起来

在终端下赋值黏贴运行该命令:

```shell
git clone git://github.com/allwefantasy/ServiceFramework.git ServiceFramework
```
此时你就获得一个开箱即用的项目。所有的目录和结构都是规范化的。

####我们先看看目录结构:

<table>
  <tbody><tr>
		<th>文件/目录</th>
		<th>作用</th>
	</tr>
	<tr>
		<td>src/</td>
		<td>包含 controllers, models, views。也就是项目源码的存放地。 在之后的教程中，我们会聚焦于这个目录</td>
	</tr>
	<tr>
		<td>config/</td>
		<td>配置文件。整个ServiceFramework只有两个配置文件，分别为application.yml 和logging.yml  更详细的配置介绍参看:<a href="#">配置 ServiceFramework 应用</a></td>
	</tr>
	<tr>
		<td>bin</td>
		<td>存放编译，部署，运行脚本</td>
	</tr>
	<tr>
		<td>sql/</td>
		<td>项目的数据库结构文件。通常是sql文件</td>
	</tr>
	<tr>
		<td>doc/</td>
		<td>项目的文档存放地</td>
	</tr>
	<tr>
		<td>lib</td>
		<td>应用本身，以及包括ServiceFramework依赖的jar包都会存放在这里</td>
	</tr>
	
	<tr>
		<td>logs/</td>
		
		<td>应用程序日志文件</td>
	</tr>
	<tr>
		<td>script/</td>
		<td>一些shell脚本之类的</td>
	</tr>
	<tr>
		<td>client</td>
		<td>你可以写一些客户端，比如使用某种脚本语言，做数据迁移啥的</td>
	</tr>
	<tr>
		<td><span class="caps">README</span>.html</td>
		<td>请对你的项目做一个简要的介绍</td>
	</tr>
	
	<tr>
		<td>test/</td>
		<td>单元测试目录。详细参看:<a href="#">如何测试ServiceFramework应用</a></td>
	</tr>
	
</tbody></table>

##运行测试前或者启动应用的准备工作。

- 在你的mysql中新建一个库，名称为：wow
- 运行sql目录下的 wow.sql,把所有的表建好。

这应该就是所有准备工作了。但是您的端口可能不是默认的3306,所以您还应该检查下，并且修改username和password

```
config/application.yml 
```
文件中的

```yaml
development:
    datasources:
        mysql:
           host: 127.0.0.1
           port: 3306
           database: wow
           username: root
           password: root
```


##如何运行测试
项目src目录下有一个com.example 示例程序。实现的是一个简单的tag系统。

在test 目录中 test.com.example 有example项目的测试代码。
test 根目录下的有个文件叫

```java
DynamicSuiteRunner 
```

你可以在IDE中启动它来运行整个测试集。

## 如何启动应用。

你可以在IDE运行

```java
net.csdn.bootstrap.Application 
```
当然你也可以写一个类继承它。然后运行这个新的类。

如果你不希望使用IDE.你可以直接进入项目，然后运行:

```shell
./bin/run.sh start
```

默认开启9400端口。你可以修改config/application.yml文件来改变端口。
接着可以通过curl 进行测试访问。
举个例子:
常见一个tag_group:

```java
curl -XPOST 'http://127.0.0.1:9400/tag_group' -d 'name=java'
```

这个时候你可以查看数据库，应该就有相应的记录了。


## Model 
这个章节，我们会知道 ServiceFramework 模型层 完整的使用。

首先，建立四张示例表:

```sql
--标签表
CREATE TABLE `tag` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) DEFAULT NULL,
  `tag_synonym_id` int(11) DEFAULT NULL,
  `weight` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8;

--标签组。一个标签可以属于多个标签组。一个标签组包含多个标签
CREATE TABLE `tag_group` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(32) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `id` (`id`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8;

--博客和标签的关联表。存有 博客id和标签id
CREATE TABLE `blog_tag` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `tag_id` int(11) DEFAULT NULL,
  `object_id` int(11) DEFAULT NULL,
  `created_at` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--标签近义词组。一个标签只可能属于一个标签近义词
CREATE TABLE `tag_synonym` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(32) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
```


对应的类文件:

```java
/**
 * User: WilliamZhu
 * Date: 12-7-23
 * Time: 下午4:52
 */

public class Tag extends Model {
    @Validate
    private final static Map $name = map(
    presence, map("message", "{}字段不能为空"),
    uniqueness, map("message", "{}字段不能重复")
    );

    @OneToMany
    private List<BlogTag> blog_tags = list();

    @ManyToMany
    private List<TagGroup> tag_groups = list();
}



public class BlogTag extends Model {

    @ManyToOne
    private Tag tag;
}


public class TagGroup extends Model {
    @ManyToMany
    private List<Tag> tags = list();
}


public class TagSynonym extends Model {
    @OneToMany
    private List<Tag> tags = list();
}


```

初看模型，你可能会惊讶于代码之少，关联配置之简单。是的，上面就是我们对模型类所有的配置了。你不需要显示声明属性和设置get/set方法，
但是就是这些代码已经可以满足你80%的需求了。哈哈，那让我们
一步一步来看ServiceFrame是如何为你带来这些魔法的。

我们先介绍一下示例中模型的关系:

1. TagGroup 和 Tag是多对多关系
2. Tag和BlogTag是一对多关系。
3. TagSynonym 和Tag 是多对一关系

建立模型类只需要两步:

1. 继承 Model 基类
2. 声明集合属性时需要初始化它

ServiceFramework 为你提供了大量便利方法。比如建立map/list

```java
Map newMap = map();
Map newMap2 = map("key1","value1","key2","value2")
List newList = list();
List newList2 = list("value1","value2","value3");
```
所以集合初始化的时候也变得很简洁，比如示例代码中

```java
@ManyToMany
private List<Tag> tags = list();
```

### 表和模型之间的映射关系
前面的例子可以看到，我们不需要进行任何表和模型之间的映射配置。这依赖于默认的命名约定。这些规则包括：

1. 类名为驼峰命名法，表名则为UnderScore的形式。比如TagWiki 在数据库相应的表名也为 tag_wiki
2. 外键名称 = 属性名+"_id".
3. 属性名 = 小写 加 下划线的形式。比如示例中的 tag_groups 等。 这和java的传统命名会有些区别。
这主要是为了数据库字段和Model属性名保持一致。如果你使用"tagGroups"这种传统的驼峰命名方式,
那么数据库中的字段名就会很丑陋了。遵循现在的方式你会发现这是相当便利的一种方式。

4. 根据语义区分单复数形式 
5.强烈推荐使用自增id,名称为id,并且为interge类型。这可以省掉很多麻烦


### 模型属性

Model类会自动根据数据库获取信息。所以你无需在Model中定义大量的属性。ServiceFramework会根据数据库表信息
自动生成这些字段。

假设Tag 含有一个name 属性，可以这样获取它。

```java
Tag tag = Tag.find(17);
String name = tag.attr("name",String.class);
```
赋值的话可以这么做:

```java
tag.attr("name","jack");
```

当然你也可以手动定义这些属性，这不会带来任何问题，而且可以获取IDE工具的代码提示。

### 关联关系

关联关系可以做两件事情:

1. 告诉框架模型(表)之间的外键关系
2. 可以方便的级联保存，更新操作

***WARNING***: 关联关系不应该用来查询。比如 你不应该通过 tag.getBlogTags()获取相关的BlogTag.即使是blogTag.getTag() 这样获取一个对象也不行。在后续的文档中你会看到一个可控性更好，不需要你具有任何ORM知识，规范的查询方式。

ServiceFramework 支持标准的三种种关系。

* OneToOne
* OneToMany
* ManyToMany

在ServiceFramework中，所有关系都是双向的。当然，你不必担心这些，你了解这些细节固然好，但是
不了解也没有关系。你只要按照直觉通过四个注解声明模型类的关系即可。

通常的ORM框架，比如Hibernate,进行关系操作是比较复杂的。比如多对多关系，如果你添加一个关系，你需要
这样做:

```java
tagGroup.getTags().add(tag);
tag.getTagGroups.add(tagGroup);
tag.save();
```

另外你可能还需要小心主控端。因为某些情况只有对主控端持久化，才会将关联关系(外键)设置好。

但是在ServiceFramework 你完全不用担心什么主控端什么的额。对于刚才的示例，ServiceFramework可以这样：


```java
 tagGroup.associate("tags").add(tag);
```
这个时候tag与tagGroup的关系就已经建立在中间表了。相应的

```java
 tagGroup.associate("tags").remove(tag);
```

会删除中间表相关的记录。
你也可以用 将tagGroup添加到tag的tagGroups中。效果是一样的。这说明你不需要区分主控端。

上面我们举的是多对多的例子，实际上也适用于一对多和一对一的关系。

常见的应用场景是，当你创建一个tag的时候，你需要把它添加到一个已经存在的tagGroup中。
上面的关联关系就可以帮上忙了。


associate方法的参数 “tags“  就是我们定义在TagGroup 中的一个属性。ServiceFramework默认会为
这种集合映射属性添加一个同名的方法，并且返回Association。

类似于:

```java
public Association tags(){throw new AutoGeneration();}
```

associate 只是帮你调用这些看不到的方法。
为了获得IDE提示的好处，
你可以把上面那段代码写进你的模型类中。ServiceFramework会去实现里面具体的细节。

```java

public class TagSynonym extends Model {
    @OneToMany
    private List<Tag> tags = list();
    public Association tags(){throw new AutoGeneration();}
}
```

现在假设我们要获取一个同义词组所有的d>10的tag，我们可以这么做

```java
List<Tag> tags = tagSynonym.tags().where("id>10").fetch(); 
```
当然，你依然可以写成

```java
List<Tag> tags = tagSynonym.associate("tags").where("id>10").fetch(); 
```
结果是一样的。对于这种只是为了代码提示而创建的方法，我们推荐方法内部 填充 'throw new AutoGeneration()'来标记它会被框架自动实现。虽然，即使它不存在，系统也会创建它。

经过上面的例子可以看出模型的关联关系可以给我们带来很多便利。这包括从表单获取多个model进行级联保存。

WARNNING: 集合属性的名称都会有一个同名的方法。这个方法名会被框架保留使用。所以，不要用这个名称来定义对你来说有其他用处的方法。


###表单和模型类

通常表单需要填充模型类。这就和Controller层扯上了关系。为了不使得后面的例子让你困惑，
我们先提一点Controller的预备知识。

ServiceFramework Contoller层获取参数的方式是通过params()函数。

比如:

```java
   String name = param("name");
```

如果不传递key值。那么

```java
 Map params = params();
```

这类似于

```java
 Map params = request.getParamsAsMap();
```

好。Controller我们就讲到这。

假设我们要创建一个tag

```java
Tag tag = Tag.create(params());
```

每个模型类默认就会有一个接受map对象的create静态方法。该类会利用map自动填充模型类。

另外，参数支持子对象属性填充。
假设 Tag 有个属性是 tag_wiki的对象属性，你想同时填充它，传参可以这样：

```java
name=java&tag_wiki.name=这真的是一个java标签
```
目前ServiceFramework支持两级填充，这意味着

```java
tag_wiki.tag_info.name 
```
这种形式是不被支持的。

form表单和Model类的字段命名会有差别，我们提供了一个简单的方法来解决

```java
Tag tag = Tag.create(selectMapWithAliasName(params(),"tag_name","name")); 
```

selectMapWithAliasName会将tag_name 替换成name.其他不变。

### 查询接口

ServiceFramework 提供了一套便利，规范，高效，且拥有部分HQL对象特色的查询功能。如果你熟悉Rails框架，那么你便能看到ServiceFramework 借鉴了他那套优秀的 "Query Interface"。

为了高效，规范化的操作数据库, ServiceFramework 提供了众多的查询方法. 每个查询方法允许你传递参数执行特定的查询而不需要你写令人烦躁的sql语句。

方法列表:

* where
* select
* group
* order
* limit
* offset
* joins
* from

以后我们会继续完善。添加更多方法，譬如 lock,having等。

从这些关键字可以看出，这些方法基本是以Sql关键字为基础的。所有这些方法最终返回的是JPQL对象(ServiceFramework内部组装sql语句的一个类)。

1.1 根据ID获得对象

```java
Tag.findById(10)
//或者
Tag.find(10)
```
1.2 根据多个ID获取

```java
Tag.find(list(1,2,,4,5))
```
1.3 条件查询

```java
Tag.where("id=:id",map("id",7)).fetch();
```
map 是一个创建Map的一个便利方法。

你也可以使用一个更复杂的例子:

```java
Tag.where("tag_synonym=:tag_synonym",map("tag_synonym",tag_synonym));
```
还记得之前提到的，对象关联关系的建立，可以方便框架进行一些对象化的操作。在Tag中tag_synonym是一个对象属性，你可以直接在where中使用该属性。
他会转为为类似:

```java
select * from Tag where tag_synonym_id=? 
```
因为对象关联模型告诉了系统那个是外键。这不会带来任何性能方面的损耗。

1.4 order

```java
Tag.order("id desc")
```
或者

```java
Tag.order("id desc,name asc")
```

1.5 joins

joins 语法也是对象化的，这也得益于我们之前简单的模型关系声明。你所操作的就是相应的模型属性。不管简单属性还是对象属性。

```java
Tag.joins("tag_synonym").fetch();
```

那么 tag对象的tag_synonym 属性会自动得到填充。这不会有n+1问题。因为一条SQL语句就搞定了

你也可以join多个属性

```java
Tag.joins("tag_synonym left join  tag_groups left join blog_tags").fetch();
```
当然，对于互联网应用，这么多join毫无疑问会拖垮你的数据库。我们只是举个例子，你不应该这么做。

1.6 offset,limit

```java
Tag.offset(10).limit(15);
//这相当于
select * from Tag limit 10,15;
```

1.7 select 

这通常用于你不想获取所有的字段的场合

```java
 List<Object[]> results =Tag.select("name").fetch();
```

这通常返回是一个数组。当然，如果你想让它填充进一个模型也是可以的。

```java
 List<Tag> results =Tag.select("new Tag(name)").fetch();
```
需要注意的是，你需要在Tag填充一个相应的构造方法。希望不久就能去掉这个限制。嗯，应该尽力去掉。

1.8 group
说实话，真不应该提供这个，性能杀手。不过还是提供了….

```java
Tag.where("id>10").group("name").fetch();
```

###Name_Scope
假设tag需要审核。只有审核通过的才应该被查询出来。如果每次查询的时候都要加这个条件岂不是
太麻烦？我们可以定义一个方法：

```java
@Entity
public class Tag extends Model {
    public static JPQL active(){
      return where("status=1");
    }
}
```

之后你就可以这么用了

```java
Tag.active().where("id>10").join("tag_groups").offset(0).limit(15).fetch();
```


### 模型方法

在ServiceFramework中。一旦你定义了模型类，那么该模型类会自动拥有众多的方法。一些静态方法:

```java
  Tag.create(map)
	Tag.deleteAll()
	Tag.count()
	Tag.count(String query, Object... params)
	Tag.findAll()
	Tag.findById(Object id)
	Tag.all() 
	Tag.delete(String query, Object... params)
	
	Tag.where(String whereCondition, Object... params)
	Tag.join(String join)
	Tag.order(String order)
	Tag.offset(int offset)
	Tag.limit(int limit)
	Tag.select(String select)
```

一些实例方法

```java
    tag.save()
	tag.valid()
	tag.update()
	tag.refresh()
	tag.delete()
```
	
ServiceFramework还会为你生成很多你看不见的"模型实例方法"。你需要特定语法去调用他。这里使用"m" 方法。
这主要针对关联关系。
对于类似这种申明:

```java
@ManyToMany
private List<Tag> tags = new ArrayList<Tag>();
```
那么你能获得tags方法。

```java
tagGroup.m("tags",Tag.create(map("name","jack")));
```
这段代码的含义是，调用tags方法，该方法接受tag实例作为参数。实际上tags方法等价于下面的方法(只是你看不到这个方法，但是能通过"m”调用他)

```java
  public TagGroup tags(Tag tag){
       this.tags.add(tag);
       tag.getTag_groups().add(this);
       return this;
  }
```
配置了关联关系的字段都会自动生成一个同名的方法，通过调用他们，会自动将对象之间的关联关系设置好，从而可以直接使用包括级联保存等ORM特性。


关于查询，我们强烈建议你使用这一套优美的Query Interface。

但是复杂的查询依然是有的。这属于%20的不常见需求。

但是ServiceFramework 依然提供了原生sql的支持。

这个时候你需要MysqlClient对象。我们提供两种方式引用该类。

1 通过声明注入的方式(IOC)。适用在Controller层或者Service层使用。
2 直接在Model层可以通过nativeSqlClient()方法获取MysqlClient对象

MysqlClient 提供的常用接口:

```java

//查询
public List<Map> query(String sql, Object... params) ；
public Map single_query(String sql, Object... params) ；

//批量插入或者更新
public void executeBatch(String sql, BatchSqlCallback callback) 
```

值得注意的是，任何一个Model类都提供了 

```java
List<Map> findBySql(String sql,Object...params)
```
方法。方便你直接使用Sql查询。


###Validator(模型校验器)

ServiceFramework提供了声明式的validator

```java
@Validate
    private final static Map $name = map(
         presence, map("message", "{}字段不能为空"),
         uniqueness, map("message", "{}字段不能重复")
    );

```

验证器:

* presence  值不能为null或者空
* uniqueness 值具有唯一性
* numericality 是数字，且可以设置范围
* format  正则
* associated  关联对象验证
* length 长度校验

你会发现valiator具有以下几个特点:

1. 想成validator的必要条件是，声明为 private final static 添加 @Validate 注解，并且字段名以$开始 。通常，@Validate注解只是让你知道，这个字段是验证器。否则你可能会对这种以$开头的字段感到疑惑。
2. validator 是一个Map类型的字段
3. $name 中的name 为需要验证的字段名。这里 我们要求Tag中的name不能为空，并且需要具有唯一性。

你可以显式调用一个模型的valid()方法。你也可以直接调用save()方法。该方法返回boolean.false代表没有通过验证。
验证结果你可以通过直接使用模型的validateResults属性获取。

```java
 if(!tag.save()){
   render(HTTP_400,tag.validateResults);
 }
 
 //或者
 
 if(tag.valid()){
   tag.save();
 }
```

对于save方法，你也可以跳过验证

```java
tag.save(false)
```
参数 false 表示不需要验证就进行保存。

1.1 prensence


```java
@Validate
private final static Map $name = map(presence, map("message", "{}字段不能为空"));

```

1.2 uniqueness

```java
@Validate
private final static Map $name = map(uniqueness, map("message", ""));
```

1.3 numericality

```java
@Validate
private final static Map $id = map(numericality, map("greater_than",10,"message":""));
```
拥有的选项为:

* greater_than
* greater_than_or_equal_to
* equal_to
* less_than
* less_than_or_equal_to
* odd
* even

1.4 length

```java
@Validate
private final static Map $name = map(length, map("minimum",10));
```
拥有的选项:

* minimum
* maximum
* too_short
* too_long

###回调

ServiceFramework中，你可以使用标准的JPA回调注解。但是我们依然希望你使用我们替你增强过的回调

* @BeforeSave
* @AfterSave
* @BeforeUpdate
* @AfterUpdate
* @BeforeDestory
* @AfterLoad

```java

public class Tag extends Model {
    @AfterUpdate
    public void afterUpdate() {
        findService(RedisClient.class).expire(this.id().toString());
    }
```
需要注意的是，接受注解的方法必须没有参数。
ServiceFramework 任何一个模型类都能通过findService 方法获得有用的Service,Util服务。例子中
当更新一个对象的时候，我们就让redis缓存中的对象过期。

在回调中你依然可调用模型类进行持久化操作。但是需要注意的是 

1. 不能对本身进行相关的持久化，更新操作。但是可以进行查询动作。
2. 回调函数被包装在一个事务中，执行完后会被立即提交


```java

public class Tag extends Model {
    @AfterUpdate
    public void afterUpdate() {
        BlogTag.create(map("object_id",10)).save();
    }
```

其实，从上面的介绍可以看出，ServiceFramework的Model层是真正富领域模型。关于数据库大部分逻辑操作都应该定义在model层。
当然，Service层依然是需要的。DAO层则被完全摒弃了。通常我们建议，对model调用
可以直接在controller中。而Service则提供其他服务，譬如远程调用，复杂的逻辑判断。当然，
我们完全赞同在Service里调用model。这样对事物也具有较好的控制。

### 模型类的单元测试

一个简答的示例如下:

```java
public class TagTest extends IocTest {


   @Test
    public void associationJPQLTest() {
        setUpTagAndTagSynonymData();

        TagSynonym tagSynonym = TagSynonym.where("name=:name", map("name", "java")).single_fetch();
        List<Tag> tags = tagSynonym.associate("tags").where("name=:name", map("name", "tag_1")).limit(1).fetch();
        assertTrue(tags.size() == 1);

        tearDownTagAndTagSynonymData();
    }
   
}
```

你可以继承IocTest以获取必要的测试框架支持。当然，如果你希望测试是clean的也可以不继承它。

ServiceFramework  强烈建议：

***测试要么全部运行，要不都不运行***

这意味着目前你没法只运行一个测试单元。你必须运行所有测试集。
为此，ServiceFramework在test目录下提供了DynamicSuiteRunner 类。
你可以直接使用支持JUnit的IDE或者通过脚本运行该类即可。该类会自动运行所有配置文件指定package下的所有的测试类。
  
这种方式带来的一个额外好处是，他可以保证你做的任何修改不至于让别人的或者自己写的其他测试奔溃。如果你有机会只运行自己的测试，估计没有多少人会主动去运行所有的测试。在那种情况下，测试就没有意义了。

## Controller

下面是一个典型的ServiceFramework Controller.

```java

public class TagController extends ApplicationController {

    @BeforeFilter
    private final static Map $checkParam = map(only, list("save", "search"));
    @BeforeFilter
    private final static Map $findTag = map(only, list("addTagToTagGroup", "deleteTagToTagGroup","createBlogTag"));


    @AroundFilter
    private final static Map $print_action_execute_time2 = map();

   
    @At(path = "/tag_group/create", types = POST)
    public void createTagGroup() {
        TagGroup tagGroup = TagGroup.create(params());
        if (!tagGroup.save()) {
            render(HTTP_400, tagGroup.validateResults);
        }
        render(OK);
    }


    @At(path = "/tag_group/tag", types = {PUT, POST})
    public void addTagToTagGroup() {
        TagGroup tagGroup = TagGroup.findById(paramAsInt("id"));
        tagGroup.associate("tags").add(tag);
        render(OK);
    }

    @At(path = "/tag_group/tag", types = {DELETE})
    public void deleteTagToTagGroup() {
        TagGroup tagGroup = TagGroup.findById(paramAsInt("id"));
        tagGroup.associate("tags").remove(tag);
        tagGroup.save();
        render(OK);
    }


    @At(path = "/{tag}/blog_tags", types = PUT)
    public void createBlogTag() {
        tag.associate("blog_tags").add(BlogTag.create(map("object_id", paramAsInt("object_id"))));
        render(OK);
    }


   
    @At(path = "/doc/{type}/insert", types = POST)
    public void save() {

        for (String tagStr : tags) {
            Model model = (Model) invoke_model(param("type"), "create", selectMapWithAliasName(paramAsJSON("jsonData"), "id", "object_id", "created_at", "created_at"));
            model.m("tag", Tag.create(map("name", tagStr)));
            if (!model.save()) {
                render(HTTP_400, model.validateResults);
            }
        }
        render(OK);
    }

    @Inject
    private RemoteDataService remoteDataService;

    
    @At(path = "/doc/{type}/search", types = GET)
    public void search() {

        Set<String> newTags = Tag.synonym(param("tags"));


        JPQL query = (JPQL) invoke_model(param("type"), "where", "tag.name in (" + join(newTags, ",", "'") + ")");

        if (!isEmpty(param("channelIds"))) {
            String channelIds = join(param("channelIds").split(","), ",", "'");
            query.where("channel_id in (" + channelIds + ")");
        }

        if (!isEmpty(param("blockedTagsNames"))) {
            String blockedTagsNames = join(param("blockedTagsNames").split(","), ",", "'");
            String abc = "select object_id from " + param("type") + " where  tag.name in (" + blockedTagsNames + ")";
            query.where("object_id not in (" + abc + ")");
        }

        long count = query.count_fetch("count(distinct object_id ) as count");

        if (!isEmpty("orderFields")) {
            query.order(order());
        }

        List<Model> models = query.offset(paramAsInt("start", 0)).limit(paramAsInt("size", 15)).fetch();

        // JSONArray data = remoteDataService.findByIds(param("type"), param("fields"), fetchObjectIds(models));

        render(map("total", count, "data", map()));
    }


    private String[] tags;

    private void checkParam() {
        tags = param("tags", " ").split(",");
        if (tags.length == 0) {
            render(HTTP_400, format(FAIL, "必须传递标签"));
        }
    }

    private Tag tag;

    private void findTag() {
        tag = Tag.where("name=:name", map("name", param("tag"))).single_fetch();
        if (tag == null) {
            render(HTTP_400, format(FAIL, "必须传递tag参数"));
        }
    }

    private String fetchObjectIds(List<Model> models) {
        List<Integer> ids = new ArrayList<Integer>(models.size());
        for (Model model : models) {
            ids.add(model.attr("object_id", Integer.class));
        }
        return join(ids, ",");
    }

    private String order() {
        String[] orderFields = param("orderFields").split(",");
        String[] orderFieldsDescAsc = param("orderFieldsDescAsc", "").split(",");
        List<String> temp = new ArrayList<String>();
        int i = 0;
        for (String str : orderFields) {
            if (orderFieldsDescAsc.length < i) {
                temp.add(str + " " + orderFieldsDescAsc[i]);
            } else {
                temp.add(str + " " + "desc");
            }

        }
        return join(temp, ",");
    }

    private Object invoke_model(String type, String method, Object... params) {
        return ReflectHelper.method(const_model_get(type), method, params);
    }
    
     private void print_action_execute_time2(RestController.WowAroundFilter wowAroundFilter) {
        long time1 = System.currentTimeMillis();

        wowAroundFilter.invoke();
        logger.info("execute time2:[" + (System.currentTimeMillis() - time1) + "]");

    }


}
```
这个类有点长，主要是为了较为全面的展示Controller的使用，希望不要引起你的不适。
我们再来分析ServiceFramework的controller有什么特点。

1. 成为Controller的必要条件是继承 ApplicationController
2. 类似Model验证器，你可以以相似的方式添加过滤器
3. 通过At配置路径以及接受的Http 请求方式
4. 所有其他的Service或者Util推荐采用使用IOC容器管理。譬如例子里的RemoteDataService
5. filter只是一个简单的私有方法。如果申明在ApplicationController。那么对所有controller有效


###过滤器

ServiceFramework 目前支持两种过滤器

1. BeforeFilter 前置过滤器
2. AroundFilter 环绕过滤器

如同示例，过滤的器声明非常简单

* private,final static 三个修饰符
* 过滤器 @BeforeFilter 或者 @AroundFilter 注解声明



```java
  @BeforeFilter
    private final static Map $checkParam = map(only, list("save", "search"));
```

filter是声明在一个map属性上的。map 接受两个属性，only,except。如果没有这两个属性，那么表示过滤当前Controller中所有Action。
属性依然以$开头，后面的属性名其实是一个方法的名称。比如你会发现在上面的controller中确实包含一个checkParam 方法。

例子的含义是，只有save,search两个Action方法在调用前会先调用checkParam。

Controller是多线程安全的。这意味着，你可以安全的使用实例变量。示例中"addTagToTagGroup", "deleteTagFromoTagGroup","createBlogTag" 三个Action在调用前都需要事先获得tag对象。你可以使用findTag过滤器先填充 tag实例变量。如果用户没有传递tag名，就可以在过滤器中直接告诉用户参数问题。

需要注意的一点是，BeforeFilter 比 AroundFilter 运行的更早。Filter 也可以调用render 方法，进行结果输出。

###路径配置

路径配置使用的也是注解配置。

```java
@At(path = "/tag_group/tag", types = {PUT, POST})
```

@At注解接受两个参数，path 和 types

path 代表请求路径。 types则是表示接受的请求方法的,默认是GET.

path 支持占位符，比如:

```java
@At(path = "/{tag}/blog_tags", types = PUT)
```
tag这个值会被自动填充到请求对象中。你可以通过 param("tag")获取。


#### request 参数获取

在ServiceFramework 中 提供了一个非常便利的获取request参数的方式。不管是form表单,get请求，还是url中的数据，都可以统一通过param() 方法获取。

```java
int id = paramAsInt("id");
//或者
String id = param("id");
```

比如这就可以获取 id 参数，并且将其转换为int类型。
如果你确认传递过来的是json或者xml格式，你可以调用下面的方式

```java
JSON obj = paramAsJSON();
//或者
JSON obj = paramsAsXML();
```
其中,xml文本的数据会自动转化json格式,便与操作。

ServiceFramework 尽量让事情简单而方便。

方法列表:

```java
params()
param(key)
param(key,defaultValue)
paramAsInt(key)
paramAsLong(key)
paramAsFloat(key)
//还有更多….
```


#### 渲染输出

所有渲染输出统一使用render 方法。

普通文本输出

```java
render("hello word");
```

如果传入的是对象，会自动呗转化为json格式

```java
render(tag);
```

你可以手动指定输出格式

```java
render(tag,ViewType.xml);
```

你还可以指定输出的http状态码

```java
render(HTTP_200,tag,ViewType.xml);
```

render 方法也可以在过滤器中使用。一旦调用render方法后，就会自动跳过action调用。

```java
@At(path = "/tag_group/create", types = POST)
    public void createTagGroup() {
        TagGroup tagGroup = TagGroup.create(params());
        if (!tagGroup.save()) {
            render(HTTP_400, tagGroup.validateResults);
        }
        render(OK);
    }
```

在上面的示例代码中，你无需render之后再调用return 语句。

###Json格式输出控制
对于json输出的控制是非常有必要，因为某些字段你可能不想展示给用户，不同权限的人可以看到不同的字段，等等，
你还可能希望某些情况下格式化json，便于阅读。在Controller层，这些很容易实现。

```java
 //设置json输出,排除字段blog_tags
 config.setExcludes(new String[]{"blog_tags"});
 //格式化输出json
 config.setPretty(true);
```

config对象来自 父类。本质上就是json-lib 中的JsonConfig。对json控制非常的完善。能够满足大部分输出要求。



###ServiceFramework

```java
@Inject
private RemoteDataService remoteDataService;
```

之后你就可以在Action中直接使用remoteDataService了。

#### Controller提供的便利的方法集

在controller中，你天然会获取大量有用的工具方法。比如 isEmpty，字符串join。比如

```java
JPQL query = (JPQL) invoke_model(param("type"), "where", "tag.name in (" + join(newTags, ",", "'") + ")");
```

## 配置文件

ServiceFramework 所有的配置文件位于config目录下。其实只有两个配置文件，一个application.yml,
一个logging.yml.分别配置应用和日志。

一个完整的application.yml

```yaml
#mode
mode:
  development
#mode=production

###############datasource config##################
#mysql,mongodb,redis等数据源配置方式
development:
    datasources:
        mysql:
           host: 127.0.0.1
           port: 3306
           database: tag_engine
           username: tag
           password: tag
        mongodb:
           host: 127.0.0.1
           port: 27017
           database: tag_engine
        redis:
            host: 127.0.0.1
            port: 6379

production:
    datasources:
        mysql:
           host: 127.0.0.1
           port: 3306
           database: tag_engine
           username: tag
           password: tag
        mongodb:
           host: 127.0.0.1
           port: 27017
           database: tag_engine
        redis:
            host: 127.0.0.1
            port: 6379

orm:
    show_sql: true
    pool_min_size: 5
    pool_max_size: 10
    timeout: 300
    max_statements: 50
    idle_test_period: 3000
###############application config##################
application:
    controller: com.example.controller
    model:      com.example.model
    service:    com.example.service
    util:       com.example.util
    test:       test.com.example


###############http config##################
http:
    port: 9400



###############validator config##################
#如果需要添加验证器，只要配置好类全名即可
#替换验证器实现，则替换相应的类名即可
#warning: 自定义验证器实现需要线程安全

validator:
   format:        net.csdn.validate.impl.Format
   numericality:  net.csdn.validate.impl.Numericality
   presence:      net.csdn.validate.impl.Presence
   uniqueness:    net.csdn.validate.impl.Uniqueness
   length:        net.csdn.validate.impl.Length
   associated:    net.csdn.validate.impl.Associated

################ 数据库类型映射 ####################
type_mapping:  net.csdn.jpa.type.impl.MysqlType

```

对于数据库等的配置是区分开发或者生产环境的

## 单元测试
单元测试非常重要。这里我们会重点阐述如何进行Controller层的测试。

```java
    @Test
    public void testSave() throws Exception {
    
        //获取你需呀测试的controller。injector就是google guice 的injector ^_^  
        TagController tagController = injector.getInstance(TagController.class);
        
        //设置请求参数
        tagController.mockRequest(
        map(
                "object_id", "17",
                "tags", "java,google"

        ),//这些参数会填充进request中，之后可以通过param方法获取。 
        RestRequest.Method.PUT, //请求方法
        null//post数据，通常当你要提交json或者xml数据时，填充该值
        );

        //调用你需要的过滤器  m 方法其实就是通过反射调用拦截器
        tagController.m("check_params");


        try {
            //调用真实的action
            tagController.save();
        } catch (RenderFinish e) {
           //这是测试唯一比较麻烦的地方。因为render方法会通过抛出RenderFinish异常来结束流程，所以这里你需要手动捕获
           //下RenderFinish。
        }

        //获取render后的response对象
        RestResponse restResponse = tagController.mockResponse();
        
        //拿到你传递给render的对象，这个时候你可以查看是否是否你想要的结果
        JSONObject renderResult = JSONObject.fromObject((String) restResponse.originContent());
        assertTrue(renderResult.getBoolean("ok"));

        //手动提交数据操作
        dbCommit();
        List<BlogTag> blogTags = BlogTag.where("object_id=17").fetch();
        assertTrue(blogTags.size() == 2);

        //清理数据
        Tag.delete(format("name in ({})", "'java','google'"));
        BlogTag.delete("object_id=17");

    }
```