package org.apache.lucene.search;

import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import org.apache.lucene.index.FieldInvertState;

/**
 * User: william
 * Date: 11-10-25
 * Time: 下午4:47
 */
public class CSDNSimilarity extends  DefaultSimilarity{
   private CSLogger logger = Loggers.getLogger(getClass());
   private int distance_factor = 1;
   private int coord_factor = 1;
   /*
     忽略长度影响
   */
   @Override
   public float computeNorm(String field, FieldInvertState state) {
      return state.getBoost();
  }

    @Override
    public float sloppyFreq(int distance) {
        return 1.0f / (distance * distance_factor);
    }

    @Override
    public float coord(int overlap, int maxOverlap) {
        return overlap*coord_factor / (float)maxOverlap;
    }

    public CSDNSimilarity distance_factor(int _distance_factor){
        this.distance_factor = _distance_factor;
        return this;
    }

    public CSDNSimilarity coord_factor(int _coord_factor){
        this.coord_factor = _coord_factor;
        return this;
    }
    public int  coord_factor(){
        return this.coord_factor;
    }
    public int distance_factor(){
        return this.distance_factor;
    }

    /*
     因为分布式，所以需要返回相同的值，使得不同机器上的分数可以进行比较
    */
    @Override
    public float idf(int docFreq, int numDocs) {
        return 1.0f;
    }
}
