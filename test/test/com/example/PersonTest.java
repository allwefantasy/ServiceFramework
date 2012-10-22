package test.com.example;

import com.example.document.Address;
import com.example.document.Person;
import junit.framework.Assert;
import net.csdn.junit.IocTest;
import net.csdn.mongo.Document;
import org.junit.Test;

import java.util.List;

import static net.csdn.common.collections.WowCollections.list;
import static net.csdn.common.collections.WowCollections.map;

/**
 * User: WilliamZhu
 * Date: 12-10-18
 * Time: 上午11:36
 */
public class PersonTest extends IocTest {

    @Test
    public void testDocumentHasManyAssociation() {
        Person person = Person.create(map(
                "_id", 100,
                "name", "google",
                "bodyLength", 10
        ));

        person.addresses().build(map("location", "天国的世界"));
    }

    @Test
    public void testDocumentEnhancer() {

        Assert.assertTrue(Person.collectionName().equals("persons"));
        Assert.assertTrue(Address.collectionName().equals("addresses"));
        Assert.assertTrue(Document.collectionName() == null);
        Assert.assertTrue(Document.collection() == null);

        Person person = new Person();
        person.setName("我是天才");
        Assert.assertTrue("我是天才".equals(person.getName()));

        person = Person.create(map(
                "_id", 100,
                "name", "google",
                "bodyLength", 10
        ));
        person.save();

        Person personFound = Person.findById(100);
        Assert.assertTrue(personFound != null);
        Assert.assertTrue(personFound.attributes().get("name").equals("google"));

        List<Person> personList = Person.where(map("name", "google")).fetch();
        Assert.assertTrue(personList.size() == 1);
        Assert.assertTrue(personList.get(0).getName().equals("google"));


        personList = Person.select(list("name")).where(map("name", "google")).fetch();
        Assert.assertTrue(personList.size() == 1);
        Assert.assertTrue(personList.get(0).attributes().toMap().size() == 2);

        personFound.remove();
        personFound = Person.findById(100);
        Assert.assertTrue(personFound == null);


    }
}
