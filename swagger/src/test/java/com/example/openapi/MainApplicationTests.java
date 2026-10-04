package com.example.openapi;

import com.example.openapi.config.PetStoreConfig;
import com.example.openapi.petstore.api.PetApi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MainApplicationTests {

    @Autowired
    private PetApi petApi;

    @Autowired
    private PetStoreConfig petStoreConfig;

    @Test
    void contextLoads() {
        Assertions.assertNotNull(petApi);
    }

    @Test
    void bindsPetStoreCredentialsFromApplicationYml() {
        Assertions.assertEquals("user", petStoreConfig.getUsername());
        Assertions.assertEquals("pass", petStoreConfig.getPassword());
    }
}
