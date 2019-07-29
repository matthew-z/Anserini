/**
 * Anserini: A Lucene toolkit for replicable information retrieval research
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.anserini.search.topicreader;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Topic reader for standard XML format used in the TREC Web Tracks.
 */
public class NtcirTopicReader extends TopicReader {
  public NtcirTopicReader(Path topicFile) {
    super(topicFile);
  }


  @Override
  public SortedMap<Integer, Map<String, String>> read(BufferedReader bRdr) throws IOException {
    SortedMap<Integer, Map<String, String>> map = new TreeMap<>();
    Map<String,String> fields = new HashMap<>();

    String number = "";
    String query = "";
    String description = "";
    String line;

    while ((line = bRdr.readLine()) != null) {
      line = line.trim();
      if (line.startsWith("<qid")) {
        number = line.substring(5, line.length() - 6).trim();
      }
      if (line.startsWith("<content>") && line.endsWith("</content>")) {
        query = line.substring(9, line.length() - 10).trim();
        fields.put("title", query);
      }

      if (line.startsWith("<description>") && line.endsWith("</description>")){
        description = line.substring(13, line.length() - 14).trim();
        fields.put("description", description);
      }

      if (line.startsWith("</query>")) {
        map.put(Integer.valueOf(number), fields);
        fields = new HashMap<>();
      }
    }
    return map;
  }
}
