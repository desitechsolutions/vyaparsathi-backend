package com.desitech.vyaparsathi.common.payload;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApiResponse<T> {

    private String status;
    private String message;
    private T data;

}
