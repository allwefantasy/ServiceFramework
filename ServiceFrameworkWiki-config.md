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

### 配置文件

ServiceFramework 所有的配置文件位于config目录下。其实只有两个配置文件，一个application.yml,
一个logging.yml.分别配置应用和日志。

一个完整的application.yml

```
#mode
mode:
  development
#mode=production

###############datasource config##################
#mysql,mongodb,redis等数据源配置方式
development:
    datasources:
        mysql:
           host: 127.0.0.1
           port: 3306
           database: tag_engine
           username: tag
           password: tag
           disable: true
        mongodb:
           host: 127.0.0.1
           port: 27017
           database: tag_engine
        redis:
            host: 127.0.0.1
            port: 6379

production:
    datasources:
        mysql:
           host: 127.0.0.1
           port: 3306
           database: tag_engine
           username: tag
           password: tag
        mongodb:
           host: 127.0.0.1
           port: 27017
           database: tag_engine
        redis:
            host: 127.0.0.1
            port: 6379

orm:
    show_sql: true
    pool_min_size: 5
    pool_max_size: 10
    timeout: 300
    max_statements: 50
    idle_test_period: 3000
###############application config##################
application:
    controller: com.example.controller  
    model:      com.example.model
    service:    com.example.service
    util:       com.example.util
    test:       test.com.example


###############http config##################
http:
    port: 9400



###############validator config##################
#如果需要添加验证器，只要配置好类全名即可
#替换验证器实现，则替换相应的类名即可
#warning: 自定义验证器实现需要线程安全

validator:
   format:        net.csdn.validate.impl.Format
   numericality:  net.csdn.validate.impl.Numericality
   presence:      net.csdn.validate.impl.Presence
   uniqueness:    net.csdn.validate.impl.Uniqueness
   length:        net.csdn.validate.impl.Length
   associated:    net.csdn.validate.impl.Associated

################ 数据库类型映射 ####################
type_mapping:  net.csdn.jpa.type.impl.MysqlType

```

对于数据库等的配置默认区分开发，生产，测试。单元测试强制使用测试环境。

除了默认的的一些配置，你可以随意按标准的yaml格式添加配置，在实际代码中，你可以通过下面的方式获取
配置

首先注入Settings类

      @Inject
      private Settings settings;

然后就可以如下使用了:

      boolean enable = settings.getAsBoolean("foo.bar.yes",false)


在ServiceFramework中，Controller,Service,Util，Model等是需要在配置文件中明确指定的。类似配置如下：

      application:
          controller: com.example.controller  
          model:      com.example.model
          service:    com.example.service
          util:       com.example.util
          test:       test.com.example


理论上，ServiceFramework的配置就是数据库配置类的扫描路径配置。


