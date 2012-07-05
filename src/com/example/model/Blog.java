package com.example.model;

import net.csdn.annotation.Hint;
import net.csdn.annotation.NotMapping;
import net.csdn.annotation.Validate;
import net.csdn.jpa.model.Generic;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.newArrayList;
import static net.csdn.common.collections.WowCollections.newHashMap;
import static net.csdn.validate.ValidateHelper.*;
import static net.csdn.validate.ValidateHelper.Length.*;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-1
 * Time: 下午2:25
 */
@Entity
@NotMapping({"blog_info_id"})
public class Blog extends Generic {


    @Validate
    private final static Map $articles = newHashMap(
            associated,
            newArrayList(
                    "blog_info"
            )
    );

    @Validate
    private final static Map $user_name/*需要验证的字段名 以$开始*/ =
            newHashMap(

                    /*------------user_name 长度限制--------------------------*/
                    length,
                    newHashMap(
                            minimum, 2,
                            maximum, 16,
                            too_short, "{}文字太短",
                            too_long, "{}文字太长"),

                    /*------------------唯一性验证---------------------------*/
                    uniqueness,
                    true
            );


    @OneToMany(mappedBy = "blog", cascade = CascadeType.ALL)
    @Hint(Article.class)
    private List<Article> articles = new ArrayList<Article>();

    @OneToOne(cascade = {CascadeType.ALL})
    private BlogInfo blog_info;
    /*
    这个方法会自动生成。如果你定义了的话，则使用你定义的 ，调用方式：
    JPABase model = blog.m("articles")
    */
//    public Article articles() {
//        Article article = new Article();
//        article.attr("blog", this);
//        articles.add(article);
//        return article;
//    }
}
