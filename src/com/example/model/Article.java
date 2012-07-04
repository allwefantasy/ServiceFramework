package com.example.model;

import net.csdn.annotation.NotMapping;
import net.csdn.annotation.Validate;
import net.csdn.jpa.model.GenericModel;

import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.newHashMap;
import static net.csdn.validate.ValidateHelper.*;


@Entity
@NotMapping({"blog_id"})
public class Article extends GenericModel {
    @ManyToOne
    @JoinColumn(name = "blog_id")
    private Blog blog;

    @Validate
    private final static Map $content =
            newHashMap(
                    presence, newHashMap(message, "{}不能为空")
            );

}
