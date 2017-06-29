package test.com.william.document;

import net.csdn.common.exception.AutoGeneration;
import net.csdn.mongo.Document;
import net.csdn.mongo.association.Association;
import net.csdn.mongo.association.Options;

import static net.csdn.common.collections.WowCollections.map;

/**
 * User: WilliamZhu
 * Date: 12-10-22
 * Time: 下午9:27
 */
public class IdCard extends Document {
    static {
        storeIn("idcards");
        belongsTo("person", new Options(map(
                Options.n_kclass, Person.class,
                Options.n_foreignKey, "person_id"
        )));
    }


    public Association person() {
        throw new AutoGeneration();
    }


    private String idNumbers;

    public String getIdNumbers() {
        return idNumbers;
    }

    public void setIdNumbers(String idNumbers) {
        this.idNumbers = idNumbers;
    }
}
