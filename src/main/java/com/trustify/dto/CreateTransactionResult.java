package com.trustify.dto;

import com.trustify.model.Transaction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateTransactionResult {
    private Transaction transaction;
    private String stripeClientSecret;
}
