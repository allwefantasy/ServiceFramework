package test.com.william.document;

import net.csdn.common.exception.AutoGeneration;
import net.csdn.mongo.Document;
import net.csdn.mongo.association.Options;
import net.csdn.mongo.embedded.AssociationEmbedded;

import static net.csdn.common.collections.WowCollections.map;

/**
 * User: WilliamZhu
 * Date: 12-10-24
 * Time: 上午11:35
 */
public class Article extends Document {
    static {
        storeIn("articles");
        belongsToEmbedded("blog", new Options(map(
                Options.n_kclass, Blog.class
        )));
    }

    public AssociationEmbedded blog() {
        throw new AutoGeneration();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String title;
    public String body;
}
