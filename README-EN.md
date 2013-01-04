##Welcome To ServiceFramework

ServiceFramework is  a web-application framework that includes some powerfull Persistence Components 
like [ActiveORM](https://github.com/allwefantasy/active_orm) and [MongoMongo](https://github.com/allwefantasy/mongomongo)
written by  java language according to the Model-View-Controller(MVC) pattern.

ServiceFramework divides your application into three layers,each with specific responsibility and they are all connected 
by [Google Guice](http://code.google.com/p/google-guice/).


###A short glance on Action Code

Create Model class.

```java
public class TagSynonym extends Model {
    @OneToMany
    private List<Tag> tags= list();
		
	public Association tags() {
	           throw new AutoGeneration();
	       }
}

public class Tag extends Model {
    @ManyToOne
    private TagSynonym tag_synonym;
	
}

```

That's all code you need to write to define models and build their association.
Awewome,time to see how to use them in your Controller

```java
        
		@At(path = "/tag_synonym/{tag_synonym_name}/tag/{tag_name}", types = POST)
        public void addTagTotagSynonym() {
            Map query = map("name", param("tag_synonym_name"));

            //if tag_synonym  not exits then create new 
            TagSynonym tagSynonym = (TagSynonym) or(
                    TagSynonym.where(query).single_fetch(),
                    TagSynonym.create(query)
            );
            
			//add a new tag to tagSynonym.
            if (!tagSynonym.tags().add(map("name", param("tag_name")))) {
                render(HTTP_400, tagSynonym.validateResults);
            }
            render("ok save");
        }
```

Using Curl to test:

```shell
curl -XPOST '127.0.0.1:9500/tag_synonym/java/tag/j2ee'
```

Magic ,right? Hmm.... If you are intresting on this,please continue to read.


#### Tutorial Usefull


* [Walk through for Creating Your first Application with ServiceFramework](https://github.com/allwefantasy/service_framework_example/blob/master/README.md)
* [How to use Model Association](https://github.com/allwefantasy/service_framework_example/blob/master/doc/Step-by-Step-tutorial-for-ServiceFramework(2).md)

