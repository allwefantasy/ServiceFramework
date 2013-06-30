# ServiceFramework Wiki

[README-EN](https://github.com/allwefantasy/ServiceFramework/blob/master/README-EN.md)

![logo](http://allwefantasy.com/service_framework_logo_big.jpg)

ServcieFramework 定位在 **移动互联网后端** 领域,强调开发的高效性，其开发效率可以比肩Rails.

1. ActiveRecord化的Model层，支持 MongoDB 和 MySQL.
  
  
		    List<Tag> tags = Tag.where(map("name","java")).fetch;
   
2. 完全重新设计的Controller层,支持方法级过滤器，大量便利的函数

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

5. Thrift 和 RESTFul 只需简单配置即可同时提供 Thrift 和 RESTFul 接口
    
			 
			    @At(path = "/tag/{type}/rank", types = GET)
			    public void listTagByCount() {
			        searchService.tagCount(param("type"), page);
			        render(200, page);
			    }
	  
6. 支持 Velocity.

	 
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
			        Tag tag = Tag.create(map("name","java"));
			        tag.save();
			        render(200, map(
			                "name", tag.attr("name",String.class)
			        ), ViewType.html);
			    }
			}
			
Step7 >	新建 template/tag/hello.vm


			Hello $name!  Hello  world!		

Step8 >   创建启动类

    public class ExampleApplication {

    public static void main(String[] args) {
        ServiceFramwork.scanService.setLoader(ExampleApplication.class);
        Application.main(args);
    }
    }
    
Step9 >   运行  ExampleApplication

Step10 >  浏览器中输入  http://127.0.0.1:9002/hello .同时查看数据库，你会发现tag表已经有数据了。  

Model层基于如下开源项目:
 
* [ActiveORM](https://github.com/allwefantasy/active_orm)
* [MongoMongo](https://github.com/allwefantasy/mongomongo)


ServiceFramework 不适合遗留项目。我们倾向于在一个全新的项目中使用它。

   
## Doc Links

* [Summary](https://github.com/allwefantasy/ServiceFramework/tree/master/doc/ServiceFrameworkWiki-start.md)
* [Model](https://github.com/allwefantasy/ServiceFramework/tree/master/doc/ServiceFrameworkWiki-model.md)
* [Controller](https://github.com/allwefantasy/ServiceFramework/tree/master/doc/ServiceFrameworkWiki-controller.md)
* [Test](https://github.com/allwefantasy/ServiceFramework/tree/master/doc/ServiceFrameworkWiki-test.md)

## Step by Step tutorial
Step-by-Step-tutorial-for-ServiceFramework(continue...)

* [Step-by-Step-tutorial-for-ServiceFramework(1)](https://github.com/allwefantasy/service_framework_example/blob/master/README.md)
* [Step-by-Step-tutorial-for-ServiceFramework(2)](https://github.com/allwefantasy/service_framework_example/blob/master/doc/Step-by-Step-tutorial-for-ServiceFramework\(2\).md)
* [Step-by-Step-tutorial-for-ServiceFramework(3)](https://github.com/allwefantasy/service_framework_example/blob/master/doc/Step-by-Step-tutorial-for-ServiceFramework\(3\).md)
* [Step-by-Step-tutorial-for-ServiceFramework(4)](https://github.com/allwefantasy/service_framework_example/blob/master/doc/Step-by-Step-tutorial-for-ServiceFramework\(4\).md)


##  Some projects based on ServiceFramework

* [QuickSand](https://github.com/allwefantasy/QuickSand)








