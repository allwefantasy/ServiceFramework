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

1. hql与sql相似却又有不同之处。这种细微差别会导致不熟悉hql语法的人困惑。而且，Hql提供的一些高级语法容易让人陷入性能陷阱，虽然它们看起来很酷
2. hql具有sql所具有所有缺点。hql语句很容易被程序员肢解到程序的各个角落，天哪，看了半天都不知道完整的hql语句会是什么样子的。

解决方案：

1. 简化hql语法，使得hql更加像sql.譬如hql通常需要别名，为什么我可以id=1非要写成blog.id=1呢？
2. 提供一个统一的调用方式，使得sql语句规范化

整个设计过程基本原则是：

1. 尽量少给用户选择
2. 用一个方案覆盖80%的问题(简化后的hql)
3. 用另一个可选方案覆盖另外20%的问题(就是使用原生的Sql)


note: 这其实就是Ruby 语言中 Arel所提倡的方式。   
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
       .fetch();
```

有效的利用了代码提示，比如select,where 等方法.关键是结构非常的清晰。并且可以轻易实现named_scope.
以博客为例，我们可以在 Blog模型类中定义这么一个方法:

```
 public final static JPQL activeBlogs() {
        return where("status=:status", map("status", Status.active.value));
    }
```

之后就可以这样调用:     

```
Blog.activeBlogs().where("title=:title",map("title","yes")).fetch();
```
这样就可以获取标题为yes,并且是激活的博客。


##对Controller层封装的思考

Php之前的函数式编程有一个很大的优点，就是你按流程调用一定数量的函数，基本就能把逻辑走下来。Java一直缺乏这方面的觉悟。比如一个 isEmpty判断，你要么自己写个工具类，要嘛调用StringUtils(apache commons里)的。
总之不方便，天哪，为啥不能直接这么用:

```
if (!isEmpty(param("channelIds"))) {
   …..
}
```
所以，一个用起来清爽的Controller应该是，你一旦继承了父类，就能够从父类获得大量的有用的方法，比如join,isEmpty,map,list等。
我甚至在想，我们应该实现一套PHP的函数库放到ApplicationController中。继承了它，你就获得了一个函数库。