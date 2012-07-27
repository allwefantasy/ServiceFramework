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




#搜索教程 

<ul id="category"></ul>

可访问的服务器：

测试服务器：192.168.6.35:9400
高亮服务器: 192.168.4.99:8081   
高亮服务的具体使用方式可参看  [搜索API](http://192.168.6.35/search_system_api.html)

##创建索引
1. 索引分片注册
   
   假设需要索引分为6片.那么分片序号为:0-5
   
		curl -XPUT "192.168.6.35:9400/index1/_shard" -d '{"cs1":[0,2,4],"cs2":[1,3,5]}'
   
   结果是 0,2,4号分片在服务器cs1上，另外的则在cs2上。   
2. 查看分片结果

		curl -XGET "192.168.6.35:9400/index1/_shard"


3. 提交索引的结构信息

	  
		   curl -XPUT "192.168.6.35:9400/index1/csdn/_mapping" -d '{"csdn" : {
		        "_source" : { "enabled" : false },
		        "properties" : {
		            "title":           {"type" : "string","term_vector":"with_positions_offsets","boost":2.0},
		            "body":            {"type" : "string","term_vector":"with_positions_offsets"},
		            "username":        {"type" : "string","index":"not_analyzed","store":"no"},
		            "id" :             {"type" : "integer","index":"not_analyzed","include_in_all":false},
		            "created_at" :     {"type" : "integer","index":"not_analyzed","include_in_all":false}
		        }}}'

##提交数据

4. 提交测试数据


		curl -XPUT "192.168.6.35:9400/index1/csdn/_bulk" -d '[
		{"title":"java 是好东西","body":"hey java","id":"1","username":"jack","created_at":2007072323},
		{"title":"this java cool","body":"hey java","id":"2","created_at":2009072323,"username":"robbin"},
		{"title":"this is java cool","body":"hey java","id":"3","created_at":2010072323,"username":"www"},
		{"title":"java is really cool","body":"hey java","id":"4","created_at":2007062323,"username":"google"},
		{"title":"this is wakak cool","body":"hey java","id":"5","created_at":2007062323,"username":"jackde"},
		{"title":"this is java cool","body":"hey java","id":"6","created_at":2007012323,"username":"jackk wa"},
		{"title":"this java really cool","body":"hey java","id":"7","created_at":2002072323,"username":"william"},
		{"title":"this java really cool","body":"hey java","id":"8","created_at":2002072323,"username":"william"},
		{"title":"this java really cool","body":"hey java","id":"9","created_at":2002072323,"username":"william"},
		{"title":"this java really cool","body":"hey java","id":"10","created_at":2002072323,"username":"william"},
		{"title":"this java really cool","body":"hey java","id":"11","created_at":2002072323,"username":"william"},
		{"title":"this java really cool","body":"hey java","id":"12","created_at":2002072323,"username":"william"},
		{"title":"this java really cool","body":"hey java","id":"13","created_at":2002072323,"username":"william"},
		{"title":"this java really cool","body":"hey java","id":"14","created_at":2002072323,"username":"william"}
		
		]'
## 持久化数据

5. 结束索引

```
	curl -XPUT "192.168.6.35:9400/index1/_flush"
```
## 刷新索引

6. 通知搜索

```  
	curl -XPUT "192.168.6.35:9400/index1/_refresh"
```
## 搜索

7.  搜索查询
	
		curl -XGET "192.168.6.35:9400/index1/csdn/_search" -d '{"query":{"text":{"title":"java"}},"size":10,"from":1,"sort":{"created_at":"desc"}}'
		  
或者    

		  curl -XGET "192.168.6.35:9400/index1/csdn/_search" -d '{"query":{"term":{"title":"java"}},"size":10,"from":1}'
		  
