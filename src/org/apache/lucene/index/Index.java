package org.apache.lucene.index;

/**
 * User: william
 * Date: 11-9-13
 * Time: 下午2:35
 */
public class Index {
    private String name;

    private Index() {

    }

    public Index(String name) {
        this.name = name.intern();
    }

    public String name() {
        return this.name;
    }

    public String getName() {
        return name();
    }

    @Override public String toString() {
        return "Index [" + name + "]";
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Index index1 = (Index) o;

        if (name != null ? !name.equals(index1.name) : index1.name != null) return false;

        return true;
    }

    @Override public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }
}
