package com.example.server.controller;

import com.example.server.dto.*;
import com.example.server.entity.*;
import com.example.server.mapper.ProductMapper;
import com.example.server.respository.*;
import com.example.server.service.ProductIngredientService;
import com.example.server.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ChinaStockRepository chinaStockRepository;

    @Autowired
    private ThaiStockRepository thaiStockRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StockLotRepository stockLotRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductIngredientService ingredientService;

    @Autowired
    private ProductIngredientStockAllocationRepository allocationRepository;

    @Autowired
    private  ProductIngredientRepository productIngredientRepository;

    @Value("${file.upload-dir:/var/www/images/products}")
    private String uploadDir;

    private static final String DEFAULT_IMAGE_URL = "/images/products/logo.jpg";

    // ============================================
    // GET Endpoints
    // ============================================
    @GetMapping("/by-sku/{sku}")
    public ResponseEntity<?> findBySku(@PathVariable String sku) {
        return productRepository.findBySku(sku)
                .map(p -> ResponseEntity.ok(Map.of(
                        "found", true,
                        "productId", p.getProductId(),
                        "productName", p.getProductName(),
                        "sku", p.getSku()
                )))
                .orElse(ResponseEntity.ok(Map.of("found", false, "sku", sku)));
    }

    @GetMapping("/stock-options")
    public ResponseEntity<List<StockOptionDTO>> getAvailableStockItems() {
        try {
            List<StockOptionDTO> options = new ArrayList<>();

            // China Stocks
            List<ChinaStock> chinaStocks = chinaStockRepository.findByStatus(ChinaStock.StockStatus.ACTIVE);
            for (ChinaStock stock : chinaStocks) {
                StockOptionDTO option = new StockOptionDTO();
                option.setStockItemId(stock.getStockItemId());
                option.setName(stock.getName());
                option.setType("CHINA");
                option.setUnitCost(stock.getFinalPricePerPair());
                option.setAvailableQuantity(stock.getQuantity());
                option.setStatus(stock.getStatus().name());
                option.setStockLotId(stock.getStockLotId());

                if (stock.getStockLotId() != null) {
                    stockLotRepository.findById(stock.getStockLotId()).ifPresent(lot ->
                            option.setLotName(lot.getLotName())
                    );
                }
                options.add(option);
            }

            // Thai Stocks
            List<ThaiStock> thaiStocks = thaiStockRepository.findByStatus(ThaiStock.StockStatus.ACTIVE);
            for (ThaiStock stock : thaiStocks) {
                StockOptionDTO option = new StockOptionDTO();
                option.setStockItemId(stock.getStockItemId());
                option.setName(stock.getName());
                option.setType("THAI");
                option.setUnitCost(stock.getPricePerUnitWithShipping());
                option.setAvailableQuantity(stock.getQuantity());
                option.setStatus(stock.getStatus().name());
                option.setStockLotId(stock.getStockLotId());

                if (stock.getStockLotId() != null) {
                    stockLotRepository.findById(stock.getStockLotId()).ifPresent(lot ->
                            option.setLotName(lot.getLotName())
                    );
                }
                options.add(option);
            }

            options.sort(Comparator.comparing(StockOptionDTO::getName));
            return ResponseEntity.ok(options);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<ProductDTO>> getAllProducts() {
        List<Product> products = productService.getAllProducts();
        return ResponseEntity.ok(productMapper.toProductDTOList(products));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getProductById(@PathVariable Long id) {
        return productService.getProductById(id)
                .map(product -> ResponseEntity.ok(productMapper.toProductDTO(product)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/active")
    public ResponseEntity<List<ProductDTO>> getActiveProducts() {
        List<Product> products = productService.getActiveProducts();
        return ResponseEntity.ok(productMapper.toProductDTOList(products));
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<ProductDTO>> getProductsByCategory(@PathVariable String category) {
        List<Product> products = productService.getProductsByCategory(category);
        return ResponseEntity.ok(productMapper.toProductDTOList(products));
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProductDTO>> searchProducts(@RequestParam String keyword) {
        List<Product> products = productService.searchProducts(keyword);
        return ResponseEntity.ok(productMapper.toProductDTOList(products));
    }

    // ============================================
    // CREATE Product
    // ============================================
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<?> createProduct(
            @RequestPart("product") String productJson,
            @RequestPart(value = "image", required = false) MultipartFile image) {

        try {
            ProductCreateRequestDTO request = objectMapper.readValue(productJson, ProductCreateRequestDTO.class);

            System.out.println("📦 Creating product: " + request.getProductName());
            System.out.println("📋 Ingredients count: " + (request.getIngredients() != null ? request.getIngredients().size() : 0));

            // ⭐ ตรวจสอบว่ามี Multi-Lot ingredients หรือไม่
            boolean hasMultiLot = request.getIngredients() != null &&
                    request.getIngredients().stream()
                            .anyMatch(ing -> "MULTI_LOT".equals(ing.getAllocationMode()));

            System.out.println("🔍 Has Multi-Lot ingredients: " + hasMultiLot);

            Product product = productMapper.toProduct(request);

            // จัดการรูปภาพ
            if (image != null && !image.isEmpty()) {
                String imagePath = saveImage(image);
                product.setImageUrl(imagePath);
                System.out.println("✅ Image uploaded: " + imagePath);
            } else {
                product.setImageUrl(DEFAULT_IMAGE_URL);
            }

            Product savedProduct;

            if (hasMultiLot) {
                // ⭐ ใช้ method ที่รองรับ Multi-Lot
                System.out.println("🔄 Using Multi-Lot creation method");
                savedProduct = productService.createProductWithMultiLotIngredients(
                        product,
                        request.getIngredients()
                );
            } else {
                // ใช้ method แบบเดิมสำหรับ Single mode
                System.out.println("🔄 Using Single mode creation method");
                List<ProductIngredient> ingredients = null;
                if (request.getIngredients() != null && !request.getIngredients().isEmpty()) {
                    ingredients = productMapper.toProductIngredients(request.getIngredients());
                }
                savedProduct = productService.createProduct(product, ingredients);
            }

            ProductDTO dto = productMapper.toProductDTO(savedProduct);
            System.out.println("✅ Product created successfully with ID: " + savedProduct.getProductId());

            return ResponseEntity.status(HttpStatus.CREATED).body(dto);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Failed to create product",
                            "message", e.getMessage(),
                            "details", e.toString()
                    ));
        }
    }

    @PutMapping(value = "/{id}", consumes = {"multipart/form-data"})
    public ResponseEntity<?> updateProduct(
            @PathVariable Long id,
            @RequestPart("product") String productJson,
            @RequestPart(value = "image", required = false) MultipartFile image) {

        try {
            System.out.println("📝 Updating product ID: " + id);
            System.out.println("📄 Product JSON: " + productJson);

            ProductCreateRequestDTO request = objectMapper.readValue(productJson, ProductCreateRequestDTO.class);

            Product existingProduct = productService.getProductById(id)
                    .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

            System.out.println("✅ Found existing product: " + existingProduct.getProductName());

            // สร้าง Product object ที่จะอัปเดต
            Product productDetails = new Product();
            productDetails.setProductName(request.getProductName());
            productDetails.setDescription(request.getDescription());
            productDetails.setSku(request.getSku());
            productDetails.setCategory(request.getCategory());
            productDetails.setSellingPrice(request.getSellingPrice());

            // จัดการ status
            if (request.getStatus() != null && !request.getStatus().isEmpty()) {
                try {
                    Product.ProductStatus newStatus = Product.ProductStatus.valueOf(request.getStatus());
                    productDetails.setStatus(newStatus);
                    System.out.println("📊 Setting status to: " + newStatus);
                } catch (IllegalArgumentException e) {
                    System.err.println("⚠️ Invalid status: " + request.getStatus());
                    productDetails.setStatus(existingProduct.getStatus());
                }
            } else {
                productDetails.setStatus(existingProduct.getStatus());
            }

            // จัดการรูปภาพ
            if (image != null && !image.isEmpty()) {
                if (!existingProduct.isUsingDefaultImage()) {
                    deleteOldImage(existingProduct.getImageUrl());
                }
                String newImagePath = saveImage(image);
                productDetails.setImageUrl(newImagePath);
                System.out.println("✅ New image uploaded: " + newImagePath);
            } else {
                productDetails.setImageUrl(existingProduct.getImageUrl());
            }

            // ⭐ ตรวจสอบ Multi-Lot
            boolean hasMultiLot = request.getIngredients() != null &&
                    request.getIngredients().stream()
                            .anyMatch(ing -> "MULTI_LOT".equals(ing.getAllocationMode()));

            System.out.println("🔍 Has Multi-Lot ingredients: " + hasMultiLot);
            System.out.println("📦 Ingredients count: " + (request.getIngredients() != null ? request.getIngredients().size() : 0));

            // ลบ ingredients เก่าทั้งหมด
            List<ProductIngredient> oldIngredients = productIngredientRepository.findByProductProductId(id);
            if (!oldIngredients.isEmpty()) {
                System.out.println("🗑️ Deleting " + oldIngredients.size() + " old ingredients");
                productIngredientRepository.deleteAll(oldIngredients);
                productIngredientRepository.flush();
            }

            // อัปเดต product details
            productService.updateProduct(id, productDetails);

            Product updatedProduct;

            if (hasMultiLot) {
                // ⭐ สร้าง ingredients ใหม่แบบ Multi-Lot
                System.out.println("🔄 Creating Multi-Lot ingredients");

                Product product = productService.getProductById(id)
                        .orElseThrow(() -> new RuntimeException("Product not found"));

                for (ProductIngredientRequestDTO ingReq : request.getIngredients()) {
                    if ("MULTI_LOT".equals(ingReq.getAllocationMode())) {
                        System.out.println("  📌 Creating Multi-Lot ingredient: " + ingReq.getIngredientName());

                        ingredientService.createMultiLotIngredient(
                                product,
                                ingReq.getIngredientName(),
                                ingReq.getRequiredQuantity(),
                                ingReq.getUnit(),
                                ingReq.getStockAllocations().stream()
                                        .map(alloc -> {
                                            ProductIngredientService.StockAllocationRequest req =
                                                    new ProductIngredientService.StockAllocationRequest();
                                            req.setStockItemId(alloc.getStockItemId());
                                            req.setAllocatedQuantity(alloc.getAllocatedQuantity());
                                            req.setAllocationPriority(alloc.getAllocationPriority());
                                            return req;
                                        })
                                        .collect(Collectors.toList()),
                                ingReq.getNotes()
                        );
                    } else {
                        // Single mode
                        System.out.println("  📌 Creating Single mode ingredient: " + ingReq.getIngredientName());
                        ProductIngredient ingredient = productMapper.toProductIngredient(ingReq);
                        ingredient.setProduct(product);

                        // ⭐ คำนวณต้นทุนสำหรับ Single mode
                        if (ingredient.getStockItem() != null) {
                            BigDecimal unitCost = getStockUnitCost(ingredient.getStockItem());
                            ingredient.setCostPerUnit(unitCost);
                            ingredient.setTotalCost(unitCost.multiply(ingredient.getRequiredQuantity()));
                        }

                        productIngredientRepository.save(ingredient);
                    }
                }

                // ⭐ คำนวณต้นทุนรวมหลังจากสร้าง ingredients ทั้งหมดแล้ว
                System.out.println("🧮 Recalculating product cost after update...");
                productService.recalculateProductCost(id);

                updatedProduct = productService.getProductById(id)
                        .orElseThrow(() -> new RuntimeException("Product not found"));

                System.out.println("✅ Final Updated Product Cost: " + updatedProduct.getCalculatedCost());

            } else {
                // ⭐ แบบเดิม (Single mode)
                System.out.println("🔄 Creating Single mode ingredients");
                List<ProductIngredient> newIngredients = null;
                if (request.getIngredients() != null && !request.getIngredients().isEmpty()) {
                    newIngredients = productMapper.toProductIngredients(request.getIngredients());
                }

                updatedProduct = productService.updateProductWithIngredients(id, productDetails, newIngredients);
            }

            // คำนวณต้นทุนใหม่
            productService.recalculateProductCost(id);

            System.out.println("✅ Product updated successfully with status: " + updatedProduct.getStatus());

            ProductDTO dto = productMapper.toProductDTO(updatedProduct);
            return ResponseEntity.ok(dto);

        } catch (RuntimeException e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "error", "Product not found",
                            "message", e.getMessage()
                    ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Failed to update product",
                            "message", e.getMessage(),
                            "details", e.toString()
                    ));
        }
    }

    private BigDecimal getStockUnitCost(StockBase stock) {
        if (stock instanceof ChinaStock) {
            ChinaStock chinaStock = (ChinaStock) stock;
            return chinaStock.getFinalPricePerPair() != null ?
                    chinaStock.getFinalPricePerPair() :
                    chinaStock.calculateFinalPrice();
        } else if (stock instanceof ThaiStock) {
            ThaiStock thaiStock = (ThaiStock) stock;
            return thaiStock.getPricePerUnitWithShipping() != null ?
                    thaiStock.getPricePerUnitWithShipping() :
                    thaiStock.calculateFinalPrice();
        }
        return BigDecimal.ZERO;
    }

    /**
     * ⭐ Endpoint สำหรับอัปเดตเฉพาะสถานะ
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateProductStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        try {
            Product product = productService.getProductById(id)
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            Product.ProductStatus newStatus = Product.ProductStatus.valueOf(status);
            product.setStatus(newStatus);

            Product updatedProduct = productRepository.save(product);

            System.out.println("✅ Status updated to: " + newStatus);

            ProductDTO dto = productMapper.toProductDTO(updatedProduct);
            return ResponseEntity.ok(dto);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid status value: " + status));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ============================================
    // DELETE Product
    // ============================================
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        try {
            Product product = productService.getProductById(id)
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            if (!product.isUsingDefaultImage()) {
                deleteOldImage(product.getImageUrl());
            }

            productService.deleteProduct(id);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Product deleted successfully"
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", e.getMessage()
                    ));
        }
    }

    // ============================================
    // Helper Methods
    // ============================================

    private String saveImage(MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("File must be an image");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";
        String filename = UUID.randomUUID().toString() + extension;

        File uploadDirectory = new File(uploadDir);
        if (!uploadDirectory.exists()) {
            uploadDirectory.mkdirs();
        }

        Path targetPath = Paths.get(uploadDir, filename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        return "/images/products/" + filename;
    }

    private void deleteOldImage(String imageUrl) {
        try {
            if (imageUrl != null && !imageUrl.isEmpty() && !imageUrl.equals(DEFAULT_IMAGE_URL)) {
                String filename = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
                Path filePath = Paths.get(uploadDir, filename);
                File file = filePath.toFile();

                if (file.exists()) {
                    boolean deleted = file.delete();
                    System.out.println(deleted
                            ? "✅ Deleted old image: " + filePath
                            : "⚠️ Failed to delete: " + filePath);
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error deleting old image: " + e.getMessage());
        }
    }

    // ============================================
    // Cost Calculation Endpoints
    // ============================================

    @PostMapping("/{id}/recalculate-cost")
    public ResponseEntity<?> recalculateProductCost(@PathVariable Long id) {
        try {
            productService.recalculateProductCost(id);
            Product updatedProduct = productService.getProductById(id)
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            return ResponseEntity.ok(productMapper.toProductDTO(updatedProduct));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/recalculate-all-costs")
    public ResponseEntity<?> recalculateAllCosts() {
        try {
            productService.recalculateAllProductCosts();
            return ResponseEntity.ok(Map.of(
                    "message", "All product costs recalculated successfully"
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/cost-analysis")
    public ResponseEntity<?> getProductCostAnalysis(@PathVariable Long id) {
        try {
            Product product = productService.getProductById(id)
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            ProductCostAnalysisDTO analysis = productMapper.toProductCostAnalysisDTO(product);
            return ResponseEntity.ok(analysis);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ============================================
    // ⭐ Multi-Lot Ingredient Endpoints
    // ============================================

    /**
     * ⭐ ดึงข้อมูล Stock Allocations ของ Ingredient
     */
    @GetMapping("/ingredients/{ingredientId}/allocations")
    public ResponseEntity<List<ProductIngredientAllocationDTO>> getIngredientAllocations(
            @PathVariable Long ingredientId) {
        try {
            List<ProductIngredientStockAllocation> allocations =
                    allocationRepository.findByProductIngredientIngredientId(ingredientId);

            List<ProductIngredientAllocationDTO> dtos = allocations.stream()
                    .map(this::toAllocationDTO)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * ⭐ อัปเดต Stock Allocations
     */
    @PutMapping("/ingredients/{ingredientId}/allocations")
    public ResponseEntity<?> updateIngredientAllocations(
            @PathVariable Long ingredientId,
            @RequestBody List<StockAllocationRequestDTO> allocations) {
        try {
            List<ProductIngredientService.StockAllocationRequest> requests =
                    allocations.stream()
                            .map(this::toServiceRequest)
                            .collect(Collectors.toList());

            ProductIngredient updated = ingredientService.updateMultiLotAllocations(
                    ingredientId, requests);

            return ResponseEntity.ok(productMapper.toProductIngredientDTO(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ⭐ ดึง Stock Options พร้อมข้อมูล Lot (ค้นหาตามชื่อ)
     */
    @GetMapping("/stock-options/by-name/{stockName}")
    public ResponseEntity<List<StockOptionDTO>> getStockOptionsByName(
            @PathVariable String stockName) {
        try {
            List<StockOptionDTO> options = new ArrayList<>();

            // China Stocks
            List<ChinaStock> chinaStocks = chinaStockRepository.searchByKeyword(stockName);
            for (ChinaStock stock : chinaStocks) {
                if (stock.getStatus() == ChinaStock.StockStatus.ACTIVE && stock.getQuantity() > 0) {
                    StockOptionDTO option = new StockOptionDTO();
                    option.setStockItemId(stock.getStockItemId());
                    option.setName(stock.getName());
                    option.setType("CHINA");
                    option.setUnitCost(stock.getFinalPricePerPair());
                    option.setAvailableQuantity(stock.getQuantity());
                    option.setStatus(stock.getStatus().name());
                    option.setStockLotId(stock.getStockLotId());

                    if (stock.getStockLotId() != null) {
                        stockLotRepository.findById(stock.getStockLotId()).ifPresent(lot ->
                                option.setLotName(lot.getLotName())
                        );
                    }
                    options.add(option);
                }
            }

            // Thai Stocks
            List<ThaiStock> thaiStocks = thaiStockRepository.searchByKeyword(stockName);
            for (ThaiStock stock : thaiStocks) {
                if (stock.getStatus() == ThaiStock.StockStatus.ACTIVE && stock.getQuantity() > 0) {
                    StockOptionDTO option = new StockOptionDTO();
                    option.setStockItemId(stock.getStockItemId());
                    option.setName(stock.getName());
                    option.setType("THAI");
                    option.setUnitCost(stock.getPricePerUnitWithShipping());
                    option.setAvailableQuantity(stock.getQuantity());
                    option.setStatus(stock.getStatus().name());
                    option.setStockLotId(stock.getStockLotId());

                    if (stock.getStockLotId() != null) {
                        stockLotRepository.findById(stock.getStockLotId()).ifPresent(lot ->
                                option.setLotName(lot.getLotName())
                        );
                    }
                    options.add(option);
                }
            }

            // เรียงตาม Lot Name แล้วชื่อ
            options.sort(Comparator.comparing(StockOptionDTO::getLotName,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(StockOptionDTO::getName));

            return ResponseEntity.ok(options);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ============================================
    // ⭐ Helper Methods สำหรับ Multi-Lot
    // ============================================

    /**
     * แปลง ProductIngredientStockAllocation เป็น DTO
     */
    private ProductIngredientAllocationDTO toAllocationDTO(ProductIngredientStockAllocation allocation) {
        ProductIngredientAllocationDTO dto = new ProductIngredientAllocationDTO();
        dto.setAllocationId(allocation.getAllocationId());
        dto.setStockItemId(allocation.getStockItem().getStockItemId());
        dto.setStockItemName(allocation.getStockItem().getName());
        dto.setStockType(allocation.getStockType());
        dto.setAllocatedQuantity(allocation.getAllocatedQuantity());
        dto.setAllocationPriority(allocation.getAllocationPriority());
        dto.setCostPerUnit(allocation.getCostPerUnit());
        dto.setTotalCost(allocation.getTotalCost());
        dto.setAvailableQuantity(allocation.getStockItem().getQuantity());

        // ดึงข้อมูล Lot
        if (allocation.getStockItem().getStockLotId() != null) {
            stockLotRepository.findById(allocation.getStockItem().getStockLotId())
                    .ifPresent(lot -> {
                        dto.setLotName(lot.getLotName());
                        dto.setStockLotId(lot.getStockLotId());
                    });
        }

        return dto;
    }

    /**
     * แปลง DTO เป็น Service Request
     */
    private ProductIngredientService.StockAllocationRequest toServiceRequest(
            StockAllocationRequestDTO dto) {
        ProductIngredientService.StockAllocationRequest request =
                new ProductIngredientService.StockAllocationRequest();
        request.setStockItemId(dto.getStockItemId());
        request.setAllocatedQuantity(dto.getAllocatedQuantity());
        request.setAllocationPriority(dto.getAllocationPriority());
        return request;
    }

    /**
     * ⭐ DTO สำหรับรับ Request (Inner Class)
     */
    public static class StockAllocationRequestDTO {
        private Long stockItemId;
        private java.math.BigDecimal allocatedQuantity;
        private Integer allocationPriority;

        // Getters & Setters
        public Long getStockItemId() {
            return stockItemId;
        }

        public void setStockItemId(Long stockItemId) {
            this.stockItemId = stockItemId;
        }

        public java.math.BigDecimal getAllocatedQuantity() {
            return allocatedQuantity;
        }

        public void setAllocatedQuantity(java.math.BigDecimal allocatedQuantity) {
            this.allocatedQuantity = allocatedQuantity;
        }

        public Integer getAllocationPriority() {
            return allocationPriority;
        }

        public void setAllocationPriority(Integer allocationPriority) {
            this.allocationPriority = allocationPriority;
        }
    }
}