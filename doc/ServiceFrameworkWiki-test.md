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

### 测试

单元测试非常重要。
controller 测试:

		/**
		 * 6/28/13 WilliamZhu(allwefantasy@gmail.com)
		 */
		public class SearchControllerTest extends BaseControllerTest {

		    @Test
		    public void search() throws Exception {
		        RestResponse response = get("/doc/blog/search", map(
		                "tagNames", "_10,_9"
		        ));
		        Assert.assertTrue(response.status() == 200);
		        Page page = (Page) response.originContent();
		        Assert.assertTrue(page.getResult().size() > 0);
		    }		  
		}
		
继承BaseControllerTest后, get,post,delete,put等方法接受两个参数，一个是 请求路径，一个是map对象(参数)。 如果你需要更多的控制，可参看
runAction方法。

Service层的测试:

			public class SearchServiceTest extends BaseServiceTest {
			   
			    @Test
			    public void search() {
			        SearchService searchService = findService(SearchService.class);			       
			        Page page = searchService.search(searchParams, new Page());
			        Assert.assertTrue(page.getResult().size() > 0);
			    }			   
			}


继承BaseServiceTest 父类，你可以通过findService方法获取你要测试的Service.然后直接测试验证结果即可。

			

	   