![logo](http://allwefantasy.com/service_framework_logo_big.jpg)

## Welcome To ServiceFramework

ServiceFramework is  a web-application framework that includes some powerfull Persistence Components 
like [ActiveORM](https://github.com/allwefantasy/active_orm) and [MongoMongo](https://github.com/allwefantasy/mongomongo)
written by  java language according to the Model-View-Controller(MVC) pattern.


1.  ActiveORM in ServiceFramework like ActiveRecord in Rails,Awesome.
  
  
		    List<Tag> tags = Tag.where(map("name","java")).fetch;
   
2. Controller is total redesigned and some usefull functions are provied.

3. Most Object are managed by IOC.
  
			  @inject
			  Service service;
   
4. Easy to Test without Servlet container
  
	     @Test
	     public void search() throws Exception {
	         RestResponse response = get("/doc/blog/search", map(
	                 "tagNames", "_10,_9"
	         ));
	         Assert.assertTrue(response.status() == 200);
	         Page page = (Page) response.originContent();
	         Assert.assertTrue(page.getResult().size() > 0);
	     }

5. just a little configuration and Thrift & RESTFul are all supported
    
			 
			    @At(path = "/tag/{type}/rank", types = GET)
			    public void listTagByCount() {
			        searchService.tagCount(param("type"), page);
			        render(200, page);
			    }
	  
6. Template Engine using Velocity.

	 
			    @At(path = "/hello", types = GET)
			    public void hello() {
			        render(200, map(
			                "name", "ServiceFramework"
			        ), ViewType.html);
			    }  





## QuickStart

Step 1 >   clone
 
 

	git clone https://github.com/allwefantasy/ServiceFramework
 
 
Step 2 >   import to your IDE
 
Step 3 >   modify config/application.yaml according to your DB Connection infomation. Notice that if you only use mysql you should disable mongodb.
  				
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
 
Step4 >   import sql/wow.sql into MySQL
 
Step5 >   create com.example.model.Tag class.

			public class Tag extends Model 
			{
			
			}

Step6 >   create com.example.controller.http.TagController 

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
			
Step7 >  create template/tag/hello.vm


			Hello $name!  Hello  world!		

Step8 >   create startup class

    public class ExampleApplication {

    public static void main(String[] args) {
        ServiceFramwork.scanService.setLoader(ExampleApplication.class);
        Application.main(args);
    }
    }
    
Step9 >   run  ExampleApplication in your IDE

Step10 >  visit  http://127.0.0.1:9002/hello . Check your database, table tag will have one item.


Step11 >  Prepare for Test Unit . modify runner.DynamicSuite and add follow in  first line of initEnv method.

      ServiceFramwork.scanService.setLoader(ExampleApplication.class);

Step12 > create test class test.com.example.TagControllerTest

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

Step13 >  run DynamicSuiteRunner 



## Tutorial Usefull


* [Walk through for Creating Your first Application with ServiceFramework](https://github.com/allwefantasy/service_framework_example/blob/master/README.md)


## Step by Step tutorial
Step-by-Step-tutorial-for-ServiceFramework(continue...)

* [Step-by-Step-tutorial-for-ServiceFramework(1)](https://github.com/allwefantasy/service_framework_example/blob/master/README.md)
* [Step-by-Step-tutorial-for-ServiceFramework(2)](https://github.com/allwefantasy/service_framework_example/blob/master/doc/Step-by-Step-tutorial-for-ServiceFramework\(2\).md)
* [Step-by-Step-tutorial-for-ServiceFramework(3)](https://github.com/allwefantasy/service_framework_example/blob/master/doc/Step-by-Step-tutorial-for-ServiceFramework\(3\).md)
* [Step-by-Step-tutorial-for-ServiceFramework(4)](https://github.com/allwefantasy/service_framework_example/blob/master/doc/Step-by-Step-tutorial-for-ServiceFramework\(4\).md)


## Doc Links

* [Summary](https://github.com/allwefantasy/ServiceFramework/tree/master/doc/ServiceFrameworkWiki-start.md)
* [Model](https://github.com/allwefantasy/ServiceFramework/tree/master/doc/ServiceFrameworkWiki-model.md)
* [Controller](https://github.com/allwefantasy/ServiceFramework/tree/master/doc/ServiceFrameworkWiki-controller.md)
* [Test](https://github.com/allwefantasy/ServiceFramework/tree/master/doc/ServiceFrameworkWiki-test.md)


##  Some projects based on ServiceFramework

* [QuickSand](https://github.com/allwefantasy/QuickSand)


