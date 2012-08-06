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

table {
margin: 0 0 1.5em;
border: 2px solid #CCC;
background: white;
border-collapse: collapse;
}
</style>

#ServiceFramework Wiki

##  创建一个新的ServiceFramework 项目


###ServiceFramework 适合你吗？

ServcieFramework 定位在**移动互联网后端**。
所以ServcieFramework非常强调开发的高效性，其开发效率可以比肩Rails(不相信？可以体验一下哦)。

1. 拥有Java界最简单，非常高效，且规范的Model层
2. Controller层有非常简洁的验证器，过滤器
3. 简单但实用的View层，天然支持JSON,XMl格式输出

框架已经提供了对mysql,mongodb,redis的支持

如果你面对的是一个遗留项目或者遗留的数据库，那么ServiceFramework不适合你。我们倾向于一个全新的项目中使用它。相信你会为Java也能做到如此的简洁而惊讶，如此高效的开发而窃喜。

现在让我们开始 ServiceFramework 十五分钟旅程吧。



### 搭起来，跑起来

在终端下赋值黏贴运行该命令:

git clone https://github.com/service_framework/service_framework.git tag_engine

此时你就获得一个开箱即用的项目。所有的目录和结构都是规范化的。

####我们先看看目录结构:

<table>
	<tbody><tr>
		<th>文件/目录</th>
		<th>作用</th>
	</tr>
	<tr>
		<td>src/</td>
		<td>包含 controllers, models, views。也就是项目源码的存放地。 在之后的教程中，我们会聚焦于这个目录</td>
	</tr>
	<tr>
		<td>config/</td>
		<td>配置文件。整个ServiceFramework只有两个配置文件，分别为application.yml 和logging.yml  更详细的配置介绍参看:<a href="configuring.html">配置 ServiceFramework 应用</a></td>
	</tr>
	<tr>
		<td>bin</td>
		<td>存放编译，部署，运行脚本</td>
	</tr>
	<tr>
		<td>sql/</td>
		<td>项目的数据库结构文件。通常是sql文件</td>
	</tr>
	<tr>
		<td>doc/</td>
		<td>项目的文档存放地</td>
	</tr>
	<tr>
		<td>lib</td>
		<td>应用本身，以及包括ServiceFramework依赖的jar包都会存放在这里</td>
	</tr>
	
	<tr>
		<td>logs/</td>
		
		<td>应用程序日志文件</td>
	</tr>
	<tr>
		<td>script/</td>
		<td>一些shell脚本之类的</td>
	</tr>
	<tr>
		<td>client</td>
		<td>你可以写一些客户端，比如使用某种脚本语言，做数据迁移啥的</td>
	</tr>
	<tr>
		<td><span class="caps">README</span>.html</td>
		<td>请对你的项目做一个简要的介绍</td>
	</tr>
	
	<tr>
		<td>test/</td>
		<td>单元测试目录。详细参看:<a href="testing.html">如何测试ServiceFramework应用</a></td>
	</tr>
	
</tbody></table>

项目在src目录下有一个com.example 示例程序。实现的是一个简单的tag系统。
在test 中的 test.com.example 有example项目的测试代码。
你可以在IDE环境运行 test 根目录下的

DynamicSuiteRunner 文件。

你可以可以运行 net.csdn.bootstrap.Application
启动HttpServer.
然后通过curl 进行访问。

这里我们会实现一个Tag系统来展示如何使用ServiceFrameWork 开发是如何的高效。

