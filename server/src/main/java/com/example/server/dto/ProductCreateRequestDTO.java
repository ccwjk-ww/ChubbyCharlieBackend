//package com.example.server.dto;
//
//import lombok.Data;
//
//import java.math.BigDecimal;
//import java.util.List;
//
//@Data
//public class ProductCreateRequestDTO {
//    private String productName;
//    private String description;
//    private String sku;
//    private String category;
//    private BigDecimal sellingPrice;
//    private List<ProductIngredientRequestDTO> ingredients;
//}
package com.example.server.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductCreateRequestDTO {
    private String productName;
    private String description;
    private String sku;
    private String category;
    private BigDecimal sellingPrice;
    private List<ProductIngredientRequestDTO> ingredients;
    /**
     * ⭐ เพิ่ม: Status field
     */
    private String status; // "ACTIVE", "INACTIVE", "DISCONTINUED"

}