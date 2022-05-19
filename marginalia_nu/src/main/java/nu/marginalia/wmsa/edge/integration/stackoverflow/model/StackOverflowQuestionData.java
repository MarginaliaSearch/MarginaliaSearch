package nu.marginalia.wmsa.edge.integration.stackoverflow.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data @AllArgsConstructor
public class StackOverflowQuestionData {
    int id;
    String title;
    String question;
    List<String> replies;
}
