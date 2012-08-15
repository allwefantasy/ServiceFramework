package com.example.model;

import net.csdn.annotation.validate.Validate;
import net.csdn.jpa.model.Model;

import javax.persistence.ManyToOne;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.map;
import static net.csdn.validate.ValidateHelper.Numericality.greater_than_or_equal_to;
import static net.csdn.validate.ValidateHelper.numericality;

/**
 * User: WilliamZhu
 * Date: 12-7-23
 * Time: 下午4:53
 */
public class BlogTag extends Model {

    @Validate//这个限制只是为了展示用法
    private final static Map $object_id = map(numericality, map(greater_than_or_equal_to, 2));


    @ManyToOne
    private Tag tag;
}
