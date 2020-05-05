package com.asraf.aws;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.services.comprehend.AmazonComprehend;
import com.amazonaws.services.comprehend.AmazonComprehendClientBuilder;
import com.amazonaws.services.comprehend.model.DetectEntitiesRequest;
import com.amazonaws.services.comprehend.model.DetectEntitiesResult;
import com.amazonaws.services.comprehend.model.Entity;
import com.amazonaws.services.textract.AmazonTextract;
import com.amazonaws.services.textract.AmazonTextractClientBuilder;
import com.amazonaws.services.textract.model.DetectDocumentTextRequest;
import com.amazonaws.services.textract.model.DetectDocumentTextResult;
import com.amazonaws.services.textract.model.Document;
import net.ricecode.similarity.JaroWinklerStrategy;
import net.ricecode.similarity.SimilarityStrategy;
import net.ricecode.similarity.StringSimilarityService;
import net.ricecode.similarity.StringSimilarityServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class DocumentText {

    static AWSCredentials awsCredentials = new AWSCredentials() {
        @Override
        public String getAWSAccessKeyId() {
            return "#####";
        }

        @Override
        public String getAWSSecretKey() {
            return "#####";
        }
    };

    static AmazonTextract amazonTextractClient = AmazonTextractClientBuilder.standard()
            .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
            .withRegion("us-east-1")
            .build();

    static AmazonComprehend amazonComprehendClient = AmazonComprehendClientBuilder.standard()
            .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
            .withRegion("us-east-1")
            .build();

    static NidImageResponse getNidImageResponse(byte[] bytes) {
        String allExtractedText = getExtractedTextFromImage(bytes);

        if (StringUtils.isBlank(allExtractedText)) {
            return null;
        }

        DetectEntitiesRequest request = new DetectEntitiesRequest()
                .withText(allExtractedText)
                .withLanguageCode("en");
        DetectEntitiesResult result = amazonComprehendClient.detectEntities(request);

        return NidImageResponse.builder()
                .name(getLastOccurredText(result, NidDataFetchType.PERSON))
                .nid(getNid(getLastOccurredText(result, NidDataFetchType.OTHER)))
                .dateOfBirth(getDateOfBirth(getLastOccurredText(result, NidDataFetchType.DATE)))
                .nidImageValid(isNidValid(result))
                .build();
    }

    static boolean isNidValid(DetectEntitiesResult result) {
        SimilarityStrategy strategy = new JaroWinklerStrategy();
        StringSimilarityService service = new StringSimilarityServiceImpl(strategy);
        double score = service.score("Government Of The People's Republic Of Bangladesh", getAllOrganizationName(result));
        return score > 0.5;
    }

    static ByteBuffer getByteBuffer(byte[] bytes) {
        return ByteBuffer.wrap(bytes);
    }

    static String getExtractedTextFromImage(byte[] bytes) {
        DetectDocumentTextRequest request = new DetectDocumentTextRequest()
                .withDocument(new Document().withBytes(getByteBuffer(bytes)));

        DetectDocumentTextResult result = amazonTextractClient.detectDocumentText(request);
        return result.getBlocks().stream()
                .filter(block -> block != null && block.getBlockType().equals("LINE") && block.getConfidence() != null && block.getConfidence() >= 80)
                .map(block -> WordUtils.capitalizeFully(block.getText()))
                .collect(Collectors.joining(" "));
    }

    static String getLastOccurredText(DetectEntitiesResult result, NidDataFetchType nidDataFetchType) {
        return result.getEntities().stream()
                .filter(entity -> entity.getType().equals(nidDataFetchType.name()))
                .max(Comparator.comparing(Entity::getEndOffset))
                .orElse(new Entity())
                .getText();
    }

    static String getAllOrganizationName(DetectEntitiesResult result) {
        return result.getEntities().stream()
                .filter(entity -> entity.getType().equals(NidDataFetchType.ORGANIZATION.name()))
                .map(Entity::getText)
                .collect(Collectors.joining(" "));
    }

    static Long getNid(String nid) {
        try {
            return Long.valueOf(nid.replaceAll("[^0-9]", "").trim());
        } catch (Exception e) {
            return null;
        }
    }

    static Date getDateOfBirth(String dateString) {
        try {
            return new SimpleDateFormat("dd MMM yyyy").parse(dateString.trim());
        } catch (Exception e) {
            return null;
        }
    }

    public static void main(String[] arg) throws Exception {

        List<NidImageResponse> responses = new ArrayList<>();

//        SimilarityStrategy strategy = new JaroWinklerStrategy();
//        StringSimilarityService service = new StringSimilarityServiceImpl(strategy);
//        double score = service.score("Government Of The People's Republic Of Bangladesh", "Government Ofthe Teople Bangladesh");
//        System.err.println(score);

//        for(int I = 1; I <= 10; I++) {
            String filePath = "test_case\\r1.4.JPEG";
            responses.add(getNidImageResponse(Files.readAllBytes(Paths.get(filePath))));
//        }
//        System.err.println(allExtractedText);
//        result.getEntities().forEach(System.err::println);
        System.err.println(responses);

    }

    enum NidDataFetchType {
        PERSON, DATE, OTHER, ORGANIZATION
    }
}

