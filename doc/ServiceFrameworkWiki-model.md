
<link rel="stylesheet" href="http://yandex.st/highlightjs/6.2/styles/googlecode.min.css">

<script src="http://code.jquery.com/jquery-1.7.2.min.js"></script>
<script src="http://yandex.st/highlightjs/6.2/highlight.min.js"></script>

<script>hljs.initHighlightingOnLoad();</script>


<script type="text/javascript">
 $(document).ready(function(){
      $("h2,h3,h4,h5,h6").each(function(i,item){
          $(item).attr("id","wow"+i);
          $("#category").append("<li><a href=\"#wow"+i+"\">"+$(this).text()+"</a></li>");
      });     
 });
</script> 



<style>
pre code {
  break-word: break-all;
  word-wrap: break-word;
}
</style>

#ServiceFramework Wiki

## Model 

这个章节，我们会知道 ServiceFramework 模型层 完整的使用。

首先，建立四张示例表:

```
--标签表
CREATE TABLE `Tag` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) DEFAULT NULL,
  `tag_synonym_id` int(11) DEFAULT NULL,
  `weight` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8;

--标签组。一个标签可以属于多个标签组。一个标签组包含多个标签
CREATE TABLE `TagGroup` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(32) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `id` (`id`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8;

--博客和标签的关联表。存有 博客id和标签id
CREATE TABLE `BlogTag` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `tag_id` int(11) DEFAULT NULL,
  `object_id` int(11) DEFAULT NULL,
  `created_at` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--标签近义词组。一个标签只可能属于一个标签近义词
CREATE TABLE `TagSynonym` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(32) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
```


对应的类文件:

```
/**
 * User: WilliamZhu
 * Date: 12-7-23
 * Time: 下午4:52
 */
@Entity
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


@Entity
public class BlogTag extends Model {

    @ManyToOne
    private Tag tag;
}

@Entity
public class TagGroup extends Model {
    @ManyToMany
    private List<Tag> tags = list();
}

@Entity
public class TagSynonym extends Model {
    @OneToMany
    private List<Tag> tags = list();
}


```

初看模型，你可能会惊讶于代码之少，关联配置之简单。是的，上面就是我们对模型类所有的配置了。甚至，连属性都没有，但是就是这些代码实现了非常多的事情。哈哈，那让我们
一步一步来看ServiceFrame是如何为你带来这些魔法的。

模型关系介绍:

1. TagGroup 和 Tag是多对多关系
2. Tag和BlogTag是一对多关系。
3. TagSynonym 和Tag 是多对一关系

建立模型类只需要三步:

1. 继承 Model 基类
2. 添加 Entity 注解
3. 声明集合属性时需要初始化它

ServiceFramework 为你提供了大量便利方法。比如建立map/list

```
Map newMap = map();
Map newMap2 = map("key1","value1","key2","value2")
List newList = list();
List newList2 = list("value1","value2","value3");
```
所以集合初始化的时候也变得很简洁，比如示例代码中

```
@ManyToMany
private List<Tag> tags = list();
```

### 表和模型之间的映射关系
前面的例子可以看到，我们不需要进行任何表和模型之间的映射配置。当然，你也可以使用标准的JPA进行配置。
但是我们强烈建议你使用默认的命名约定。这些规则包括：

1. 表名和类名相同
2. 外键名称 = 属性名+"_id"
3. 属性名 = 小写 加 下划线的形式。比如示例中的 tag_groups 等。 这和传统java会有些区别。这主要是为了数据库字段和Model属性名保持一致。如果你使用"tagGroups"这种传统的驼峰命名方式，,那么数据库中的字段名就会很丑陋了。遵循现在的方式你会发现这是相当便利的一种方式。
4. 根据语义区分单复数形式 

***关于主键ID***    
ServiceFramework 强烈推荐使用自增id,名称为id,并且为interge类型。这可以省掉很多麻烦。


### 模型属性

Model类会自动根据数据库获取信息。
比如Tag 含有一个name 属性，可以这样获取它。

```
String name = tag.attr("name",String.class);
```
将其赋值为jack 则为:

```
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


给一个***已经存在的***同义词组添加一个同义词，我们可以这样做:

```
 tagSynonym.associate("tags").add(Tag.create(map("name","i am tag "));
```
这个时候会创建一个tag并且设置好与TagSynonym的关系。相应的

```
tagSynonym.associate("tags").remove(tag);
```

可以将同义词组和标签解除关系。当然，这并不会删除tag在数据库里的记录。而只是吧他们之间的关系抹除了

我们可以看到 “tags“  就是我们定义在TagSynonym 中的一个属性。ServiceFramework默认会为
这种集合映射属性添加一个同名的方法，并且返回Association。

类似于:

```
public Association tags(){throw new AutoGeneration();}
```

associate 只是帮你调用这些看不到的方法。
为了获得IDE提示的好处，
你可以把上面那段代码写进你的模型类中。ServiceFramework会去实现里面具体的细节。

```
@Entity
public class TagSynonym extends Model {
    @OneToMany
    private List<Tag> tags = list();
    public Association tags(){throw new AutoGeneration();}
}
```

现在假设我们要获取一个同义词组所有的d>10的tag，我们可以这么做

```
List<Tag> tags = tagSynonym.tags().where("id>10").fetch(); 
```
当然，你依然可以写成

```
List<Tag> tags = tagSynonym.associate("tags").where("id>10").fetch(); 
```
结果是一样的。对于这种只是为了代码提示而创建的方法，我们推荐方法内部 填充 'throw new AutoGeneration()'来标记它会被框架自动实现。虽然，即使它不存在，系统也会创建它。

经过上面的例子可以看出模型的关联关系可以给我们带来很多便利。这包括从表单获取多个model进行级联保存。

WARNNING: 集合属性的名称都会有一个同名的方法。这个方法名会被框架保留使用。所以，不要用这个名称来定义对你来说有其他用处的方法。

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

```
Tag.findById(10)
//或者
Tag.find(10)
```
1.2 根据多个ID获取

```
Tag.find(list(1,2,,4,5))
```
1.3 条件查询

```
Tag.where("id=:id",map("id",7)).fetch();
```
map 是一个创建Map的一个便利方法。

你也可以使用一个更复杂的例子:

```
Tag.where("tag_synonym=:tag_synonym",map("tag_synonym",tag_synonym));
```
还记得之前提到的，对象关联关系的建立，可以方便框架进行一些对象化的操作。在Tag中tag_synonym是一个对象属性，你可以直接在where中使用该属性。
他会转为为类似:

```
select * from Tag where tag_synonym_id=? 
```
因为对象关联模型告诉了系统那个是外键。这不会带来任何性能方面的损耗。

1.4 order

```
Tag.order("id desc")
```
或者

```
Tag.order("id desc,name asc")
```

1.5 joins

joins 语法也是对象化的，这也得益于我们之前简单的模型关系声明。你所操作的就是相应的模型属性。不管简单属性还是对象属性。

```
Tag.joins("tag_synonym").fetch();
```

那么 tag对象的tag_synonym 属性会自动得到填充。这不会有n+1问题。因为一条SQL语句就搞定了

你也可以join多个属性

```
Tag.joins("tag_synonym left join fetch tag_groups left join blog_tags").fetch();
```
当然，对于互联网应用，这么多join毫无疑问会拖垮你的数据库。我们只是举个例子，你不应该这么做。

1.6 offset,limit

```
Tag.offset(10).limit(15);
//这相当于
select * from Tag limit 10,15;
```

1.7 select 

这通常用于你不想获取所有的字段的场合

```
 List<Object[]> results =Tag.select("name").fetch();
```

这通常返回是一个数组。当然，如果你想让它填充进一个模型也是可以的。

```
 List<Tag> results =Tag.select("new Tag(name)").fetch();
```
需要注意的是，你需要在Tag填充一个相应的构造方法。希望不久就能去掉这个限制。嗯，应该尽力去掉。

1.8 group
说实话，真不应该提供这个，性能杀手。不过还是提供了….

```
Tag.where("id>10").group("name").fetch();
```

###Name_Scope
假设tag需要审核。只有审核通过的才应该被查询出来。如果每次查询的时候都要加这个条件岂不是
太麻烦？我们可以定义一个方法：

```
@Entity
public class Tag extends Model {
    public static JPQL active(){
      return where("status=1");
    }
}
```

之后你就可以这么用了

```
Tag.active().where("id>10").join("tag_groups").offset(0).limit(15).fetch();
```


### 模型方法

在ServiceFramework中。一旦你定义了模型类，那么该模型类会自动拥有众多的方法。一些静态方法:

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
	
一些实例方法

    tag.save()
	tag.valid()
	tag.update()
	tag.refresh()
	tag.delete()
	
ServiceFramework还会为你生成很多你看不见的"模型实例方法"。你需要特定语法去调用他。这里使用"m" 方法。
这主要针对关联关系。
对于类似这种申明:

```
@ManyToMany
private List<Tag> tags = new ArrayList<Tag>();
```
那么你能获得tags方法。

```
tagGroup.m("tags",Tag.create(map("name","jack")));
```
这段代码的含义是，调用tags方法，该方法接受tag实例作为参数。实际上tags方法等价于下面的方法(只是你看不到这个方法，但是能通过"m”调用他)

```
  public TagGroup tags(Tag tag){
       this.tags.add(tag);
       tag.getTag_groups().add(this);
       return this;
  }
```
配置了关联关系的字段都会自动生成一个同名的方法，通过调用他们，会自动将对象之间的关联关系设置好，从而可以直接使用包括级联保存等ORM特性。



###Validator

ServiceFramework提供了声明式的validator语法。

```
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

```
 if(!tag.save()){
   render(HTTP_400,tag.validateResults);
 }
 
 //或者
 
 if(tag.valid()){
   tag.save();
 }
```

对于save方法，你也可以跳过验证

```
tag.save(false)
```
参数 false 表示不需要验证就进行保存。

1.1 prensence


```
@Validate
private final static Map $name = map(presence, map("message", "{}字段不能为空"));

```

1.2 uniqueness

```
@Validate
private final static Map $name = map(uniqueness, map("message", ""));
```

1.3 numericality

```
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

```
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

```
@Entity
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


```
@Entity
public class Tag extends Model {
    @AfterUpdate
    public void afterUpdate() {
        BlogTag.create(map("object_id",10)).save();
    }
```

我们期望的是你能定义在模型内。但是如果你想给所有模型方法共用的话，你可以通过类的声明方式。

```
@Entity
@EntityListeners(UpdateCallback.class)
public class Tag extends Model {
```

相应的类为:

```
class UpdateCallback{
   @AfterUpdate
    public void afterUpdate() {
        findService(RedisClient.class).expire(this.id().toString());
    }
}
```


其实，从上面的介绍可以看出，ServiceFramework的Model层是真正富领域模型。关于数据库大部分逻辑操作都应该定义在model层。
当然，Service层依然是需要的。DAO层则被完全摒弃了。通常我们建议，对model调用
可以直接在controller中。而Service则提供其他服务，譬如远程调用，复杂的逻辑判断。当然，
我们完全赞同在Service里调用model。这样对事物也具有较好的控制。

### 模型类的单元测试

一个简答的示例如下:

```
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


	
	


