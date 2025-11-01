package com.example.server.service;

import com.example.server.entity.*;
import com.example.server.respository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductIngredientRepository productIngredientRepository;

    @Autowired
    private ProductCostCalculationService costCalculationService;

    @Autowired
    private StockBaseRepository stockBaseRepository;

    // CRUD Operations
    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Product> getActiveProducts() {
        return productRepository.findByStatus(Product.ProductStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Product> getProductsByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    @Transactional(readOnly = true)
    public List<Product> searchProducts(String keyword) {
        return productRepository.searchProducts(keyword);
    }

    // สร้าง Product ใหม่
    public Product createProduct(Product product, List<ProductIngredient> ingredients) {
        validateProduct(product);

        Product savedProduct = productRepository.save(product);

        if (ingredients != null && !ingredients.isEmpty()) {
            for (ProductIngredient ingredient : ingredients) {
                ingredient.setProduct(savedProduct);

                // ตรวจสอบและโหลด StockItem
                if (ingredient.getStockItem() != null) {
                    StockBase stockItem = stockBaseRepository.findById(ingredient.getStockItem().getStockItemId())
                            .orElseThrow(() -> new RuntimeException("Stock item not found"));
                    ingredient.setStockItem(stockItem);
                }

                productIngredientRepository.save(ingredient);
            }

            // คำนวณต้นทุนหลังจากบันทึก ingredients
            recalculateProductCost(savedProduct.getProductId());
        }

        return savedProduct;
    }

    // ⭐ อัปเดต Product พร้อมคำนวณต้นทุนใหม่ (รองรับการอัปเดตรูปภาพ)
    public Product updateProduct(Long id, Product productDetails) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

        updateProductFields(product, productDetails);
        validateProduct(product);

        Product updatedProduct = productRepository.save(product);

        // คำนวณต้นทุนใหม่ทันทีหลังอัปเดต
        recalculateProductCost(updatedProduct.getProductId());

        return updatedProduct;
    }

    // เพิ่ม/อัปเดต Ingredient
    public ProductIngredient addOrUpdateIngredient(Long productId, ProductIngredient ingredient) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));

        ingredient.setProduct(product);

        // Validate และโหลด stock item
        if (ingredient.getStockItem() != null) {
            StockBase stockItem = stockBaseRepository.findById(ingredient.getStockItem().getStockItemId())
                    .orElseThrow(() -> new RuntimeException("Stock item not found"));
            ingredient.setStockItem(stockItem);
        }

        ProductIngredient savedIngredient = productIngredientRepository.save(ingredient);

        // คำนวณต้นทุนใหม่
        recalculateProductCost(productId);

        return savedIngredient;
    }

    // ลบ Ingredient
    public void removeIngredient(Long ingredientId) {
        ProductIngredient ingredient = productIngredientRepository.findById(ingredientId)
                .orElseThrow(() -> new RuntimeException("Ingredient not found"));

        Long productId = ingredient.getProduct().getProductId();
        productIngredientRepository.deleteById(ingredientId);

        // คำนวณต้นทุนใหม่
        recalculateProductCost(productId);
    }

    // คำนวณต้นทุนใหม่สำหรับ Product เดียว
    public void recalculateProductCost(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // คำนวณต้นทุนรวม
        BigDecimal totalCost = costCalculationService.calculateProductTotalCost(product);
        product.setCalculatedCost(totalCost);

        // คำนวณกำไร
        if (product.getSellingPrice() != null && totalCost != null) {
            BigDecimal profit = product.getSellingPrice().subtract(totalCost);
            product.setProfitMargin(profit);
        }

        productRepository.save(product);
    }

    // คำนวณต้นทุนทั้งหมดใหม่ (เมื่อ Stock ราคาเปลี่ยน)
    public void recalculateAllProductCosts() {
        List<Product> allProducts = productRepository.findAll();

        int successCount = 0;
        int errorCount = 0;

        for (Product product : allProducts) {
            try {
                recalculateProductCost(product.getProductId());
                successCount++;
            } catch (Exception e) {
                errorCount++;
                System.err.println("Error recalculating cost for product " +
                        product.getProductId() + " (" + product.getProductName() + "): " +
                        e.getMessage());
            }
        }

        System.out.println("Recalculation completed: " + successCount + " successful, " +
                errorCount + " errors");
    }

    // หา Products ที่ได้รับผลกระทบเมื่อ Stock Item เปลี่ยน
    public List<Product> getProductsAffectedByStock(Long stockItemId) {
        return productIngredientRepository.findProductsUsingStockItem(stockItemId);
    }

    // คำนวณต้นทุนใหม่สำหรับ Products ที่ใช้ Stock Item นั้น
    public void recalculateProductsCostByStock(Long stockItemId) {
        List<Product> affectedProducts = getProductsAffectedByStock(stockItemId);

        System.out.println("Recalculating costs for " + affectedProducts.size() +
                " products affected by Stock Item ID: " + stockItemId);

        for (Product product : affectedProducts) {
            try {
                recalculateProductCost(product.getProductId());
            } catch (Exception e) {
                System.err.println("Error recalculating cost for product " +
                        product.getProductId() + ": " + e.getMessage());
            }
        }
    }

    // ✅ ลบ Product (Hard Delete) - ลำดับการลบที่ถูกต้อง
    @Transactional
    public void deleteProduct(Long id) {
        // 1. ตรวจสอบว่า Product มีอยู่จริง
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

        try {
            // 2. ลบ ingredients ที่เกี่ยวข้องก่อน (สำคัญมาก!)
            List<ProductIngredient> ingredients = productIngredientRepository
                    .findByProductProductId(id);

            if (!ingredients.isEmpty()) {
                System.out.println("Deleting " + ingredients.size() + " ingredients for product ID: " + id);
                productIngredientRepository.deleteAll(ingredients);
                // Force flush เพื่อให้แน่ใจว่า ingredients ถูกลบก่อน
                productIngredientRepository.flush();
            }

            // 3. ลบ product
            productRepository.delete(product);
            productRepository.flush();

            System.out.println("Successfully deleted product ID: " + id);
        } catch (Exception e) {
            System.err.println("Error deleting product ID " + id + ": " + e.getMessage());
            throw new RuntimeException("Failed to delete product: " + e.getMessage(), e);
        }
    }

    // Soft Delete (เปลี่ยนสถานะเป็น DISCONTINUED)
    public Product softDeleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

        product.setStatus(Product.ProductStatus.DISCONTINUED);
        return productRepository.save(product);
    }

    // Validation
    private void validateProduct(Product product) {
        if (product.getProductName() == null || product.getProductName().trim().isEmpty()) {
            throw new IllegalArgumentException("Product name is required");
        }

        if (product.getSku() != null) {
            Optional<Product> existingProduct = productRepository.findBySku(product.getSku());
            if (existingProduct.isPresent() &&
                    !existingProduct.get().getProductId().equals(product.getProductId())) {
                throw new IllegalArgumentException("SKU already exists: " + product.getSku());
            }
        }
    }

    // ⭐ ปรับปรุง updateProductFields เพื่อรองรับ imageUrl
    private void updateProductFields(Product product, Product details) {
        if (details.getProductName() != null) product.setProductName(details.getProductName());
        if (details.getDescription() != null) product.setDescription(details.getDescription());
        if (details.getSku() != null) product.setSku(details.getSku());
        if (details.getCategory() != null) product.setCategory(details.getCategory());
        if (details.getSellingPrice() != null) product.setSellingPrice(details.getSellingPrice());
        if (details.getStatus() != null) product.setStatus(details.getStatus());

        // ⭐ อัปเดต imageUrl (ถ้ามีการส่งมา)
        if (details.getImageUrl() != null) {
            product.setImageUrl(details.getImageUrl());
        }
    }
}