package com.novedadeslz.backend.service;

import com.novedadeslz.backend.dto.request.ProductRequest;
import com.novedadeslz.backend.dto.response.ProductResponse;
import com.novedadeslz.backend.model.Product;
import com.novedadeslz.backend.repository.ProductRepository;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Un espacio invisible convertia "Nuevo" y "Nuevo " en dos categorias distintas, y el filtro del
 * catalogo terminaba mostrando dos opciones identicas a la vista.
 */
@ExtendWith(MockitoExtension.class)
class ProductCategoryTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private org.modelmapper.ModelMapper modelMapper;

    @Mock
    private CloudinaryService cloudinaryService;

    @Mock
    private Validator validator;

    @InjectMocks
    private ProductService productService;

    @Test
    void createProductShouldTrimSurroundingSpacesFromCategory() throws Exception {
        when(validator.validate(any(ProductRequest.class))).thenReturn(Set.of());
        when(cloudinaryService.uploadImage(any())).thenReturn("https://cdn.example.com/a.png");
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));
        when(modelMapper.map(any(Product.class), eq(ProductResponse.class)))
                .thenReturn(new ProductResponse());

        ProductRequest request = new ProductRequest();
        request.setName("Vincha");
        request.setPrice(new BigDecimal("15.00"));
        request.setCategory("  Nuevo  ");
        request.setStock(3);

        productService.createProduct(
                request,
                List.of(new MockMultipartFile("images", "a.png", "image/png", "x".getBytes())),
                null
        );

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertEquals("Nuevo", saved.getValue().getCategory());
    }

}
