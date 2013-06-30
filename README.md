# ServiceFramework Wiki

[README-EN](https://github.com/allwefantasy/ServiceFramework/blob/master/README-EN.md)

![logo](http://allwefantasy.com/service_framework_logo_big.jpg)

ServcieFramework 定位在 **移动互联网后端** 领域,强调开发的高效性，其开发效率可以比肩Rails.

1. ActiveRecord化的Model层，支持 MongoDB 和 MySQL.
  
	  * 典型代码
  
		    List<Tag> tags = Tag.where(map("name","java")).fetch;
   
2. 完全重新设计的Controller层,支持方法级过滤器，大量便利的函数

3. 大部分对象使用IOC自动管理,使用简单。

	  * 典型代码
  
			  @inject
			  Service service;
   
4. 不依赖容器，单元测试简单，从action到service,都可做到测试代码最少

   * 典型代码
  
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
    
   * 典型代码
			 
			    @At(path = "/tag/{type}/rank", types = GET)
			    public void listTagByCount() {
			        searchService.tagCount(param("type"), page);
			        render(200, page);
			    }
	  
6. 支持 Velocity.

	  * 典型代码
	 
			    @At(path = "/hello", types = GET)
			    public void hello() {
			        render(200, map(
			                "name", "ServiceFramework"
			        ), ViewType.html);
			    }  




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








