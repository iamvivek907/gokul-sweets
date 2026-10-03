package com.gokulsweets.restaurant.loyalty;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import java.math.BigDecimal;

@Component @ConfigurationProperties(prefix="gokul.loyalty") @Validated @Getter @Setter
public class LoyaltyProperties {
 @DecimalMin("1") private BigDecimal rupeesPerCoin=new BigDecimal("10");
 @DecimalMin("0.01") @DecimalMax("10") private BigDecimal redemptionPercent=new BigDecimal("10");
 @Min(1) @Max(180) private int expiryDays=180;
 @DecimalMin("0.1") @DecimalMax("3") private BigDecimal normalMaximumCostPercent=new BigDecimal("3");
 @Min(0) @Max(15) private int welcomeCoins=0;
 @DecimalMin("149") private BigDecimal qualifyingSubtotal=new BigDecimal("149");
}
