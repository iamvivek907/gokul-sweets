package com.gokulsweets.restaurant.product;
import com.gokulsweets.restaurant.product.dto.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/admin/products/{id}/image")
public class AdminProductImageController {
 private final ProductService products;
 @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ProductResponse upload(@PathVariable Long id,@RequestParam MultipartFile image){return products.uploadImage(id,image);}
 @DeleteMapping public ProductResponse remove(@PathVariable Long id){return products.removeImage(id);}
}
