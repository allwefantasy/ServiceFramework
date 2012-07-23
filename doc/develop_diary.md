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

#ServiceFramework 开发日志

##对Model层封装的思考
Hibernate 的Hql是个很酷的选择。
但是依然有缺点:

1. 和Sql类似，但是又有自己的特点。比如不适用表名而是用类名，join语法也是根据配置的orm关系进行智能判断，
  这种细小的差别常常让只有sql基础的人感到困惑。
2. Hql和Sql如此相似使得他获得足够的灵活性覆盖大部分Sql的功能，但也引入了sql所具有的一切缺点,譬如不同的人可能写出不同的sql语句,Sql 本身具有一定的复杂性。

所以解决方案就是，提供一个新的语法形式。做到：

1. 屏蔽Hql 语法。使用原生的Sql 方式，但是能使用Hql提供的高级功能以及ORM功能。
2. 规范会原生的Sql使用方式

整个设计过程基本原则是：
1. 尽量少给用户选择
2. 用一个方案覆盖80%的问题。
3. 用另一个可选方案覆盖另外20%的问题(就是使用原生的Sql)


这其实就是Ruby 语言中 Arel所提倡的方式。   
举个例子:  

```
Post.find(
    "select p from Post p, Comment c " +
    "where c.post = p and c.subject like ?", "%hop%"
);
```

这种方式就是Hql的方式。但是有什么问题吗？

1. 命名过于随意，比如Post 叫做 p,Comment 叫做 c
2. 很容易导致用户去拼接复杂字符。这是传统sql常见问题。Sql每个部分散落在代码不同的地方，这是非常恐怖的一件事情。

如果改成这个样子:   


```
Comment.select("post")
       .where("subject like ?","%hop%")
       .join("post")
       .order("id desc")
       .offset(0),
       .limit(10)
       .fetch();
```

有效的利用了代码提示，比如select,where 等方法.关键是结构非常的清晰。并且可以轻易实现named_scope