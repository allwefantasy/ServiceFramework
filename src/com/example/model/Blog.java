package com.example.model;

import net.csdn.annotation.Validate;
import net.csdn.jpa.model.GenericModel;
import net.csdn.validate.ValidateHelper;

import javax.persistence.Entity;
import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.newHashMap;
import static net.csdn.validate.ValidateHelper.*;
import static net.csdn.validate.ValidateHelper.Length.*;
import static net.csdn.validate.ValidateHelper.Numericality.greater_than;
import static net.csdn.validate.ValidateHelper.Numericality.less_than;
import static net.csdn.validate.ValidateHelper.Numericality.odd;

/**
 * User: WilliamZhu
 * Date: 12-7-1
 * Time: 下午2:25
 */
@Entity
public class Blog extends GenericModel {

    /*
    <p>真正易用的验证框架不应该在字段上添加一大堆的注解。应该类似下面这种申明式的
    使用方式。同时这也保证了你可以不需要在模型类添加任何字段。所有映射数据库的字段由系统
    自动生成</p>
     <code>
     validates :content, :length => {
    :minimum   => 300,
    :maximum   => 400,
    :too_short => "must have at least %{count} words",
    :too_long  => "must have at most %{count} words"
    }
     </code>
     */
    @Validate
    private static Map $content/*需要验证的字段名 以$开始*/ =
            newHashMap(

                    /*------------length:长度--------------------------*/
                    length,
                    newHashMap(
                            minimum, 300,
                            maximum, 400,
                            too_short, "文字太短",
                            too_long, "文字太长"),

                    /*-----------presence:不能为null或者空--------------------*/
                    presence,
                    newHashMap(message, "{}不能为空"),

                    /*---------------------------------------------*/
                    uniqueness,
                    true
            );


    @Validate
    private static Map $id/*需要验证的字段名 以$开始*/ =
            newHashMap(

                    /*------------numericality:类型验证---------------*/
                    numericality,
                    newHashMap(
                            less_than, 4,
                            odd, true
                    ),
                    /*------------presence:不能为null或者空---------------------*/
                    presence,
                    newHashMap(message, "{}不能为空")
            );


}
