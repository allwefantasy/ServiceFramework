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

下面是一个典型的Controller.

```
public class TagController extends ApplicationController {

    @BeforeFilter
    private final static Map $checkParam = map(only, list("searchBlogTagsByName"));

    @At(path="/{tag}/blog_tags",types = GET)
    public void searchBlogTagsByName(){
      List<BlogTag> blogTags =  BlogTag.where("tag.name=:name",map("name",param("tag")))
                .offset(paramAsInt("start",0))
                .limit(paramAsInt("size",15))
                .fetch();
        render(blogTags);
    }
    
    @Inject
    private RemoteDataService remoteDataService;

    private void checkParam() {

        if (isEmpty(param("tag"))) {
            render(HTTP_400, format(FAIL, "必须传递标签名称"));
        }
    }
}
```

我们再来分析ServiceFramework的controller有什么特点。

1. 成为Controller的必要条件是继承 ApplicationController
2. 类似Model验证器，你可以以相似的方式添加过滤器
3. 通过At配置路径以及接受的Http 请求方式
4. 所有其他的Service或者Util推荐采用使用IOC容器管理。譬如例子里的RemoteDataService
5. filter只是一个简单的私有方法。如果申明在ApplicationController。那么对所有controller有效

