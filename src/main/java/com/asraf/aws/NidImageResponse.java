package com.asraf.aws;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class NidImageResponse {
    private String name;
    private Date dateOfBirth;
    private Long nid;
    private boolean nidImageValid;
}