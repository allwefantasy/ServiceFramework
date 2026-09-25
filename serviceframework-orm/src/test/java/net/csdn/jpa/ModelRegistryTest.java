package net.csdn.jpa;

import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.jpa.model.Model;
import org.junit.Test;
import test.com.william.model.BlogTag;
import test.com.william.model.Tag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ModelRegistryTest {

    @Test
    public void resolvesUniqueShortNamesAndRejectsAliasesThatCollide() {
        ModelRegistry registry = new ModelRegistry();
        registry.register(Tag.class);
        registry.register(Tag.class, Tag.class.getSimpleName());
        assertEquals(Tag.class, registry.resolve(Tag.class.getName()));
        assertEquals(Tag.class, registry.resolve("Tag"));
        assertEquals(1, registry.size());
        assertEquals(1, registry.values().size());

        registry.register(BlogTag.class, "Shared");
        registry.register(Tag.class, "Shared");
        assertEquals(2, registry.size());
        List<Class<? extends Model>> values = new ArrayList<Class<? extends Model>>(registry.values());
        assertEquals(2, values.size());
        assertEquals(2, new HashSet<Class<? extends Model>>(values).size());
        assertEquals(BlogTag.class, registry.resolve(BlogTag.class.getName()));
        try {
            registry.resolve("Shared");
            fail("ambiguous alias resolved");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.CONFLICT, failure.getCategory());
            assertEquals("resolve", failure.getPhase());
            assertTrue(failure.getMessage().contains(Tag.class.getName()));
            assertTrue(failure.getMessage().contains(BlogTag.class.getName()));
        }
        assertNull(registry.get("missing-model"));
    }
}
