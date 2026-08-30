package com.harvestflow.registration.entity;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter @Setter @NoArgsConstructor
public class RegistrationAddress {
    private String addressLine;
    private String villageOrCity;
    private String district;
    private String state;
    private String pincode;
}
