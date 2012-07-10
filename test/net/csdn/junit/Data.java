package net.csdn.junit;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-20
 * Time: 上午10:39
 */
public class Data extends JettyTest {
    protected String mapping() {
        return "{\"csdn\" : {\n" +
                "        \"_source\" : { \"enabled\" : false },\n" +
                "        \"properties\" : {\n" +
                "            \"title\":           {\"type\" : \"string\",\"term_vector\":\"with_positions_offsets\",\"boost\":2.0},\n" +
                "            \"body\":            {\"type\" : \"string\",\"term_vector\":\"with_positions_offsets\"},\n" +
                "            \"username\":        {\"type\" : \"string\",\"index\":\"not_analyzed\",\"store\":\"no\"},\n" +
                "            \"id\" :             {\"type\" : \"integer\",\"index\":\"not_analyzed\",\"include_in_all\":false},\n" +
                "            \"created_at\" :     {\"type\" : \"integer\",\"index\":\"not_analyzed\",\"include_in_all\":false}\n" +
                "        }}}";
    }

    protected String data() {
        String data = "[\n" +
                "{\"title\":\"java 是好东西\",\"body\":\"hey java\",\"id\":\"1\",\"username\":\"jack\",\"created_at\":2007072323},\n" +
                "{\"title\":\"this java cool\",\"body\":\"hey java\",\"id\":\"2\",\"created_at\":2009072323,\"username\":\"robbin\"},\n" +
                "{\"title\":\"this is java cool\",\"body\":\"hey java\",\"id\":\"3\",\"created_at\":2010072323,\"username\":\"www\"},\n" +
                "{\"title\":\"java is really cool\",\"body\":\"hey java\",\"id\":\"4\",\"created_at\":2007062323,\"username\":\"google\"},\n" +
                "{\"title\":\"this is wakak cool\",\"body\":\"hey java\",\"id\":\"5\",\"created_at\":2007062323,\"username\":\"jackde\"},\n" +
                "{\"title\":\"this is java cool\",\"body\":\"hey java\",\"id\":\"6\",\"created_at\":2007012323,\"username\":\"jackk wa\"},\n" +
                "{\"title\":\"this java really cool\",\"body\":\"hey java\",\"id\":\"7\",\"created_at\":2002072323,\"username\":\"william\"},\n" +
                "{\"title\":\"this java really cool\",\"body\":\"hey java\",\"id\":\"8\",\"created_at\":2002072323,\"username\":\"william\"},\n" +
                "{\"title\":\"this java really cool\",\"body\":\"hey java\",\"id\":\"9\",\"created_at\":2002072323,\"username\":\"william\"},\n" +
                "{\"title\":\"this java really cool\",\"body\":\"hey java\",\"id\":\"10\",\"created_at\":2002072323,\"username\":\"william\"},\n" +
                "{\"title\":\"this java really cool\",\"body\":\"hey java\",\"id\":\"11\",\"created_at\":2002072323,\"username\":\"william\"},\n" +
                "{\"title\":\"this java really cool\",\"body\":\"hey java\",\"id\":\"12\",\"created_at\":2002072323,\"username\":\"william\"},\n" +
                "{\"title\":\"this java really cool\",\"body\":\"hey java\",\"id\":\"13\",\"created_at\":2002072323,\"username\":\"william\"},\n" +
                "{\"title\":\"this java really cool\",\"body\":\"hey java\",\"id\":\"14\",\"created_at\":2002072323,\"username\":\"william\"}\n" +
                "\n" +
                "]";
        return data;
    }
}
