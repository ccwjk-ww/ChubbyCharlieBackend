package com.example.server.controller;

import com.example.server.dto.*;
import com.example.server.entity.*;
import com.example.server.mapper.ProductMapper;
import com.example.server.respository.ChinaStockRepository;
import com.example.server.respository.ProductRepository;
import com.example.server.respository.StockLotRepository;
import com.example.server.respository.ThaiStockRepository;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

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

    @Value("${file.upload-dir:/var/www/images/products}")
    private String uploadDir;

    private static final String DEFAULT_IMAGE_URL = "/images/products/logo.jpg";

    // ============================================
    // GET Endpoints
    // ============================================

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

            Product product = productMapper.toProduct(request);

            if (image != null && !image.isEmpty()) {
                String imagePath = saveImage(image);
                product.setImageUrl(imagePath);
                System.out.println("✅ Image uploaded: " + imagePath);
            } else {
                product.setImageUrl(DEFAULT_IMAGE_URL);
            }

            List<ProductIngredient> ingredients = null;
            if (request.getIngredients() != null && !request.getIngredients().isEmpty()) {
                ingredients = productMapper.toProductIngredients(request.getIngredients());
            }

            Product savedProduct = productService.createProduct(product, ingredients);

            ProductDTO dto = productMapper.toProductDTO(savedProduct);
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Failed to create product",
                            "message", e.getMessage()
                    ));
        }
    }

//    // ============================================
//    // ⭐ UPDATE Product with Ingredients Support
//    // ============================================
//    @PutMapping(value = "/{id}", consumes = {"multipart/form-data"})
//    public ResponseEntity<?> updateProduct(
//            @PathVariable Long id,
//            @RequestPart("product") String productJson,
//            @RequestPart(value = "image", required = false) MultipartFile image) {
//
//        try {
//            System.out.println("📝 Updating product ID: " + id);
//            System.out.println("📄 Product JSON: " + productJson);
//
//            // 1. Parse JSON เป็น ProductCreateRequestDTO (มี ingredients)
//            ProductCreateRequestDTO request = objectMapper.readValue(productJson, ProductCreateRequestDTO.class);
//
//            // 2. ตรวจสอบว่า Product มีอยู่จริง
//            Product existingProduct = productService.getProductById(id)
//                    .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
//
//            System.out.println("✅ Found existing product: " + existingProduct.getProductName());
//
//            // 3. สร้าง Product object ที่จะอัปเดต
//            Product productDetails = new Product();
//            productDetails.setProductName(request.getProductName());
//            productDetails.setDescription(request.getDescription());
//            productDetails.setSku(request.getSku());
//            productDetails.setCategory(request.getCategory());
//            productDetails.setSellingPrice(request.getSellingPrice());
//            productDetails.setStatus(existingProduct.getStatus()); // เก็บ status เดิม
//
//            // 4. จัดการรูปภาพ
//            if (image != null && !image.isEmpty()) {
//                if (!existingProduct.isUsingDefaultImage()) {
//                    deleteOldImage(existingProduct.getImageUrl());
//                }
//
//                String newImagePath = saveImage(image);
//                productDetails.setImageUrl(newImagePath);
//                System.out.println("✅ New image uploaded: " + newImagePath);
//            } else {
//                productDetails.setImageUrl(existingProduct.getImageUrl());
//                System.out.println("ℹ️ Keeping existing image");
//            }
//
//            // 5. ⭐ แปลง Ingredients
//            List<ProductIngredient> newIngredients = null;
//            if (request.getIngredients() != null && !request.getIngredients().isEmpty()) {
//                newIngredients = productMapper.toProductIngredients(request.getIngredients());
//                System.out.println("📦 New ingredients count: " + newIngredients.size());
//            }
//
//            // 6. ⭐ อัปเดต Product พร้อม Ingredients
//            Product updatedProduct = productService.updateProductWithIngredients(id, productDetails, newIngredients);
//
//            System.out.println("✅ Product updated successfully");
//
//            // 7. Return DTO
//            ProductDTO dto = productMapper.toProductDTO(updatedProduct);
//            return ResponseEntity.ok(dto);
//
//        } catch (RuntimeException e) {
//            System.err.println("❌ Product not found: " + e.getMessage());
//            return ResponseEntity.status(HttpStatus.NOT_FOUND)
//                    .body(Map.of(
//                            "error", "Product not found",
//                            "message", e.getMessage()
//                    ));
//        } catch (Exception e) {
//            e.printStackTrace();
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(Map.of(
//                            "error", "Failed to update product",
//                            "message", e.getMessage()
//                    ));
//        }
//    }

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

            // ⭐ จัดการ status
            if (request.getStatus() != null && !request.getStatus().isEmpty()) {
                try {
                    Product.ProductStatus newStatus = Product.ProductStatus.valueOf(request.getStatus());
                    productDetails.setStatus(newStatus);
                    System.out.println("📊 Setting status to: " + newStatus);
                } catch (IllegalArgumentException e) {
                    System.err.println("⚠️ Invalid status: " + request.getStatus() + ", keeping existing status");
                    productDetails.setStatus(existingProduct.getStatus());
                }
            } else {
                // ถ้าไม่ส่ง status มา ให้เก็บ status เดิม
                productDetails.setStatus(existingProduct.getStatus());
                System.out.println("📊 Keeping existing status: " + existingProduct.getStatus());
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
                System.out.println("ℹ️ Keeping existing image");
            }

            // แปลง Ingredients
            List<ProductIngredient> newIngredients = null;
            if (request.getIngredients() != null && !request.getIngredients().isEmpty()) {
                newIngredients = productMapper.toProductIngredients(request.getIngredients());
                System.out.println("📦 New ingredients count: " + newIngredients.size());
            }

            // อัปเดต Product พร้อม Ingredients
            Product updatedProduct = productService.updateProductWithIngredients(id, productDetails, newIngredients);

            System.out.println("✅ Product updated successfully with status: " + updatedProduct.getStatus());

            ProductDTO dto = productMapper.toProductDTO(updatedProduct);
            return ResponseEntity.ok(dto);

        } catch (RuntimeException e) {
            System.err.println("❌ Product not found: " + e.getMessage());
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
                            "message", e.getMessage()
                    ));
        }
    }

    /**
     * ⭐ เพิ่ม: Endpoint สำหรับอัปเดตเฉพาะสถานะ
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

            // ⭐ บันทึกโดยไม่ยุ่งกับ ingredients
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
}