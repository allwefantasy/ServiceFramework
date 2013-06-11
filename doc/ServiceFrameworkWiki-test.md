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

单元测试非常重要。这里我们会重点阐述如何进行Controller层的测试。


	    @Test
	    public void testSave() throws Exception {
	    
	        //获取你需呀测试的controller。injector就是google guice 的injector ^_^  
	        TagController tagController = injector.getInstance(TagController.class);
	        
	        //设置请求参数
	        tagController.mockRequest(
	        map(
	                "object_id", "17",
	                "tags", "java,google"
	
	        ),//这些参数会填充进request中，之后可以通过param方法获取。 
	        RestRequest.Method.PUT, //请求方法
	        null//post数据，通常当你要提交json或者xml数据时，填充该值
	        );
	
	        //调用你需要的过滤器  m 方法其实就是通过反射调用拦截器
	        tagController.m("check_params");
	
	
	        try {
	            //调用真实的action
	            tagController.save();
	        } catch (RenderFinish e) {
	           //这是测试唯一比较麻烦的地方。因为render方法会通过抛出RenderFinish异常来结束流程，所以这里你需要手动捕获
	           //下RenderFinish。
	        }
	
	        //获取render后的response对象
	        RestResponse restResponse = tagController.mockResponse();
	        
	        //拿到你传递给render的对象，这个时候你可以查看是否是否你想要的结果
	        JSONObject renderResult = JSONObject.fromObject((String) restResponse.originContent());
	        assertTrue(renderResult.getBoolean("ok"));
	
	        //手动提交数据操作
	        dbCommit();
	        List<BlogTag> blogTags = BlogTag.where("object_id=17").fetch();
	        assertTrue(blogTags.size() == 2);
	
	        //清理数据
	        Tag.delete(format("name in ({})", "'java','google'"));
	        BlogTag.delete("object_id=17");
	
	    }