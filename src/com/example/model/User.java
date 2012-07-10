package com.example.model;

import net.csdn.annotation.Validate;
import net.csdn.jpa.model.Model;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.OneToOne;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.newHashMap;
import static net.csdn.validate.ValidateHelper.*;
import static net.csdn.validate.ValidateHelper.Length.*;

/**
 * User: WilliamZhu
 * Date: 12-7-5
 * Time: 上午8:03
 */
@Entity
public class User extends Model {

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


    @OneToOne(mappedBy = "user")
    private Blog blog;
}
