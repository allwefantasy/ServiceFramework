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


### 搭起来，跑起来

在终端下赋值黏贴运行该命令:


	git clone git://github.com/allwefantasy/ServiceFramework.git ServiceFramework

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
		<td>配置文件。整个ServiceFramework只有两个配置文件，分别为application.yml 和logging.yml  更详细的配置介绍参看:<a href="#">配置 ServiceFramework 应用</a></td>
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
		<td>单元测试目录。详细参看:<a href="#">如何测试ServiceFramework应用</a></td>
	</tr>
	
</tbody></table>

##运行测试前或者启动应用的准备工作。

- 在你的mysql中新建一个库，名称为：wow
- 运行sql目录下的 wow.sql,把所有的表建好。

这应该就是所有准备工作了。但是您的端口可能不是默认的3306,所以您还应该检查下，并且修改username和password


	config/application.yml 

文件中的

	development:
	    datasources:
	        mysql:
	           host: 127.0.0.1
	           port: 3306
	           database: wow
	           username: root
	           password: root

##如何运行测试
项目src目录下有一个com.example 示例程序。实现的是一个简单的tag系统。

在test 目录中 test.com.example 有example项目的测试代码。
test 根目录下的有个文件叫


	DynamicSuiteRunner 


你可以在IDE中启动它来运行整个测试集。

## 如何启动应用。

你可以在IDE运行


	net.csdn.bootstrap.Application 

当然你也可以写一个类继承它。然后运行这个新的类。

如果你不希望使用IDE.你可以直接进入项目，然后运行:


	./bin/run.sh start


默认开启9400端口。你可以修改config/application.yml文件来改变端口。
接着可以通过curl 进行测试访问。
举个例子:
常见一个tag_group:


	curl -XPOST 'http://127.0.0.1:9400/tag_group' -d 'name=java'


这个时候你可以查看数据库，应该就有相应的记录了。

