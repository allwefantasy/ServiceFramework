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

#ServiceFramework


09年的时候做了一个类似于"原创音乐基地"的音乐类网站。用的是SSH2开发的。当时没用好，代码臃肿，速度缓慢。这给我印象非常深刻。2010年接触了Rails，见识了开发速度,和印象中的SSH2形成了鲜明的对比，后来一直从事搜索方面的开发，对Web类应用便接触的少了。
12年06月因为内部一个项目，让我重新开始对Java Web框架进行了思考。

***一个框架自己应该做到：***

* 约定大约配置
	* 约定大于配置，这个已经是广大coder的常识了。但是有多少人真正去思考其精髓呢？真正的含义应该是，
‘用***最佳实践***来指导约定，用约定来规范项目和代码，完全去除***不必要***的配置’。当你尝试跳过
某种约定去适应某种需求，就说明你已经开始误入歧途。因为，约定是"最佳实践"，当你企图跳过，你已经在增加复杂度了，你应该三思了。

* 限定自己的使用范围
	* 每个框架都应该有自己的使用范围。就如同每个领域都有自己的专业工具。没有一个框架或者软件是所有领域的王者。
	* ServiceFramework 定位在移动互联网后端开发。作为APP后端开发的基石。

* 在理念和实用中找到平衡点
	* 解耦，扩展，设计模式的应用等等，但是过度，过早去思考，设计这些都是不合理的。应该对这些“理念”和“实用”做好平衡。这样带来的一个额外好处是框架自己的代码会比较简单。而代码简单意味着以后较小的维护成本。


***一个框架应该帮程序员做到：***

* 开箱即用
	* 好的框架应该是下载即可使用。Rails门槛已经越来越高，初学者甚至已经很难启动它的一个demo了。
	* SSH2 更是离奇，没有拷贝黏贴，新手或许怎么着得半天到一天才能把框架搭起来吧

* 规范项目结构和代码风格
	* 框架除了方便开发，提供必要的基础设施，我觉得最重要的是限制项目结构，规范代码风格。这点很重要。框架应该引导用户怎么做才是最合理的，而不是什么都需要用户去揣测，去定夺。框架应该提出应该怎么做，不应该怎么做。

* 尽量减少代码
    * ServiceFramework 能够让你豁然开朗，只要按照最佳实践，即使是Java这种语言，代码也是可以非常少的。少的代码意味着开发的高效，维护的便利。毕竟，很多Java框架都是很多年前的东西了，有很多过时的理念，这些都是历史负担。


首先 ServiceFramework 是一个一站式的框架。你下载下来就能用，只需要配置数据库连接，就能跑起来。你不需要掌握任何命令或者操作流程。

ServiceFramework 详细规划了应用程序的目录。保证了你不会为如何组织项目而烦恼。你看到框架的时候就可以开始写你的代码了。根本不需要考虑如何规划Service,Model,Controller等。

ServiceFramework 有一个强大的真正的富领域模型，便利的，规范化Query Interface，优秀的声明式模型校验语法。线程安全的控制器，真正便利的前置过滤器和环绕过滤器的实现,一个目前还比较简单但实用的***函数库***。在ServiceFramework上面写代码，你会发现,代码如此之少，实现如此只简洁。




##对Model层封装的思考

传统的Java ORM框架真的是复杂繁琐。以Hibernate为例

1. 不熟悉的人大部分特性不敢用，只是用来操作简单的POJO。其他特性不敢碰
2. 也有一开始就上去啥特性都用，结果各种性能陷阱，死的很难看
3. 你当然想成为熟悉Hibernate的人咯，于是你可能要去看一本书，而不是一个简单的tutorial.
4. 即使你不惧怕Hibernate各种性能陷阱，你依然呗Hibernate 各种繁琐代码所困扰。Session的打开关闭，这和原始社会的时候要打开数据库和关闭数据库有区别吗？
5. 所以，你又会对Hibernate进行一次封装，或者采用Spring的胶水。但是，依然云里雾里。
6. 因为ORM的问题，导致 Java 衍生出了DAO层('贫血模型')。大部分情况下，Service层只是DAO层的简单调用。这增加了大量代码和层次，无形增加了项目的复杂度。而且很难把握好DAO和Service层次的封装调用。除非你是个老手，否则写的代码可能就会比较凌乱。



就Hibernate实现而言，也是问题多多。

1. hql 语法
	* 和sql相似，又和sql具有不同点，让人困惑。
	* 添加的大量面向对象语法，比如"from Blog where user.info.range>200" 这种调用往往有性能陷阱。
	* hql避免不了sql最本质的问题。你可能需要拼接sql语句，不同的程序员以不同的方式将sql片段散落到各个角落。同时以不同的方式给sql传参数。而hibernate对该问题的解决是提出新的一套API: Criteria.这绝对是件愚蠢的方式。即增加学习成本，又给代码造成混乱，不同的程序员偏好不一样，有的喜好hql,有的喜好criteria.而且程序员还要去思考criteria和hql分别应该试用什么场景。需要根据条件拼凑hql的话使用Criteria？其他场景都使用hql?
	
2. 调用语法，每次都需要打开session,然后使用完还需要关闭session.callback里面不能直观的使用数据库操作(解决方案使用了拙略的open temp session的方式)

3. hibernate的校验框架也是没有任何设计可言。就是简单的叠加Annotation到Model层的属性上。加上一些关联属性和column属性，这个时候，属性就如同就好像一个戴着19世纪英国绅士带的那种高帽，当然，现在的魔术师也常带。




ServiceFramework提供了一套完整的解决方案：

1. 简化hql语法，使得hql更加像sql.譬如hql通常需要别名，为什么我可以用id=1非要写成blog.id=1呢？
2. 提供一个统一的查询接口，使得sql语句规范化，同时具有原生hql和Criteria的有点，而且基本不需要学习成本。

总而言之，ServiceFramework 有一个强大的真正的富领域模型，便利的，规范化Query Interface，优秀的声明式模型校验语法。

整个设计过程基本原则是：

1. 尽量提供一个'最佳实践'给用户，而不是让用户做大量的选择题
2. 用一个方案覆盖80%的问题(简化后的hql)
3. 用另一个可选方案覆盖另外20%的问题(就是使用原生的Sql)


常规的例子是:

```
 //前面还需要获取session 
 Query query = session.createQuery("from Stock where stockCode = :code ");
 query.setParameter("code", "7277");
 List list = query.list();
 //别忘了关闭session
```
你会发现这和我们传统的sql查询没有区别。并且如果是插入更新删除，你还需要显示加入事物代码。当然，如果你加入
spring等则可通过配置的方式避免显式加入事务代码。

你可以做的更漂亮些

举个例子:  

```
Stock.find(
    "from Stock where stockCode = :code", "7277"
);
```

其实from Stock 是没有必要的。所以可以简化

```
Stock.find(
    "where stockCode = :code", "7277"
);
```

但是这并不是最好的方式

最佳的方式是

```
Stock.where("stockCode=:code",map("code","7277")).fetch();
```

那他是如何替代Criteria呢？假设有个status属性是需要根据用户提交来确定是否添加的，那么代码如下


```
JPQL query = Stock.where("stockCode=:code",map("code","7277"));

if(status!=null){
  query.where("status=:status",map("status",status));
}

if(limit!=0){
  query.limit(limit);
}

List<Stock> result = query.fetch(); 
```

我们可以看出如下几个优势：

1. 天然按照sql 语句的不同结构进行拆分，比如where,join,limit,query等
2. 可以非常方便的进行hql拼接。你可以多次调用where.默认关系为 'and'
3. 强制使用命名参数
4. 通过语法使得查询变得异常简洁。假如我想找到stockCode为“7277”。那么就是天然的

```
Stock.where("stockCode=:code",map("code","7277"));
```
而不是每次重复类似下面的代码

```
 Query query = session.createQuery("from Stock where stockCode = :code ");
 query.setParameter("code", "7277");
```


Java ORM框架的关联关系配置也是非常的不友好。
一个较为完整的ManyToMany配置

```

@Entity
@Table(name="EMPLOYEE")
public class Employee {
   @ManyToMany(cascade = {CascadeType.ALL})
   @JoinTable(name="EMPLOYEE_MEETING", 
    joinColumns={@JoinColumn(name="EMPLOYEE_ID")}, 
    inverseJoinColumns={@JoinColumn(name="MEETING_ID")})
  private Set<Meeting> meetings = new HashSet<Meeting>();
   } 
// Meeting Entity    
     
@Entity
@Table(name="MEETING")
public class Meeting {
  @ManyToMany(mappedBy="meetings")
   private Set<Employee> employees = new HashSet<Employee>();
}
```

非常的复杂，你要配置主控端(通过mappedBy),你还要配置关联表，在ServiceFramework中，这一切都是不必要的。


```
@Entity
public class Tag extends Model {    
    @ManyToMany
    private List<TagGroup> tag_groups = list();
}

@Entity
public class TagGroup extends Model {
    @ManyToMany
    private List<Tag> tags = list();
}
```

第一，ServiceFramework 会根据类名组合 TagGroup_Tag 或者 Tag_TagGroup 来找到中间表。

第二，关联表的外键属性，ServiceFramework 会尝试拿属性名+"_id"的方式。所以在中间表中，分别是 tags_id 和 tag_groups_id 两个字段。

通过上面的方式就将JoinTable等注解去掉了。

第三 ServiceFramework 中关联关系天然是双向的。ServiceFramework会随机挑选一个类配置mappedBy作为被控端。同理ManyToOne(或者OneToMany)则主控端为多的一方。这些事情ServiceFramework都会为你做掉。

传统的解除多对多双向关系，你必须这样：

```
tag_group.getTags().remove(tag);
tag.getTag_groups().remove(tag_group);
```

而ServiceFramework则极大的简化了这类关系的操作。

```
tag_group.associate("tags").remove(tag);
//或者
tag.associate("tag_groups").remove(tag_group);
```
即可。同理你可以添加关系。同样的规则适合 一对多 的情况。

对于sessionFilter,标准的hibernate 写法是:

```
Query filterQuery = session.createFilter(tag_group.getTags(), "where this.name like 'S%'");
```

ServiceFramework 则更加直观

```
tag_group.associate("tags").where("name like 'S%'"));
```

最为有意思的是，你可以定义tags方法，内部你可以任意实现。我们要的只是这个方法签名。

```
@Entity
public class TagGroup extends Model {
    @ManyToMany
    private List<Tag> tags = list();
    
    public Association tags(){
       throw  new AutoGeneration()
    }
}
```

接着你可以这样调用上面的例子：


```
tag_group.tags().where("name like 'S%'"));
```

浑然天成，对吗？

其实上面的移除关系之类的操作也可以用这个方法签名。

```
tag_group.tags().remove(tag);
```



此外 ServiceFramework 还提供了更加灵活的回调，比如：

```
    @AfterUpdate
    public void afterUpdate() {
        findService(RedisClient.class).expire(this.id().toString());
        BlogTag.create(map("object_id", 19)).save();
    }
```

你可以在对象更新后接着进行其他数据库操作，或者获取Service进行缓存清除操作。就这个例子而言你可以很方便的实现对象缓存。

校验器，对了，更简单，只是一个声明即可：

```
@Entity
public class Tag extends Model {
    @Validate
    private final static Map $name = map(
           presence, map("message", "{}字段不能为空"), 
           uniqueness, map("message", "{}字段不能重复"));
    
    @Validate
    private final static Map $associated = map(associated, list("blog_tags"));
}
```

总之，ServiceFramework 在一下几个方面进行了简化：

1. 属性声明。你不需要显示显式声明普通属性和设置get/set方法。
2. 极度简化了关联关系配置
3. 使得关联关系操作真的好用了。也让你敢用了。
4. 完善简洁的声明式校验器
5. 增强并且简单的回调操作。


##对Controller层封装的思考

PHP之类的函数式编程语言(当然，从5.0开始也是面向对象了)有一个很大的优点，就是你按流程调用一定数量的函数，基本就能把逻辑走下来。Java一直缺乏这方面的觉悟。比如一个 isEmpty判断，你要么自己写个工具类，要嘛调用StringUtils(apache commons里)的。
总之不方便，天哪，为啥不能直接这么用，在你写代码的时候，你只是习惯性的调用isEmpty,然后你惊奇的发现框架已经给你提供好了，无需任何包的导入!

```
if (!isEmpty(param("channelIds"))) {
   …..
}
```
所以，一个用起来清爽的Controller应该是，你一旦继承了父类，就能够从父类获得大量的有用的方法，比如join,isEmpty,map,list等。
我甚至在想，我们应该实现一套PHP的函数库放到ApplicationController中。继承了它，你就获得了一个函数库。

这个，随着ServiceFramework的进一步开发，会有更多函数加入。当然我希望有一天能够实现php的函数库，这可以作为一个目标。


### 方便的参数获取
目前Java的框架提供了各种获取参数的方式。

最传统的是:

```
String abc= request.getParameter("abc");
```

先进点的通过参数，比如SpringMVC 就是通过这种方式进行的。

```
public class TagAdminController extends ApplicationController {
  
  public void action1(@param("abc") String abc){
  }
 
```

好处是非常便于单元测试。

当然还有通过实例变量，以及定义一个Pojo类然后通过一定机制接受参数。

我们来分析下上面的缺点：

* 第一种毫无疑问，一塌糊涂。引入了Servlet API.
* 第二种 在参数很多的情况下会疯掉。而且很多人会引入一个复杂的对象接受参数，这就是第二种方式和后面的Pojo接受参数方法的杂交。

* 通过实例变量则更加愚蠢，到头来会在Controller中一大堆你都不知道用来干嘛的实例变量。

* 通过Pojo来接受参数也太复杂了吧？是不是我只有一个参数也需要构建一个Pojo模型？

其实深入一点，接受参数无非就是为了构造查询条件或者将其存入持久层。
构造查询条件很简单，如果这样子你能觉得合理吗？

```
  Tag.find("name=:name",map("name",params("name")));
```
这我们只是为了获得name 这个参数，最直观的方式就是有个方法传入name这个key,就能获得用户传来的name.最简单，最直观。

ServiceFramework 采用了 params函数。他还有很多变种，方便类型转换，比如

```
 int id = paramsAsInt("id");
```

那么对于数据存入呢？如何方便的构造一个模型？

```
Tag tag = Tag.create(params());
```

create 方式是任何模型类都具有的一个方法。不需要用户自己实现。
params() 返回一个键值对，类似

```
request.getParameterMap();
```

然后模型类会根据键值对填充模型类。安全方面则由模型类的验证器负责。

对于名称匹配的问题，比如：
用户传递过来的是tag_name,但是模型类中的字段名是name.这个时候是函数库起作用的时候了。仍然以
上面的tag为例子。

```
Tag tag = Tag.create(selectMapWithAliasName(params(),"tag_name","name"));
```
selectMapWithAliasName会将tag_name 替换成name.其他不变。并且，selectMapWithAliasName是继承ApplicationController后就直接可用的。这也是我一直强调的函数库的好处。

并且通过params方法获取参数的方式也非常方便单元测试，没有和Server进行任何耦合。params()只不过是调用父类的一个map的get方法而已。所以测试时你只要实现填充该map即可。

###方便的拦截器

struts的过滤器配置就是误入歧途。为了添加一个过滤器，你需要做以下几件事情：

1. 定义一个拦截器类
2. 接着将其配置到struts.xml文件中
3. 接着还要将这个拦截器放到你想拦截的action配置中

神那，搞什么…. 一个拦截器而已….

还有一种普遍的需求是，几个Action方法调用前都要获取一个对象A，然后这个对象在action中接着用。这个时候struts的机制就有点为难了。
在我看来，拦截器也是可以在使用普通的方法和声明来实现的。比如同一个类里

```
class C
{
   @BeforeFilter
   private final static Map $b = map(only, list("a","c"));
   
   public void a(){
   }
   
   public void c(){
   }
   
   public void d(){
   }
   
   private void b(){
   }
}
```
   @BeforeFilter 声明，当执行a,c方法的时候，先调用b方法。我们可以看到，拦截器就是一个简单的私有方法。通过@BeforeFilter 声明拦截
   那些方法。把这些被拦截的方法升级为action的时候，这个私有的b方法就是一个纯种的拦截器了。
   
   对于跨controller的拦截器，你可以定义在父类。其实就是把b移动到父类上即可。
   
   是否非常简单呢？而且能解决我之前提到的对象共享的问题。



Controller 基本设计目标是：

1. 便利的url 组织。Rest的风格声明
2. 方便的过滤器，比如前置，环绕和后置过滤器。
3. 声明式的Service，Util引入。
4. 线程安全
5. 渲染要能方便的指定HttpStatus,指定输出格式。
6. 大量的函数可用。

看看ServiceFramework是如何实现这些既定目标的。

一个典型的示例如下：

```
public class TagAdminController extends ApplicationController {

    //过滤器声明
    @BeforeFilter
    private final static Map $find_tag = map(only, list("add_tag_to_tag_group", "destroy_tag","destroy_tag_from_tag_group"));

    @AroundFilter
    private final static Map $print_action_execute_time2 = map(only, list("search"));

    //Service 引入
    @Inject
    private TagService tagService;

    //一个请求
    @At(path = "/tag_group", types = POST)
    public void create_tag_group() {
        TagGroup tagGroup = TagGroup.create(params());
        if (!tagGroup.save()) {
            render(HTTP_400, tagGroup.validateResults);
        }
        render(ok());
    }
    
     //环形过滤器实现
     private void print_action_execute_time2(RestController.WowAroundFilter aroundFilter) {
        long time1 = System.currentTimeMillis();
        aroundFilter.invoke();
        logger.info("time:[" + (System.currentTimeMillis() - time1) + "]");

    }
    }
```
这一段代码实现的功能包括：

* 对 add_tag_to_tag_group等三个Action方法添加前置过滤器，过滤器也是一个函数，find_tag.
* 环形过滤器实现了调用时间打印
* @At 可方便的配置路径
* 每个Action调用都会实例化一个TagAdminController 从而实现线程安全。
* 渲染的话只有一个简单的render方法。如果错误，可指定 HTTP_400 状态值
* Service服务可以通过声明实例变量的方式引入。

如果你希望某个过滤器全局生效，你可以放到ApplicationController中。




## 项目组织的思考
ServiceFramewrok 天然分为四部分:

* controller
* service
* util
* model

我们的目标是给你一个最佳的实践来组织你的项目结构。让你不用烦心代码应该如何放置。
你可以在配置文件配置他们的位置。典型配置为:

```
###############application config##################
application:
    controller: com.example.controller
    model:      com.example.model
    service:    com.example.service
    util:       com.example.util

```

同时这样也获得另外一个好处，框架可以为根据你的类的不同作用进行相应的代码增强。便于你获得更多的可用功能

我们希望通过这种组织方式使得项目天然获得规范化，而不是不同的额程序员开发而导致项目的结构五花八门。



##总结

上面我们只是讨论了Model,Controller的设计问题。其实还有Service和Util的设计问题。这个应该通过IOC来处理。至于静态资源文件则不在讨论方位之内。



