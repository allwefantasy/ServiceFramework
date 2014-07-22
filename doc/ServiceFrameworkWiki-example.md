#ServiceFramework Wiki

### 创建你的第一个项目

1. 创建一个maven项目。

   在你的pom.xml 文件中中添加如下引用:

        <dependency>
            <groupId>net.csdn</groupId>
            <artifactId>ServiceFramework</artifactId>
            <version>1.1</version>
        </dependency>

2. 在根目录下新建一个config目录，添加两个个文件

   * application.yml(复制application.example.yml里的内容即可)
   * logging.yml

3. 创建一个数据库，名称为wow。修改配置文件中的

			development:
			    datasources:
			        mysql:
			           host: 127.0.0.1
			           port: 3306
			           database: wow
			           username: [数据库账号]
			           password: [数据库密码]
			           disable: false
			        mongodb:
			           disable: true  

4. 将 sql/wow.sql 的表导入到数据库中

5. 建立model类

Tag.java

					package com.example.model;
					
					import net.csdn.common.exception.AutoGeneration;
					import net.csdn.jpa.association.Association;
					import net.csdn.jpa.model.Model;
					
					import javax.persistence.ManyToOne;
					
					/**
					 * 7/22/14 WilliamZhu(allwefantasy@gmail.com)
					 */
					public class Tag extends Model {
					
					    @ManyToOne
					    private TagSynonym tagSynonym;
					
					    public Association tagSynonym() {
					        throw new AutoGeneration();
					    }
					}



TagSynonym.java:

					package com.example.model;

					import net.csdn.common.exception.AutoGeneration;
					import net.csdn.jpa.association.Association;
					import net.csdn.jpa.model.Model;
					
					import javax.persistence.OneToMany;
					import java.util.List;
					
					import static net.csdn.common.collections.WowCollections.list;
					
					/**
					 * 7/22/14 WilliamZhu(allwefantasy@gmail.com)
					 */
					public class TagSynonym extends Model {
					
					    @OneToMany
					    private List<Tag> tags = list();
					
					    public Association tags() {
					        throw new AutoGeneration();
					    }
					}
					

6. 建立controller

				package com.example.controller.http;
				
				import com.example.model.Tag;
				import net.csdn.annotation.rest.At;
				import net.csdn.modules.http.ApplicationController;
				import net.csdn.modules.http.RestRequest;
				import net.csdn.modules.http.ViewType;
				
				/**
				 * 12/25/13 WilliamZhu(allwefantasy@gmail.com)
				 */
				public class TagController extends ApplicationController {
				
				    @At(path = "/tag", types = {RestRequest.Method.POST})
				    public void save() {
				        Tag tag = Tag.create(params());
				        if (tag.save()) {
				            render(200, "成功", ViewType.string);
				        }
				        render(400, "失败", ViewType.string);
				    }
				
				    @At(path = "/tag/{id}", types = {RestRequest.Method.GET})
				    public void find() {
				        Tag tag = Tag.where(map("name", param("id"))).singleFetch();
				        if (tag == null) {
				            render(404, map());
				        }
				        render(404, list(tag));
				
				    }
				
				}
				
				
7. 新建启动类


			package com.example;
			
			import net.csdn.ServiceFramwork;
			import net.csdn.bootstrap.Application;
			
			/**
			 * 4/25/14 WilliamZhu(allwefantasy@gmail.com)
			 */
			public class Example extends Application {
			    public static void main(String[] args) {
			        ServiceFramwork.scanService.setLoader(Example.class);
			        Application.main(args);
			    }
			}
			
8.  如果你使用的包名和示例中不一致，那么请求改config/application.yml文件。

		application:
		    controller: com.example.controller.http
		    model:      com.example.model
		    document:   com.example.document
		    service:    com.example.service
		    util:       com.example.util
		    test:       test.com.example
		    
		    
9. 右键运行Example类，就可以访问了。		    			

	
	
			
					

					


 
  






