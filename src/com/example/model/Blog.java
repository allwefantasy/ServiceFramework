package com.example.model;

import net.csdn.annotation.Hint;
import net.csdn.annotation.NotMapping;
import net.csdn.annotation.Validate;
import net.csdn.jpa.model.Model;

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
public class Blog extends Model {


    @Validate
    private final static Map $validate = newHashMap(associated, newArrayList("blog_info"));
    @Validate
    private final static Map $user_name/*需要验证的字段名 以$开始*/ =
            newHashMap(
                    length,
                    newHashMap(
                            minimum, 2,
                            maximum, 16,
                            too_short, "{}文字太短",
                            too_long, "{}文字太长"),

                    uniqueness,
                    true
            );


    @OneToMany(mappedBy = "blog", cascade = CascadeType.ALL)
    @Hint(Article.class)
    private List<Article> articles = new ArrayList<Article>();

    @OneToOne(cascade = {CascadeType.PERSIST})
    private BlogInfo blog_info;
}
