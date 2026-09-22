package com.gokulsweets.restaurant.rebate;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class RebateCustomerId
        implements Serializable {

    private Long rebate;

    private String customerPhone;
}