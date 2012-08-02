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

对于数据库等的配置是区分开发或者生产环境的