# ServiceFramework Wiki

[README-EN](https://github.com/allwefantasy/ServiceFramework/blob/master/README-EN.md)

ServcieFramework 定位在 **移动互联网后端** 领域,强调开发的高效性，其开发效率可以比肩Rails.

ServcieFramework 目前更新频率较高,我现在一直疏于更新中央仓库的版本。所以不再更新maven中央仓库。

建议：


1. git clone https://github.com/allwefantasy/csdn_common，
maven install 到自己本地或者 mvn deploy到自己的私有maven仓库.

2. 如果需要使用MySQL支持，则git clone https://github.com/allwefantasy/active_orm,
maven install 到自己本地或者 mvn deploy到自己的私有maven仓库.

3. 如果需要使用MongoDB支持，则git clone https://github.com/allwefantasy/mongomongo,
   maven install 到自己本地或者 mvn deploy到自己的私有maven仓库。

2. git clone ServiceFramework, maven install 到自己本地或者 mvn deploy到自己的私有maven仓库.

经过以上步骤即可使用

### 项目示例

[https://github.com/allwefantasy/godear](https://github.com/allwefantasy/godear) 该项目是一个RSS订阅系统。
里面展示了ServiceFramework各种典型用法，包括如何构造非JSON Rest API的具有页面的接口。

### 在Maven中使用该项目

接着确保 项目根目录下有config/application.yml,config/logging.yml 两个文件即可。示例可参看该项目中config文件夹。

QuickStart：[搭建自己的第一个项目](https://github.com/allwefantasy/ServiceFramework/blob/master/doc/ServiceFrameworkWiki-example.md)

ServiceFramework 特点：

1. ActiveRecord化的Model层，支持 MongoDB 和 MySQL.
  
  
		    List<Tag> tags = Tag.where(map("name","java")).fetch;
   
2. 完全重新设计的Controller层,大量便利的函数。创新的过滤器设计，比如下面的代码表示validate 方法会拦截 push方法

           static {
             beforeFilter("validate", WowCollections.map(only, WowCollections.list("push")));
           }

3. 大部分对象使用IOC自动管理,使用简单。
  
		   @inject
		   Service service;
   
4. 不依赖容器，单元测试简单，从action到service,都可做到测试代码最少
  
	     @Test
	     public void search() throws Exception {
	         RestResponse response = get("/doc/blog/search", map(
	                 "tagNames", "_10,_9"
	         ));
	         Assert.assertTrue(response.status() == 200);
	         Page page = (Page) response.originContent();
	         Assert.assertTrue(page.getResult().size() > 0);
	     }

5. 接口调用监控

	* 接口 QPS 监控
	* 接口平均响应耗时监控
	* 接口调用量(如果是http的话，则是各种状态码统计)
	* 内置http接口，提供json数据展示以上的系统状态

6. 1.2 以上版本集成了Dubbo,具有Dubbo的所有有点。同时还添加RestProtocol协议，可以像RPC一样调用现有的HTTP服务。
所有工作只需要定义一套 Interface接口即可。

        在ServiceFramework中，调用一个同样也是由ServiceFramework开发的HTTP接口可以变得非常简单。

        @At(path = "/say/hello", types = {RestRequest.Method.GET})
            public void sayHello() {
                render(200, "hello" + param("kitty"));
        }

        这里很简单通过调用 http://127.0.0.1/say/hello?kitty=wow ，服务会返回hellowow 这样的字符串。
        使用方可以通过HttpClient直接调用这个接口。为了方便调用方，服务提供方可以添加一个接口：

        public interface TagController {
            @At(path = "/say/hello", types = {RestRequest.Method.GET, RestRequest.Method.POST})
            public HttpTransportService.SResponse sayHello(RestRequest.Method method, Map<String, String> params);

            @At(path = "/say/hello", types = {RestRequest.Method.GET})
            public HttpTransportService.SResponse sayHello3(@Param("kitty") String kitty);

        接着，调用方引入这个接口，就可以像这样调用了：

        tagController.sayHello(RestRequest.Method.GET, WowCollections.map("kitty", "你好，太脑残")).getContent()

        或者

        tagController.sayHello3("哇塞，天才呀").getContent()

        服务提供者可以针对一个http接口定义出任意个方法，每个方法都之定义一部分参数，这样可以有效方便调用者使用。

7. 如果你不使用Dubbo，你也可以非常容易的调用第三方的标准HTTP接口，达到类似RPC调用的效果。

   * 将第三方HTTP API 做个申明，例如有个搜索接口(Scala代码示例)
   
			   trait SearcherClient {
			  @At(path = Array("/v2/~/~/_search"), types = Array(GET, POST))
			  @BasicInfo(
			    desc = "索引服务",
			    state = State.alpha,
			    testParams = "",
			    testResult = "",
			    author = "WilliamZhu",
			    email = "allwefantasy@gmail.com"
			  )
			  def search(params: Map[String, String], content: String, method: net.csdn.modules.http.RestRequest.Method): java.util.List[HttpTransportService.SResponse]
			
			}

    * 接着在需要使用该接口的地方调用如下代码构建SearcherClient对象。记住，这个对象只能构建一次(Scala代码示例)
    
				   val _searchClient = AggregateRestClient.buildClient[SearcherClient](hostAndPorts, new SearchEngineStrategy(), httpRequest)
				   //其中，hostAndPorts 为域名和端口。
				   //可以是多个。SearchEngineStrategy 是自定义实现如何调用后端服务，
				   //是轮训的负载均衡还是有特别的逻辑
 
    
    * 现在可以使用了(Scala代码示例)
    
		    val res = _searchClient.search(
		        url._2.toMap ++ Map("index" -> index, "type" -> ctype),
		        query,
		        RestRequest.Method.POST).searchResult
    
    使用该种方式调用第三方API会产生Trace日志。
    
7. 服务降级限流
ServiceFramework主要面向后端服务，如果没有自我保护机制，系统很容易过载而不可用。经过一定的容量规划，或者通过对接口调用平均响应耗时的监控，
我们可以动态调整 ServiceFramework 的QPS限制，从而达到保护系统的目的。这些都可以通过配置以及内置的http接口完成。
监控将会是ServiceFramework后续的重点。早期ServiceFramework也通过日志让用户对自己系统有更多的感性认识，日志会打印：

	 * http请求url
	 * 整个请求耗时
	 * 数据库耗时(如果有)
	 * 响应状态码
	 
你可以很方便的通过shell脚本做各项统计



8. Thrift 和 RESTFul 只需简单配置即可同时提供 Thrift 和 RESTFul 接口
    
			 
		###############http config##################
		http:
		    port: 7700
		    disable: false

		thrift:
		    disable: false
		    services:
		        net_csdn_controller_thrift_impl_CLoadServiceImpl:
		           port: 7701
		           min_threads: 100
		           max_threads: 1000		        

		    servers:
		        load: ["127.0.0.1:7701"]

	  
9. 支持 Velocity, 页面可直接访问所有实例变量以及helper类的方法。支持Velocity 进行模板配置

	 
			    @At(path = "/hello", types = GET)
			    public void hello() {
			        render(200, map(
			                "name", "ServiceFramework"
			        ), ViewType.html);
			    }  


## QuickStart

Step 1 >   克隆项目
 
 

	git clone https://github.com/allwefantasy/ServiceFramework
 
 
Step 2 >   导入到IDE.
 
Step 3 >   根据你自己的数据库信息 编辑修改 config/application.yaml .注意如果你使用mysql,需要disable 调 mongodb.反之亦然
  				
    datasources:
        mysql:
           host: 127.0.0.1
           port: 3306
           database: wow
           username: root
           password: root
           disable: false
        mongodb:
           host: 127.0.0.1
           port: 27017
           database: wow
           disable: false
        redis:
            host: 127.0.0.1
            port: 6379
            disable: true 		          
 
Step4 >   在Mysql中导入 sql/wow.sql.
 
Step5 >   新建 com.example.model.Tag 类.

			public class Tag extends Model 
			{
			
			}

Step6 >   新建 com.example.controller.http.TagController

          public class TagController extends ApplicationController 
			{
			   @At(path = "/hello", types = RestRequest.Method.GET)
			    public void hello() {
			        Tag tag = Tag.create(map("name", "java"));
			        tag.save();
			        render(200, map(
			                "tag", tag
			        ), ViewType.html);
			    }
			}
			
Step7 >	新建 template/tag/hello.vm


			Hello $tag.name!  Hello  world!		

Step8 >   创建启动类

    public class ExampleApplication {

    public static void main(String[] args) {
        ServiceFramwork.scanService.setLoader(ExampleApplication.class);
        Application.main(args);
    }
    }
    
Step9 >   运行  ExampleApplication

Step10 >  浏览器中输入  http://127.0.0.1:9002/hello .同时查看数据库，你会发现tag表已经有数据了。  


Step11 >  写个Action单元测试  编辑 runner.DynamicSuite  在 initEnv方法第一行处添加

      ServiceFramwork.scanService.setLoader(ExampleApplication.class);

Step12 > 创建测试类 test.com.example.TagControllerTest

    public class TagControllerTest extends BaseControllerTest {
	    @Test
	    public void testHello() throws Exception {
	        Tag.deleteAll();
	        RestResponse response = get("/hello", map());
	        Assert.assertTrue(response.status() == 200);
	        String result = response.content();
	        Assert.assertEquals("Hello java!  Hello  world!", result);
	    }
    }

Step13 >  运行 DynamicSuiteRunner 跑起测试

Step14 >  补充：你也可以不使用DynamicSuiteRunner去跑。直接使用IDE跑单元测试类。需要做的是在你的单元测试类中加几句代码：

    static {
        initEnv(ExampleApplication.class);
    }

加这句主要是保证启动容器，并且采用了合适的类加载器。

QuickStart 的一些常见错误:

1. application 文件 数据连接配置错误。单元测试一定需要单独配置test的配置。因为单元测试一般可能会会有数据清理等，系统强制使用
   test的配置。

2. ServiceFramework 是使用配置文件来找类并且加载的，所以你需要正确配置contorller等所在位置。在上述测试中，包名和类名必须保证和示例一致。如果你需要使用不同的package,那么你需要修改application.yml中的application 配置。如下:
  
		  application:
		    controller: com.example.controller.http
		    model:      com.example.model
		    document:   com.example.document
		    service:    com.example.service
		    util:       com.example.util
		    test:       test.com.example






Model层基于如下开源项目:
 
* [ActiveORM](https://github.com/allwefantasy/active_orm)
* [MongoMongo](https://github.com/allwefantasy/mongomongo)


ServiceFramework 不适合遗留项目。我们倾向于在一个全新的项目中使用它。

   
## Doc Links

* [Summary](https://github.com/allwefantasy/ServiceFramework/tree/master/doc/ServiceFrameworkWiki-start.md)
* [Model](https://github.com/allwefantasy/ServiceFramework/tree/master/doc/ServiceFrameworkWiki-model.md)
* [Controller](https://github.com/allwefantasy/ServiceFramework/tree/master/doc/ServiceFrameworkWiki-controller.md)
* [Test](https://github.com/allwefantasy/ServiceFramework/tree/master/doc/ServiceFrameworkWiki-test.md)
* [Deploy](https://github.com/allwefantasy/ServiceFramework/tree/master/doc/ServiceFrameworkWiki-deploy.md)

## Step by Step tutorial
Step-by-Step-tutorial-for-ServiceFramework(continue...)

* [Step-by-Step-tutorial-for-ServiceFramework(1)](https://github.com/allwefantasy/service_framework_example/blob/master/README.md)
* [Step-by-Step-tutorial-for-ServiceFramework(2)](https://github.com/allwefantasy/service_framework_example/blob/master/doc/Step-by-Step-tutorial-for-ServiceFramework\(2\).md)
* [Step-by-Step-tutorial-for-ServiceFramework(3)](https://github.com/allwefantasy/service_framework_example/blob/master/doc/Step-by-Step-tutorial-for-ServiceFramework\(3\).md)
* [Step-by-Step-tutorial-for-ServiceFramework(4)](https://github.com/allwefantasy/service_framework_example/blob/master/doc/Step-by-Step-tutorial-for-ServiceFramework\(4\).md)



##  Some projects based on ServiceFramework

* [QuickSand](https://github.com/allwefantasy/QuickSand)








