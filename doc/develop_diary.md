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

##为啥要开发这个玩意

09年的时候做了一个类似于原创音乐基地的音乐类网站。用的是SSH2开发的。当时没用好，代码臃肿，速度缓慢。这给我印象非常深刻。10年接触了Rails，见识了开发速度。后来一直从事搜索方面的开发，对Web类应用便接触的少了。
12年因为内部一个框架，让我从新开始对Java Web框架进行了思考。我总结出一个框架应该至少提供下面几个特点:

1. 极度易用的ORM,规范化的数据库操作。大部分Web应用都是基于数据库的。核心代码也基本都是基于数据库的处理。
2. Web层需要获得足够多的辅助函数来极度简化你的代码
3. 完整易用的IOC容器，组织并且规范整个代码结构
4. 简单清晰的应用配置，集中，简单，配置自解释，无需写专门的配置文档

实现了上面四点，之后再谈框架功能的全面性。


##对Model层封装的思考
#### 与传统Java model层的缺陷
Hibernate 的Hql是个很酷的选择。
但是依然有缺点:

1. hql与sql相似却又有不同之处。这种细微差别会导致不熟悉hql语法的人困惑。而且，Hql提供的一些高级语法容易让人陷入性能陷阱，虽然它们看起来很酷
2. hql具有sql所具有所有缺点。hql语句很容易被程序员肢解到程序的各个角落，天哪，看了半天都不知道完整的hql语句会是什么样子的。
3. 有时候拼接Sql是无法避免的，那么我们也要使得拼接变得规范起来。一看就知道完整的Sql语句会是什么样字。
4. Hibernate 本身提供了过多的可选项。代码可能忽而用hql,忽而用critiria,忽而用原生sql。体系实现过于复杂，导致很多人只是简单拿hibenrate 当做一个单pojo的curd工具。

解决方案：

1. 简化hql语法，使得hql更加像sql.譬如hql通常需要别名，为什么我可以用id=1非要写成blog.id=1呢？
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

1. 命名过于随意，比如Post 叫做 p,Comment 叫做 c。并且，大部分情况你根本不需要写别名
2. 很容易导致用户去拼接复杂字符。这是传统sql常见问题。Sql每个部分散落在代码不同的地方，这是非常恐怖的一件事情。

如果改成这个样子:   


```
Comment.select("post")
       .where("subject like ?","%hop%")
       .join("post")
       .fetch();
```

有效的利用了代码提示，比如select,where 等方法.关键是结构非常的清晰。轻易实现named_scope.
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

### ServiceFrame Model层开发
这种链式调用就是ServiceFramework推荐的方式。
假设我们要开发一个标签系统,具有四个类(表):

标签

```
package com.example.model;

import net.csdn.annotation.Validate;
import net.csdn.jpa.model.Model;

import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import java.util.*;

import static net.csdn.common.collections.WowCollections.map;
import static net.csdn.validate.ValidateHelper.presence;
import static net.csdn.validate.ValidateHelper.uniqueness;

/**
 * User: WilliamZhu
 * Date: 12-7-23
 * Time: 下午4:52
 */
@Entity
public class Tag extends Model {
    @Validate
    private final static Map $name = map(presence, map("message", "{}字段不能为空"), uniqueness, map("message", "{}字段不能重复"));


    @ManyToOne
    private TagSynonym tag_synonym;

    @OneToMany(mappedBy = "tag")
    private List<BlogTag> blog_tags = new ArrayList<BlogTag>();

    @ManyToMany
    private List<TagGroup> tag_groups = new ArrayList<TagGroup>();


    public static Set<String> synonym(String wow_names) {
        String[] names = wow_names.split(",");
        //可以改为Set?
        Set<String> temp = new HashSet<String>();
        for (String name : names) {
            Tag tag = Tag.where("name=:name", map("name", name)).single_fetch();
            if (tag == null) continue;
            List<Tag> tags = Tag.where("tag_synonym=:tag_synonym", map("tag_synonym", tag.tag_synonym)).fetch();
            for (Tag tag1 : tags) {
                temp.add(tag1.attr("name", String.class));
            }

        }
        return temp;
    }

}

```

博客与标签关联表


```    
package com.example.model;

import net.csdn.jpa.model.Model;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;

/**
 * User: WilliamZhu
 * Date: 12-7-23
 * Time: 下午4:53
 */
@Entity
public class BlogTag extends Model {

    @ManyToOne(cascade = CascadeType.PERSIST)
    private Tag tag;
}
```

标签组:

```
package com.example.model;

import net.csdn.jpa.model.Model;

import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import java.util.ArrayList;
import java.util.List;

/**
 * User: WilliamZhu
 * Date: 12-7-23
 * Time: 下午4:54
 */
@Entity
public class TagGroup extends Model {
    @ManyToMany(mappedBy = "tag_groups")
    private List<Tag> tags = new ArrayList<Tag>();
}
```


标签同义词

```
package com.example.model;

import net.csdn.jpa.model.Model;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

/**
 * User: WilliamZhu
 * Date: 12-7-23
 * Time: 下午4:54
 */
@Entity
public class TagSynonym extends Model {
    @OneToMany(mappedBy = "tag_synonym", cascade = CascadeType.PERSIST)
    private List<Tag> tags = new ArrayList<Tag>();
}
```

我们看到，只需要在各个Model里配置关联关系即可，不需要写大量的属性。
有几个点值得注意:

1. 让一个类成为Model的必要条件是: 继承自Model接口，使用@Entity标注
2. TagSynonym 在数据库的表名就是TagSynonym.这是他们之间的命名规范。
3. 关联关系会涉及到关联字段(外键)的命名。以Tag和TagSynonym为例，他们之间是多对一的关系。Tag表中
   含有指向TagSynonym的字段。Tag类中含有tag_synonym 字段，那么数据库中对应的字段为tag_synonym_id.
4. Model类字段命名规则是采用小写，连字符为"_". 数据库字段和类字段名保持一致


#### 校验器
Tag含有一个模型校验器

```
 @Validate
 private final static Map $name = map(
			 presence, map("message", "{}字段不能为空"), 
			 uniqueness, map("message", "{}字段不能重复")
 );

```

成为校验器必须声明为private final,并且添加@Validate 申明

$name 表明 字段 "name" 需要校验。
presence 表明 name 字段必须存在，不能为空或者null
uniqueness 表明 name 字段需要在数据库唯一

你可以通过

```
  if (!model.save()) {
                render(HTTP_400, model.validateResults);
            }
```
save() 返回false 说明数据不会被保存。
你也可以显示调用

```
  model.valid()
```
方法来判断是否通过校验。

获得校验结果的方式是

```
model.validateResults
```

这是一个List集合对象。包含ValidateResult对象。


#### 模型关联关系
 采用标准java JPA 标准。当然，如果你没有类似的基础也没有关系。在ServiceFramework中关联关系非常简单。
 我们推荐你只使用下面三种关系:
 
 1. 一对多
 2. 多对多
 3. 一对一
 
 我们推荐你用下面的方式设计模型:
 
 1. 完全按照命名约定去设计数据库和模型类。
 2. 关联关系参数只设置 mappedBy 和 cascade.原则上在OneToMany中设置mappedBy.OneToOne和ManyToMany你可以随机设置


##对Controller层封装的思考

PHP之类的函数式编程语言(当然，从5.0开始也是面向对象了)有一个很大的优点，就是你按流程调用一定数量的函数，基本就能把逻辑走下来。Java一直缺乏这方面的觉悟。比如一个 isEmpty判断，你要么自己写个工具类，要嘛调用StringUtils(apache commons里)的。
总之不方便，天哪，为啥不能直接这么用，在你写代码的时候，你只是习惯性的调用isEmpty,然后你惊奇的发现框架已经给你提供好了，无需任何包的导入!

```
if (!isEmpty(param("channelIds"))) {
   …..
}
```
所以，一个用起来清爽的Controller应该是，你一旦继承了父类，就能够从父类获得大量的有用的方法，比如join,isEmpty,map,list等。
我甚至在想，我们应该实现一套PHP的函数库放到ApplicationController中。继承了它，你就获得了一个函数库。

## 应用的组织
ServiceFramewrok 天然分为四部分:

* controller
* service
* util
* model

我们的目标是给你一个最佳的实践来组织你的项目结构。让你不用烦心代码应该如何放置。
你可以在配置文件配置他们的位置。典型配置为:

```
###############application config##################
application:
    controller: com.example.controller
    model:      com.example.model
    service:    com.example.service
    util:       com.example.util

```

同是这样也获得另外一个好处，框架可以为根据你的类的不同作用进行相应的代码增强。便于你获得更多的可用功能

## 测试 测试!
我们不要一遍又一遍的刷浏览器。相信我，测试会让你的代码更




