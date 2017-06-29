package test.com.william.document;

import net.csdn.common.exception.AutoGeneration;
import net.csdn.mongo.Document;
import net.csdn.mongo.annotations.BeforeSave;
import net.csdn.mongo.association.Association;
import net.csdn.mongo.association.Options;

import static net.csdn.common.collections.WowCollections.map;

/**
 * User: WilliamZhu
 * Date: 12-10-18
 * Time: 上午10:31
 */
public class Person extends Document {
    static {
        storeIn("persons");
        hasMany("addresses", new Options(map(
                Options.n_kclass, Address.class,
                Options.n_foreignKey, "person_id"
        )));

        hasOne("idcard", new Options(map(
                Options.n_kclass, IdCard.class,
                Options.n_foreignKey, "person_id"
        )));
    }

    @BeforeSave
    public void beforeSave() {
        System.out.println(this.getName() + " will be saved");
    }

    public Association addresses() {
        throw new AutoGeneration();
    }

    public Association idcard() {
        throw new AutoGeneration();
    }


    private String name;
    private Integer bodyLength;


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getBodyLength() {
        return bodyLength;
    }

    public void setBodyLength(Integer bodyLength) {
        this.bodyLength = bodyLength;
    }
}
