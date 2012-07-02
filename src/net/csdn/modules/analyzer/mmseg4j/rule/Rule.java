package net.csdn.modules.analyzer.mmseg4j.rule;

import net.csdn.modules.analyzer.mmseg4j.Chunk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 过虑规则的抽象类。
 *
 * @author chenlb 2009-3-16 上午11:35:06
 */
public abstract class Rule {

    protected List<Chunk> chunks;

    public void addChunks(List<Chunk> chunks) {
        for (Chunk chunk : chunks) {
            addChunk(chunk);
        }
    }

    /**
     * 添加 chunk
     *
     * @throws NullPointerException, if chunk == null.
     * @author chenlb 2009-3-16 上午11:34:17
     */
    public void addChunk(Chunk chunk) {
        chunks.add(chunk);
    }

    /**
     * @return 返回规则过虑后的结果。
     * @author chenlb 2009-3-16 上午11:33:10
     */
    public List<Chunk> remainChunks() {
        for (Iterator<Chunk> it = chunks.iterator(); it.hasNext(); ) {
            Chunk chunk = it.next();
            if (isRemove(chunk)) {
                it.remove();
            }
        }
        return chunks;
    }

    /**
     * 判断 chunk 是否要删除。
     *
     * @author chenlb 2009-3-16 上午11:33:30
     */
    protected abstract boolean isRemove(Chunk chunk);

    public void reset() {
        chunks = new ArrayList<Chunk>();
    }
}
