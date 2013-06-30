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

## Controller

下面是一个典型的ServiceFramework Controller.



	public class TagController extends ApplicationController {
	
	    static{
	      beforeFilter("checkParam",map(only, list("save", "search"));
	      beforeFilter("findTag",map(only, list("addTagToTagGroup", "deleteTagToTagGroup","createBlogTag"));
	      aroundFilter("print_action_execute_time2",map());
	    }
	
	   
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
	
	
	    @Inject
	    private RemoteDataService remoteDataService;
	
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
		   
	     private void print_action_execute_time2(RestController.WowAroundFilter wowAroundFilter) {
	        long time1 = System.currentTimeMillis();
	
	        wowAroundFilter.invoke();
	        logger.info("execute time2:[" + (System.currentTimeMillis() - time1) + "]");
	
	    }
	
	
	}

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




		  @BeforeFilter
		  private final static Map $checkParam = map(only, list("save", "search"));


你可以使用static block 进行声明:


	static{
	      beforeFilter("checkParam",map(only, list("save", "search"));
	}


使用filed声明的话，filter是声明在一个map属性上的。map 接受两个属性，only,except。如果没有这两个属性，那么表示过滤当前Controller中所有Action。
属性依然以$开头，后面的属性名其实是一个方法的名称。比如你会发现在上面的controller中确实包含一个checkParam 方法。

例子的含义是，只有save,search两个Action方法在调用前会先调用checkParam。

Controller是多线程安全的。这意味着，你可以安全的使用实例变量。示例中"addTagToTagGroup", "deleteTagFromoTagGroup","createBlogTag" 三个Action在调用前都需要事先获得tag对象。你可以使用findTag过滤器先填充 tag实例变量。如果用户没有传递tag名，就可以在过滤器中直接告诉用户参数问题。

需要注意的一点是，BeforeFilter 比 AroundFilter 运行的更早。Filter 也可以调用render 方法，进行结果输出。

###路径配置

路径配置使用的也是注解配置。


	@At(path = "/tag_group/tag", types = {PUT, POST})


@At注解接受两个参数，path 和 types

path 代表请求路径。 types则是表示接受的请求方法的,默认是GET.

path 支持占位符，比如:


	@At(path = "/{tag}/blog_tags", types = PUT)

tag这个值会被自动填充到请求对象中。你可以通过 param("tag")获取。


#### request 参数获取

在ServiceFramework 中 提供了一个非常便利的获取request参数的方式。不管是form表单,get请求，还是url中的数据，都可以统一通过param() 方法获取。


	int id = paramAsInt("id");
	//或者
	String id = param("id");


比如这就可以获取 id 参数，并且将其转换为int类型。
如果你确认传递过来的是json或者xml格式，你可以调用下面的方式

	JSON obj = paramAsJSON();
	//或者
	JSON obj = paramsAsXML();

其中,xml文本的数据会自动转化json格式,便与操作。

ServiceFramework 尽量让事情简单而方便。

方法列表:

	params()
	param(key)
	param(key,defaultValue)
	paramAsInt(key)
	paramAsLong(key)
	paramAsFloat(key)
	//还有更多….
	
Controller有很多有用的方法。下面简单罗列几个：

1. projectByMethod(List list, String method,Object… params)


		   List<Map> result = list(
		                map(
		                        "key1", "value1",
		                        "key2", "value2"
		                ),
		                map(
		                        "key1", "value3",
		                        "key2", "value4"
		                )
		        );
		        //newResult = list("value1","value3")
		        List<String> newResult = projectByMethod(result, "get", "key1");


2. project(List<Map> list, String key)
    projectByMethod 的定制版。
    
3. join

    
		String jack = join(list("a","b","c"),",")
		//jack == a,b,c

    
4. getInt/getLong/getString等


		   int jack = getInt(map("key1",1),"key1");
		   //jack == 1

   
5. aliasParamKeys


		Map newMap = aliasParamKeys(map(
		             "key1", "value"
		     ), "key1", "key2");
		//newMap == map("key2","value")


6. or(T a, T b)

   这相当于 a==null?b:a
   
7. regEx(String reg)

相当于 Pattern.compile(reg);


对时间的支持也是非常优秀的和方便的。




   

#### 渲染输出

所有渲染输出统一使用render 方法。

普通文本输出


	render("hello word");


如果传入的是对象，会自动呗转化为json格式


	render(tag);


你可以手动指定输出格式


	render(tag,ViewType.xml);


你还可以指定输出的http状态码


	render(HTTP_200,tag,ViewType.xml);


render 方法也可以在过滤器中使用。一旦调用render方法后，就会自动跳过action调用。


	    @At(path = "/tag_group/create", types = POST)
	    public void createTagGroup() {
	        TagGroup tagGroup = TagGroup.create(params());
	        if (!tagGroup.save()) {
	            render(HTTP_400, tagGroup.validateResults);
	        }
	        render(OK);
	    }


在上面的示例代码中，你无需render之后再调用return 语句。

###Json格式输出控制
对于json输出的控制是非常有必要，因为某些字段你可能不想展示给用户，不同权限的人可以看到不同的字段，等等，
你还可能希望某些情况下格式化json，便于阅读。在Controller层，这些很容易实现。


	//设置json输出,排除字段blog_tags
	config.setExcludes(new String[]{"blog_tags"});
	//格式化输出json
	config.setPretty(true);


config对象来自 父类。本质上就是json-lib 中的JsonConfig。对json控制非常的完善。能够满足大部分输出要求。


ServiceFrameork 也支持 Velocity,支持html的输出,使用render 时指定 ViewType 为 html即可。

###ServiceFramework


	@Inject
	private RemoteDataService remoteDataService;


之后你就可以在Action中直接使用remoteDataService了。

#### Controller提供的便利的方法集

在controller中，你天然会获取大量有用的工具方法。比如 isEmpty，字符串join。比如


	JPQL query = (JPQL) invoke_model(param("type"), "where", "tag.name in (" + join(newTags, ",", "'") + ")");








