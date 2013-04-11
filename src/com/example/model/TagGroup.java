package com.example.model;

import net.csdn.annotation.validate.Validate;
import net.csdn.jpa.model.Model;

import javax.persistence.ManyToMany;
import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.list;
import static net.csdn.common.collections.WowCollections.map;
import static net.csdn.validate.ValidateHelper.presence;
import static net.csdn.validate.ValidateHelper.uniqueness;

/**
 * User: WilliamZhu
 * Date: 12-7-23
 * Time: 下午4:54
 */
public class TagGroup extends Model {

    static {
        validate("name",map(presence, map("message", "{}字段不能为空"), uniqueness, map("message", "{}字段不能重复")));
    }
    @ManyToMany
    private List<Tag> tags = list();
}
