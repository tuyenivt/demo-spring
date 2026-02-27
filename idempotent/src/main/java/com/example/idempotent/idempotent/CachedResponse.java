package com.example.idempotent.idempotent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CachedResponse {
    private int statusCode;
    private Object body;
}
