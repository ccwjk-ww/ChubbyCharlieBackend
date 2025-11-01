package com.example.server.controller;

import com.example.server.dto.*;
import com.example.server.entity.*;
import com.example.server.mapper.ProductMapper;
import com.example.server.respository.ProductRepository;
import com.example.server.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductRepository productRepository;

    @Value("${file.upload-dir:uploads/products}")
    private String uploadDir;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // Get all products
    @GetMapping
    public ResponseEntity<List<ProductDTO>> getAllProducts() {
        List<Product> products = productService.getAllProducts();
        return ResponseEntity.ok(productMapper.toProductDTOList(products));
    }

    // Get product by ID
    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getProductById(@PathVariable Long id) {
        Optional<Product> product = productService.getProductById(id);
        return product.map(p -> ResponseEntity.ok(productMapper.toProductDTO(p)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Get active products
    @GetMapping("/active")
    public ResponseEntity<List<ProductDTO>> getActiveProducts() {
        List<Product> products = productService.getActiveProducts();
        return ResponseEntity.ok(productMapper.toProductDTOList(products));
    }

    // Get products by category
    @GetMapping("/category/{category}")
    public ResponseEntity<List<ProductDTO>> getProductsByCategory(@PathVariable String category) {
        List<Product> products = productService.getProductsByCategory(category);
        return ResponseEntity.ok(productMapper.toProductDTOList(products));
    }

    // Get product by SKU
    @GetMapping("/sku/{sku}")
    public ResponseEntity<ProductDTO> getProductBySku(@PathVariable String sku) {
        Optional<Product> product = productRepository.findBySku(sku);
        return product.map(p -> ResponseEntity.ok(productMapper.toProductDTO(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    // Search products
    @GetMapping("/search")
    public ResponseEntity<List<ProductDTO>> searchProducts(@RequestParam String keyword) {
        List<Product> products = productService.searchProducts(keyword);
        return ResponseEntity.ok(productMapper.toProductDTOList(products));
    }

    /**
     * ⭐ สร้าง Product ใหม่พร้อมอัปโหลดรูปภาพ
     */
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<?> createProduct(
            @RequestPart("product") ProductCreateRequestDTO productData,
            @RequestPart(value = "image", required = false) MultipartFile image) {

        try {
            // 1. จัดการรูปภาพ (ถ้ามี)
            String imageUrl = null;
            if (image != null && !image.isEmpty()) {
                imageUrl = saveProductImage(image);
                System.out.println("✅ อัปโหลดรูปสำเร็จ: " + imageUrl);
            } else {
                System.out.println("ℹ️ ไม่มีรูปภาพ ใช้ default");
            }

            // 2. สร้าง Product
            Product product = productMapper.toProduct(productData);
            product.setImageUrl(imageUrl); // Set full URL ที่ได้จาก saveProductImage()

            List<ProductIngredient> ingredients = productMapper.toProductIngredients(
                    productData.getIngredients());

            Product createdProduct = productService.createProduct(product, ingredients);

            // 3. Log เพื่อ debug
            System.out.println("📦 Product created: " + createdProduct.getProductName());
            System.out.println("🖼️ Image URL: " + createdProduct.getImageUrl());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(productMapper.toProductDTO(createdProduct));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            System.err.println("❌ Error uploading image: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "ไม่สามารถอัปโหลดรูปภาพได้: " + e.getMessage()));
        }
    }

    /**
     * ⭐ อัปเดต Product พร้อมเปลี่ยนรูปภาพ
     */
    @PutMapping(value = "/{id}", consumes = {"multipart/form-data"})
    public ResponseEntity<?> updateProduct(
            @PathVariable Long id,
            @RequestPart("product") Product productDetails,
            @RequestPart(value = "image", required = false) MultipartFile image) {

        try {
            // 1. จัดการรูปภาพใหม่ (ถ้ามี)
            if (image != null && !image.isEmpty()) {
                // ลบรูปเก่า (ถ้าไม่ใช่ default)
                Optional<Product> existingProduct = productService.getProductById(id);
                if (existingProduct.isPresent() &&
                        !existingProduct.get().isUsingDefaultImage()) {
                    deleteProductImage(existingProduct.get().getImageUrl());
                }

                // อัปโหลดรูปใหม่
                String newImageUrl = saveProductImage(image);
                productDetails.setImageUrl(newImageUrl);
                System.out.println("✅ อัปเดตรูปสำเร็จ: " + newImageUrl);
            }

            // 2. อัปเดต Product
            Product updatedProduct = productService.updateProduct(id, productDetails);

            System.out.println("📦 Product updated: " + updatedProduct.getProductName());
            System.out.println("🖼️ Image URL: " + updatedProduct.getImageUrl());

            return ResponseEntity.ok(productMapper.toProductDTO(updatedProduct));

        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            System.err.println("❌ Error uploading image: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "ไม่สามารถอัปโหลดรูปภาพได้: " + e.getMessage()));
        }
    }

    /**
     * ⭐ อัปเดตเฉพาะข้อมูล Product (ไม่เปลี่ยนรูป)
     */
    @PutMapping("/{id}/data")
    public ResponseEntity<?> updateProductData(
            @PathVariable Long id,
            @RequestBody Product productDetails) {
        try {
            Product updatedProduct = productService.updateProduct(id, productDetails);
            return ResponseEntity.ok(productMapper.toProductDTO(updatedProduct));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * ⭐ ลบ Product พร้อมรูปภาพ
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        try {
            // 1. ลบรูปภาพ (ถ้าไม่ใช่ default)
            Optional<Product> product = productService.getProductById(id);
            if (product.isPresent() && !product.get().isUsingDefaultImage()) {
                deleteProductImage(product.get().getImageUrl());
            }

            // 2. ลบ Product
            productService.deleteProduct(id);

            return ResponseEntity.ok(Map.of(
                    "message", "Product deleted successfully",
                    "status", "success",
                    "productId", id
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "message", "Failed to delete product: " + e.getMessage(),
                            "status", "error",
                            "productId", id
                    ));
        }
    }

    // Soft Delete
    @PatchMapping("/{id}/discontinue")
    public ResponseEntity<?> discontinueProduct(@PathVariable Long id) {
        try {
            Product product = productService.softDeleteProduct(id);
            return ResponseEntity.ok(Map.of(
                    "message", "Product discontinued successfully",
                    "status", "success",
                    "product", productMapper.toProductDTO(product)
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "message", "Product not found: " + e.getMessage(),
                            "status", "error",
                            "productId", id
                    ));
        }
    }

    // Add ingredient to product
    @PostMapping("/{id}/ingredients")
    public ResponseEntity<ProductIngredientDTO> addIngredient(
            @PathVariable Long id,
            @RequestBody ProductIngredient ingredient) {
        try {
            ProductIngredient addedIngredient = productService.addOrUpdateIngredient(id, ingredient);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(productMapper.toProductIngredientDTO(addedIngredient));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Update ingredient
    @PutMapping("/{id}/ingredients/{ingredientId}")
    public ResponseEntity<ProductIngredientDTO> updateIngredient(
            @PathVariable Long id,
            @PathVariable Long ingredientId,
            @RequestBody ProductIngredient ingredient) {
        try {
            ingredient.setIngredientId(ingredientId);
            ProductIngredient updatedIngredient = productService.addOrUpdateIngredient(id, ingredient);
            return ResponseEntity.ok(productMapper.toProductIngredientDTO(updatedIngredient));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Remove ingredient
    @DeleteMapping("/ingredients/{ingredientId}")
    public ResponseEntity<Void> removeIngredient(@PathVariable Long ingredientId) {
        try {
            productService.removeIngredient(ingredientId);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Recalculate product cost
    @PostMapping("/{id}/recalculate-cost")
    public ResponseEntity<ProductDTO> recalculateProductCost(@PathVariable Long id) {
        try {
            productService.recalculateProductCost(id);
            Optional<Product> product = productService.getProductById(id);
            return product.map(p -> ResponseEntity.ok(productMapper.toProductDTO(p)))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Recalculate all product costs
    @PostMapping("/recalculate-all-costs")
    public ResponseEntity<Map<String, String>> recalculateAllProductCosts() {
        try {
            productService.recalculateAllProductCosts();
            return ResponseEntity.ok(Map.of(
                    "message", "All product costs recalculated successfully",
                    "status", "success"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "message", "Error recalculating costs: " + e.getMessage(),
                            "status", "error"
                    ));
        }
    }

    // Recalculate costs for products using specific stock item
    @PostMapping("/recalculate-by-stock/{stockItemId}")
    public ResponseEntity<Map<String, Object>> recalculateProductsCostByStock(@PathVariable Long stockItemId) {
        try {
            List<Product> affectedProducts = productService.getProductsAffectedByStock(stockItemId);
            productService.recalculateProductsCostByStock(stockItemId);

            return ResponseEntity.ok(Map.of(
                    "message", "Costs recalculated successfully",
                    "status", "success",
                    "affectedProductsCount", affectedProducts.size(),
                    "affectedProducts", productMapper.toProductDTOList(affectedProducts)
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "message", "Error recalculating costs: " + e.getMessage(),
                            "status", "error"
                    ));
        }
    }

    // Get cost analysis for product
    @GetMapping("/{id}/cost-analysis")
    public ResponseEntity<ProductCostAnalysisDTO> getProductCostAnalysis(@PathVariable Long id) {
        try {
            Optional<Product> product = productService.getProductById(id);
            if (product.isPresent()) {
                ProductCostAnalysisDTO analysis = productMapper.toProductCostAnalysisDTO(product.get());
                return ResponseEntity.ok(analysis);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Get products affected by stock item
    @GetMapping("/affected-by-stock/{stockItemId}")
    public ResponseEntity<List<ProductDTO>> getProductsAffectedByStock(@PathVariable Long stockItemId) {
        List<Product> products = productService.getProductsAffectedByStock(stockItemId);
        return ResponseEntity.ok(productMapper.toProductDTOList(products));
    }

    // ==================== Helper Methods ====================

    /**
     * ⭐ บันทึกรูปภาพและ return FULL URL
     */
    private String saveProductImage(MultipartFile file) throws IOException {
        // 1. Validate
        if (file.isEmpty()) {
            throw new IllegalArgumentException("ไฟล์ว่างเปล่า");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("ไฟล์ต้องเป็นรูปภาพเท่านั้น (jpg, jpeg, png)");
        }

        // 2. สร้าง directory
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            System.out.println("📁 สร้างโฟลเดอร์: " + uploadPath.toAbsolutePath());
        }

        // 3. สร้างชื่อไฟล์ใหม่
        String originalFilename = file.getOriginalFilename();
        String fileExtension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String newFilename = UUID.randomUUID().toString() + fileExtension;

        // 4. บันทึกไฟล์
        Path filePath = uploadPath.resolve(newFilename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // 5. ⭐ Return FULL URL (สำคัญมาก!)
        String fullUrl = baseUrl + "/uploads/products/" + newFilename;

        System.out.println("💾 บันทึกไฟล์: " + filePath.toAbsolutePath());
        System.out.println("🔗 Full URL: " + fullUrl);

        return fullUrl;
    }

    /**
     * ⭐ ลบรูปภาพ
     */
    private void deleteProductImage(String imageUrl) {
        try {
            if (imageUrl == null || imageUrl.isEmpty()) return;

            // ถ้าเป็น default image ไม่ต้องลบ
            if (imageUrl.endsWith("/logo.jpg") || imageUrl.endsWith("/logo.png")) {
                return;
            }

            // Extract filename from URL
            String filename = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
            Path filePath = Paths.get(uploadDir).resolve(filename);

            if (Files.exists(filePath)) {
                Files.delete(filePath);
                System.out.println("🗑️ ลบรูปภาพ: " + filename);
            }
        } catch (IOException e) {
            System.err.println("❌ Error deleting image: " + e.getMessage());
        }
    }
}