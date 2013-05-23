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

### Thrift支持

ServiceFramework 对[Thrift](thrift.apache.org)进行了支持。可简单通过配置(config/application.yml)文件实现。

	http:
	    port: 9500
	    disable: false  //不禁用http协议
	
	thrift:
	    disable: false  //不禁用thrift协议
	    services:
	        com_example_thrift_demo_HelloWordServiceImpl://thrift 接口实现类，类全名，`.`使用`_`代替
	           port: 7701 //监听端口
	           min_threads: 100
	           max_threads: 1000
	#          interface: com.example.thrift.demo.HelloWorldService//如果HelloWordServiceImpl实现了多个接口，那么需要你手动指定接口位置