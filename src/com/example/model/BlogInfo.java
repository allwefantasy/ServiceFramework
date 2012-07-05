package com.example.model;

import net.csdn.annotation.Validate;
import net.csdn.jpa.model.Model;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.OneToOne;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.newHashMap;
import static net.csdn.validate.ValidateHelper.*;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-5
 * Time: 上午8:03
 */
@Entity
public class BlogInfo extends Model {

    @Validate
    private final static Map $info/*需要验证的字段名 以$开始*/ =
            newHashMap(
                    /*------------------唯一性验证---------------------------*/
                    presence, newHashMap(message, "{}不能为空")
            );

    @OneToOne(mappedBy = "blog_info")
    private Blog blog;
}
