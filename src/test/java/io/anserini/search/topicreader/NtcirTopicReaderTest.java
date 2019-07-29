package io.anserini.search.topicreader;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.SortedMap;

import static org.junit.Assert.assertEquals;

public class NtcirTopicReaderTest {

  @Test
  public void test() throws IOException {
    TopicReader reader = new NtcirTopicReader(Paths.get("src/main/resources/topics-and-qrels/topics.www2.english.txt"));

    SortedMap<Integer, Map<String, String>> topics = reader.read();

    assertEquals(80, topics.keySet().size());
    assertEquals(1, (int) topics.firstKey());
    assertEquals("Halloween picture", topics.get(topics.firstKey()).get("title"));
    assertEquals("Halloween is coming. You want to find some pictures about Halloween to introduce it to your children.", topics.get(topics.firstKey()).get("description"));


    assertEquals(80, (int) topics.lastKey());
    assertEquals("www.gardenburger.com", topics.get(topics.lastKey()).get("title"));
    assertEquals("You want to find the website &quot;www.gardenburger.com&quot;", topics.get(topics.lastKey()).get("description"));

  }
}

