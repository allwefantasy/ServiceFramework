package test.com.example.document;

import net.csdn.common.exception.AutoGeneration;
import net.csdn.mongo.Document;
import net.csdn.mongo.association.Association;
import net.csdn.mongo.association.Options;

import static net.csdn.common.collections.WowCollections.map;

/**
 * User: WilliamZhu
 * Date: 12-10-18
 * Time: 下午2:50
 */
public class Address extends Document {
    static {
        storeIn("addresses");

        belongsTo("person", new Options(
                map(
                        Options.n_kclass, Person.class,
                        Options.n_foreignKey, "person_id"
                )

        ));
    }

    public Association person() {
        throw new AutoGeneration();
    }


    private String location;

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
