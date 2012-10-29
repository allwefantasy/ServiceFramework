package net.csdn.common.scan.component;


public interface Filter {
    boolean accepts(String filename);
}
