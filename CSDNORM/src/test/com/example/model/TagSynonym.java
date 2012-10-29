package test.com.example.model;

import net.csdn.annotation.validate.Validate;
import net.csdn.common.exception.AutoGeneration;
import net.csdn.jpa.association.Association;
import net.csdn.jpa.model.Model;

import javax.persistence.OneToMany;
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
public class TagSynonym extends Model {

    @Validate
    private final static Map $name = map(presence, map("message", "{}字段不能为空"), uniqueness, map("message", "{}字段不能重复"));

    @OneToMany
    private List<Tag> tags = list();

    public Association tags() {
        throw new AutoGeneration();
    }
}
