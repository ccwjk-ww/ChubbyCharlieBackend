package com.example.server.service;

import com.example.server.dto.ProductIngredientRequestDTO;
import com.example.server.entity.*;
import com.example.server.mapper.ProductMapper;
import com.example.server.mapper.StockMapper;
import com.example.server.respository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private ProductIngredientService ingredientService;

    @Autowired
    private StockBaseRepository stockBaseRepository;

    @Autowired
    private ProductMapper productMapper;

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
    /**
     * ⭐ สร้าง Product พร้อม Multi-Lot Ingredients
     */
    @Transactional
    public Product createProductWithMultiLotIngredients(
            Product product,
            List<ProductIngredientRequestDTO> ingredientRequests) {

        validateProduct(product);
        Product savedProduct = productRepository.save(product);

        System.out.println("📦 Creating product with Multi-Lot ingredients");

        if (ingredientRequests != null && !ingredientRequests.isEmpty()) {
            for (ProductIngredientRequestDTO request : ingredientRequests) {
                System.out.println("📌 Processing ingredient: " + request.getIngredientName());
                System.out.println("  - Mode: " + request.getAllocationMode());

                if ("MULTI_LOT".equals(request.getAllocationMode())) {
                    // โหมด Multi-Lot
                    System.out.println("  - Creating Multi-Lot ingredient");
                    System.out.println("  - Allocations: " +
                            (request.getStockAllocations() != null ? request.getStockAllocations().size() : 0));

                    ingredientService.createMultiLotIngredient(
                            savedProduct,
                            request.getIngredientName(),
                            request.getRequiredQuantity(),
                            request.getUnit(),
                            request.getStockAllocations().stream()
                                    .map(this::toStockAllocationRequest)
                                    .collect(Collectors.toList()),
                            request.getNotes()
                    );
                } else {
                    // โหมด Single (แบบเดิม)
                    System.out.println("  - Creating Single mode ingredient");
                    ProductIngredient ingredient = productMapper.toProductIngredient(request);
                    ingredient.setProduct(savedProduct);

                    // ⭐ คำนวณต้นทุนสำหรับ Single mode
                    if (ingredient.getStockItem() != null) {
                        BigDecimal unitCost = getStockUnitCost(ingredient.getStockItem());
                        ingredient.setCostPerUnit(unitCost);
                        ingredient.setTotalCost(unitCost.multiply(ingredient.getRequiredQuantity()));
                        System.out.println("  - Unit Cost: " + unitCost);
                        System.out.println("  - Total Cost: " + ingredient.getTotalCost());
                    }

                    productIngredientRepository.save(ingredient);
                }
            }

            // ⭐ สำคัญมาก: คำนวณต้นทุนรวมของ Product
            System.out.println("🧮 Recalculating product cost...");
            recalculateProductCost(savedProduct.getProductId());

            // โหลด Product ใหม่เพื่อให้ได้ต้นทุนที่คำนวณแล้ว
            savedProduct = productRepository.findById(savedProduct.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            System.out.println("✅ Final Product Cost: " + savedProduct.getCalculatedCost());
        }

        return savedProduct;
    }

    // ⭐ เพิ่ม helper method
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

    private ProductIngredientService.StockAllocationRequest toStockAllocationRequest(
            ProductIngredientRequestDTO.StockAllocationRequest dto) {
        ProductIngredientService.StockAllocationRequest request =
                new ProductIngredientService.StockAllocationRequest();
        request.setStockItemId(dto.getStockItemId());
        request.setAllocatedQuantity(dto.getAllocatedQuantity());
        request.setAllocationPriority(dto.getAllocationPriority());
        return request;
    }
    /**
     * ⭐ อัปเดต Product - รองรับการอัปเดต Ingredients ด้วย
     */
    public Product updateProduct(Long id, Product productDetails) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

        System.out.println("📝 Updating product ID: " + id);

        // อัปเดตข้อมูล Product
        updateProductFields(product, productDetails);
        validateProduct(product);

        Product updatedProduct = productRepository.save(product);

        System.out.println("✅ Product data updated: " + updatedProduct.getProductName());

        // ⭐ คำนวณต้นทุนใหม่ทันที
        recalculateProductCost(updatedProduct.getProductId());

        System.out.println("✅ Cost recalculated");

        return updatedProduct;
    }

    /**
     * ⭐ เพิ่ม: อัปเดต Product พร้อม Ingredients ใหม่
     */
    @Transactional
    public Product updateProductWithIngredients(Long id, Product productDetails, List<ProductIngredient> newIngredients) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

        System.out.println("📝 Updating product with ingredients, ID: " + id);

        // 1. อัปเดตข้อมูล Product
        updateProductFields(product, productDetails);
        validateProduct(product);

        Product savedProduct = productRepository.save(product);

        // 2. ลบ Ingredients เก่าทั้งหมด
        List<ProductIngredient> oldIngredients = productIngredientRepository.findByProductProductId(id);
        if (!oldIngredients.isEmpty()) {
            System.out.println("🗑️ Deleting " + oldIngredients.size() + " old ingredients");
            productIngredientRepository.deleteAll(oldIngredients);
            productIngredientRepository.flush();
        }

        // 3. เพิ่ม Ingredients ใหม่
        if (newIngredients != null && !newIngredients.isEmpty()) {
            System.out.println("➕ Adding " + newIngredients.size() + " new ingredients");

            for (ProductIngredient ingredient : newIngredients) {
                ingredient.setProduct(savedProduct);
                ingredient.setIngredientId(null); // Reset ID เพื่อสร้างใหม่

                // โหลด StockItem
                if (ingredient.getStockItem() != null && ingredient.getStockItem().getStockItemId() != null) {
                    StockBase stockItem = stockBaseRepository.findById(ingredient.getStockItem().getStockItemId())
                            .orElseThrow(() -> new RuntimeException("Stock item not found: " + ingredient.getStockItem().getStockItemId()));
                    ingredient.setStockItem(stockItem);
                }

                productIngredientRepository.save(ingredient);
            }

            productIngredientRepository.flush();
        }

        // 4. คำนวณต้นทุนใหม่
        System.out.println("🧮 Recalculating cost...");
        recalculateProductCost(savedProduct.getProductId());

        System.out.println("✅ Product and ingredients updated successfully");

        return savedProduct;
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
    // ProductService.java
    @Transactional
    public void recalculateProductCost(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        System.out.println("🧮 Recalculating cost for product: " + product.getProductName());

        // คำนวณต้นทุนรวม
        BigDecimal totalCost = costCalculationService.calculateProductTotalCost(product);

        System.out.println("💰 Calculated total cost: " + totalCost);

        // ⭐ บันทึกต้นทุน
        product.setCalculatedCost(totalCost);

        // คำนวณกำไร
        if (product.getSellingPrice() != null && totalCost != null) {
            BigDecimal profit = product.getSellingPrice().subtract(totalCost);
            product.setProfitMargin(profit);
            System.out.println("📊 Profit: " + profit);
        }

        // ⭐ บันทึกและ flush
        productRepository.saveAndFlush(product);

        System.out.println("✅ Cost calculation saved to product");

        // ⭐ Reload เพื่อ verify
        Product reloaded = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        System.out.println("✅ Verification from DB:");
        System.out.println("  - DB Calculated Cost: " + reloaded.getCalculatedCost());
        System.out.println("  - DB Profit Margin: " + reloaded.getProfitMargin());

        if (reloaded.getCalculatedCost() == null ||
                reloaded.getCalculatedCost().compareTo(BigDecimal.ZERO) == 0) {
            System.err.println("⚠️ WARNING: Product cost is 0 in database!");
        }
    }

    // คำนวณต้นทุนทั้งหมดใหม่ (เมื่อ Stock ราคาเปลี่ยน)
    @Transactional
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
        if (details.getProductName() != null) {
            product.setProductName(details.getProductName());
        }
        if (details.getDescription() != null) {
            product.setDescription(details.getDescription());
        }
        if (details.getSku() != null) {
            product.setSku(details.getSku());
        }
        if (details.getCategory() != null) {
            product.setCategory(details.getCategory());
        }
        if (details.getSellingPrice() != null) {
            product.setSellingPrice(details.getSellingPrice());
        }
        if (details.getStatus() != null) {
            product.setStatus(details.getStatus());
        }

        // ⭐ อัปเดต imageUrl (ถ้ามีการส่งมา)
        if (details.getImageUrl() != null) {
            product.setImageUrl(details.getImageUrl());
        }
    }
}