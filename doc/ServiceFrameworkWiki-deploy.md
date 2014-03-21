
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

# ServiceFramework Wiki

## ServiceFramework 项目部署

ServiceFramework 自带了http server(jetty)。 内部很多项目接口调用量在千万级，
目前没有发现jetty存在任何性能问题。

在bin目录下，有两个文件

    config
    deploy.sh

要通过脚本启动你的项目，提供http服务，需要做如下几个步骤：

1. 打开bin/deploy.sh,修改21行的project name.你的项目名称(或者代号)。比如示例中是alpaca。
2. 24行的 S_CONFIGURATION_HOME 你的配置文件config所在目录。通常真实部署的时候，我们不会使用项目中的
   config目录,而是将project 中得config目录链接到新固定的生产环境中的配置文件
3. 打开bin/config 文件. 设置启动类，以及配置文件application.xml的名称。
4. mvn dependency:copy-dependencies

接着你就可以使用deploy脚本进行部署了：

   ./deploy.sh deploy   #形成部署目录
   ./deploy.sh start    #启动应用
   ./deploy.sh stop     #关闭应用
   ./deploy.sh restart  #启动应用
   ./deploy.sh rollback #回滚。比如你这次部署的代码有问题，你可以通过这个命令回滚到上一次部署。流程是先stop,rolback,start

本质是上就是通过脚本运行一个Java类，你完全可以自定义一个启动脚本。









	
	


