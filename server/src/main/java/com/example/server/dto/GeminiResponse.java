//package com.example.server.dto;
//
//import lombok.Data;
//import lombok.NoArgsConstructor;
//import lombok.AllArgsConstructor;
//import java.util.List;
//
//@Data
//@NoArgsConstructor
//@AllArgsConstructor
//public class GeminiResponse {
//    private List<Candidate> candidates;
//
//    @Data
//    @NoArgsConstructor
//    @AllArgsConstructor
//    public static class Candidate {
//        private Content content;
//        private String finishReason;
//        private int index;
//    }
//
//    @Data
//    @NoArgsConstructor
//    @AllArgsConstructor
//    public static class Content {
//        private List<Part> parts;
//        private String role;
//    }
//
//    @Data
//    @NoArgsConstructor
//    @AllArgsConstructor
//    public static class Part {
//        private String text;
//    }
//}
package com.example.server.dto;

import lombok.Data;
import java.util.List;

@Data
public class GeminiResponse {
    private List<Candidate> candidates;

    @Data
    public static class Candidate {
        private Content content;
    }

    @Data
    public static class Content {
        private List<Part> parts;
    }

    @Data
    public static class Part {
        private String text;
    }
}
