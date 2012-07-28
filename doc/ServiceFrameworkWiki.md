
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

首先，建立三张示例表:

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
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8;

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
    private List<BlogTag> blog_tags = new ArrayList<BlogTag>();

    @ManyToMany
    private List<TagGroup> tag_groups = new ArrayList<TagGroup>();
}


@Entity
public class BlogTag extends Model {

    @ManyToOne
    private Tag tag;
}

@Entity
public class TagGroup extends Model {
    @ManyToMany
    private List<Tag> tags = new ArrayList<Tag>();
}

@Entity
public class TagSynonym extends Model {
    @OneToMany
    private List<Tag> tags = new ArrayList<Tag>();
}


```

初看模型，你可能会惊讶于代码至少，关联配置只简单。甚至，连属性都没有。别介，让我们
一步一步来看ServiceFrame为你带来的魔法。

模型关系介绍:

1. TagGroup 和 Tag是多对多关系
2. Tag和BlogTag是一对多关系。
3. Tag 和 TagSynonym 多对一关系


你会发现ServiceFramework的模型具有以下几个特点:

1.  不需要定义属性，所有的属性在运行时会自动生成。你可以通过"attr""方法获得或者设置属性值。当然，你也可以手动定义属性,就如同传统的模型类一样。
2.  模型关联需要手动定义。但是非常的简化，只需简单添加一个标准的JPA注解。你无需配置mappedBy,cascade等属性。当然，如果你需要，你也可以手动添加。
3.  模型关联设置中如果是集合对象您需要手动初始化。
4.  成为一个模型类的必要条件是 继承 Model 基类，添加@Entity 注解

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


### 关联关系

关联关系可以做两件事情:

1. 告诉框架表之间的外键关系
2. 可以方便的级联保存，更新操作

***WARNING***: 关联关系不应该用来查询。比如 你不应该通过 tag.getBlogTags()获取相关的BlogTag.即使是blogTag.getTag() 这样获取一个对象也不行。后面你会看到一个可控性更好，不需要你具有任何ORM只是，规范的查询方式。



### 模型方法

在ServiceFramework中。一旦你定义了模型类，那么该模型类会自动拥有众多的方法。下面所有的示例都会使用我们前面建立的Tag模型
静态方法:

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
	
有代码提示的实例方法:

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


ServiceFramework 使用的是HQL语法。你可以直接使用HQL语言。但是我们强烈建议使用ServiceFramework调用HQL的方式。它会使得你的代码异常的简洁，高效。

假设我们现在要查询所有和名称为java tag相关的额BlogTag对象。通常的HQL会下面这个样子:

```
 Tag.find("from Tag tag left join tag.blog_tags where tag.name=:name",map("name","java"));
``` 

但是我们强烈建议使用下面的方式组织hql语句。

```
  Tag.where("name=:name",map("name","java")).join("blog_tags").fetch();
```

我们看到，第二种语法更像原始的sql语句。并且合理规范了每个开发者的代码格式。


###Validator

ServiceFramework提供了声明式的validator语法。

```
@Validate
    private final static Map $name = map(
    presence, map("message", "{}字段不能为空"),
    uniqueness, map("message", "{}字段不能重复")
    );

```

目前提供了presence,uniqueness,numericality,format,associated等五个验证器。

你会发现valiator具有以下几个特点:

1. 想成validator的必要条件是，声明为 private final 并且添加 @Validate 注解，并且字段名以$开始 
2. validator 是一个Map类型的字段
3. $name 中的name 为需要验证的字段名。这里，我么要求Tag中的name不能为空，并且需要具有唯一性。

你可以显示调用一个模型的valid()方法。你也可以直接调用save()方法。该方法返回boolean.false代表没有通过验证。
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


###NamedScope
这里借用了Rails里的概念。其实就是一个简单的方法调用。
举个例子。假设有些tag是禁用的，有些是公开的。如果每次获取tag列表都需要加过滤条件会比较蛋疼。这个时候你可以在模型类顶一个一个静态方法

```
@Entity
public class Tag extends Model {
   public static JPQL active_tags(){
     return where("status=:status",map("status",1));
   }
}

```
之后你就可以在任何一处使用。

```
List<Tag> tags = Tag.active_tags().where("name in('google','java')").fetch();
```


### 模型类的单元测试

一个简答的示例如下:

```
public class TagTest extends IocTest {


    @Test
    public void testTag() {
        
        //清理数据表
        BlogTag.deleteAll();
        Tag.deleteAll();
        TagSynonym.deleteAll();
        TagGroup.deleteAll();

         //创建tag和关联表
        BlogTag blogTag = BlogTag.create(map("object_id", 17, "created_at", 2007022711l));
        blogTag.m("tag", Tag.create(map("name", "java")));
        blogTag.save();//此时会级联保存tag对象

        blogTag = BlogTag.create(map("object_id", 17, "created_at", 2007022711l));
        blogTag.m("tag", Tag.create(map("name", "google")));
        blogTag.save();

        //添加一个同义词组
        TagSynonym tagSynonym = TagSynonym.create(map("name", "java"));

        List<Tag> tags = Tag.where("name in ('java','google')").fetch();
        //把取出来的tag和tagSynonym进行关联
        for (Tag tag : tags) {
            tagSynonym.m("tags", tag);
        }
        tagSynonym.save();

        //添加一个组
        TagGroup tagGroup = TagGroup.create(map("name", "天才组2"));
        for (Tag tag : tags) {
            tagGroup.m("tags", tag);
        }
        tagGroup.save();

    }
   
}
```

你需要继承IocTest以获取必要的测试框架支持。
测试类简单展示了模型类的使用。


	
	


