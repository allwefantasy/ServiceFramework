package net.csdn.jpa.model;

import net.csdn.common.exception.AutoGeneration;
import net.csdn.jpa.exception.JPAQueryException;
import net.csdn.modules.persist.mysql.MysqlClient;

import javax.persistence.Query;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-26
 * Time: 下午9:53
 */
public class Model extends JPABase {

    public static List<Map> findBySql(String sql, Object... params) {
        //TODO:竟然在这里使用了  ServiceFramwork.injector 疯掉了....  去掉，去掉....
        return mysqlClient.query(sql, params);
    }

    public static MysqlClient nativeSqlClient() {
        //TODO:竟然在这里使用了  ServiceFramwork.injector 疯掉了....  去掉，去掉....
        return mysqlClient.defaultMysqlService();
    }

    //----------------------------------------------------------------------------------

    public static <T extends JPABase> T find(Integer id) {
        throw new AutoGeneration();
    }

    public static <T extends JPABase> T find(List ids) {
        throw new AutoGeneration();
    }

    public static JPQL where(String cc) {
        throw new AutoGeneration();
    }

    public static JPQL where(String cc, Map params) {
        throw new AutoGeneration();
    }


    public static JPQL select(String cc) {
        throw new AutoGeneration();
    }

    public static JPQL joins(String cc) {
        throw new AutoGeneration();
    }

    public static JPQL order(String cc) {
        throw new AutoGeneration();
    }

    public static JPQL limit(int cc) {
        throw new AutoGeneration();
    }

    public static JPQL offset(int cc) {
        throw new AutoGeneration();
    }

    //----------------------------------------------------------------------------------

    public static <T extends JPABase> T create(Map params) {
        throw new AutoGeneration();
    }

    /**
     * Count entities
     *
     * @return number of entities of this class
     */
    public static long count() {
        throw new AutoGeneration();
    }

    /**
     * Count entities with a special query.
     * Example : Long moderatedPosts = Post.count("moderated", true);
     *
     * @param query  HQL query or shortcut
     * @param params Params to bind to the query
     * @return A long
     */
    public static long count(String query, Object... params) {
        throw new AutoGeneration();
    }

    /**
     * Find all entities of this type
     */
    public static <T extends JPABase> List<T> findAll() {
        throw new AutoGeneration();
    }

    /**
     * Find the entity with the corresponding id.
     *
     * @param id The entity id
     * @return The entity
     */
    public static <T extends JPABase> T findById(Object id) {
        throw new AutoGeneration();
    }

    /**
     * Prepare a query to find entities.
     *
     * @param query  HQL query or shortcut
     * @param params Params to bind to the query
     * @return A JPAQuery
     */
    public static JPAQuery find(String query, Object... params) {
        throw new AutoGeneration();
    }

    /**
     * Prepare a query to find *all* entities.
     *
     * @return A JPAQuery
     */
    public static JPAQuery all() {
        throw new AutoGeneration();
    }

    /**
     * Batch delete of entities
     *
     * @param query  HQL query or shortcut
     * @param params Params to bind to the query
     * @return Number of entities deleted
     */
    public static int delete(String query, Object... params) {
        throw new AutoGeneration();
    }

    /**
     * Delete all entities
     *
     * @return Number of entities deleted
     */
    public static int deleteAll() {
        throw new AutoGeneration();
    }

    public static void transaction() {

    }

    public static class JPAQuery {

        public Query query;
        public String sq;

        public JPAQuery(String sq, Query query) {
            this.query = query;
            this.sq = sq;
        }

        public JPAQuery(Query query) {
            this.query = query;
            this.sq = query.toString();
        }

        public <T> T first() {
            try {
                List<T> results = query.setMaxResults(1).getResultList();
                if (results.isEmpty()) {
                    return null;
                }
                return results.get(0);
            } catch (Exception e) {
                throw new JPAQueryException("Error while executing query <strong>" + sq + "</strong>", e.getCause());
            }
        }

        /**
         * Bind a JPQL named parameter to the current query.
         * Careful, this will also bind count results. This means that Integer get transformed into long
         * so hibernate can do the right thing. Use the setParameter if you just want to set parameters.
         */
        public JPAQuery bind(String name, Object param) {
            if (param.getClass().isArray()) {
                param = Arrays.asList((Object[]) param);
            }
            if (param instanceof Integer) {
                param = ((Integer) param).longValue();
            }
            query.setParameter(name, param);
            return this;
        }

        /**
         * Set a named parameter for this query.
         */
        public JPAQuery setParameter(String name, Object param) {
            query.setParameter(name, param);
            return this;
        }

        /**
         * Retrieve all results of the query
         *
         * @return A list of entities
         */
        public <T> List<T> fetch() {
            try {
                return query.getResultList();
            } catch (Exception e) {
                throw new JPAQueryException("Error while executing query <strong>" + sq + "</strong>", e.getCause());
            }
        }

        /**
         * Retrieve results of the query
         *
         * @param max Max results to fetch
         * @return A list of entities
         */
        public <T> List<T> fetch(int max) {
            try {
                query.setMaxResults(max);
                return query.getResultList();
            } catch (Exception e) {
                throw new JPAQueryException("Error while executing query <strong>" + sq + "</strong>", e.getCause());
            }
        }

        /**
         * Set the position to start
         *
         * @param position Position of the first element
         * @return A new query
         */
        public <T> JPAQuery from(int position) {
            query.setFirstResult(position);
            return this;
        }

        /**
         * Retrieve a page of result
         *
         * @param page   Page number (start at 1)
         * @param length (page length)
         * @return a list of entities
         */
        public <T> List<T> fetch(int page, int length) {
            if (page < 1) {
                page = 1;
            }
            query.setFirstResult((page - 1) * length);
            query.setMaxResults(length);
            try {
                return query.getResultList();
            } catch (Exception e) {
                throw new JPAQueryException("Error while executing query <strong>" + sq + "</strong>", e.getCause());
            }
        }
    }

}
